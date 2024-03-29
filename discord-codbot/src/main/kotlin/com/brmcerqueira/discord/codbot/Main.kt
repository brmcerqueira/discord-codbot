package com.brmcerqueira.discord.codbot

import com.brmcerqueira.discord.codbot.cod.CodDicePoolBotMessage
import com.brmcerqueira.discord.codbot.cod.CodDicePoolProcessor
import com.brmcerqueira.discord.codbot.cod.CodDicePoolDto
import com.brmcerqueira.discord.codbot.cod.CodDicePoolModel
import com.brmcerqueira.discord.codbot.initiative.InitiativeBotMessage
import com.brmcerqueira.discord.codbot.initiative.InitiativeModel
import com.brmcerqueira.discord.codbot.initiative.InitiativeProcessor
import com.brmcerqueira.discord.codbot.narrator.NarratorProcessor
import com.brmcerqueira.discord.codbot.wod.WodDicePoolBotMessage
import com.brmcerqueira.discord.codbot.wod.WodDicePoolDto
import com.brmcerqueira.discord.codbot.wod.WodDicePoolModel
import com.brmcerqueira.discord.codbot.wod.WodDicePoolProcessor
import com.fasterxml.jackson.databind.SerializationFeature
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.event.domain.lifecycle.ReadyEvent
import discord4j.core.DiscordClientBuilder
import discord4j.core.`object`.entity.Channel
import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.util.Snowflake
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import reactor.core.publisher.Flux
import kotlin.random.Random
import io.ktor.jackson.*
import io.ktor.request.authorization
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import io.ktor.util.pipeline.PipelineContext

@KtorExperimentalAPI
fun main(args: Array<String>) {
    val client = DiscordClientBuilder(args.first()).build()

    client.eventDispatcher.on(MessageCreateEvent::class.java)
            .register(InitiativeProcessor(),
                    WodDicePoolProcessor(),
                    CodDicePoolProcessor(),
                    NarratorProcessor())
            .subscribe()

    client.login().subscribe()

    messageChannel = client.eventDispatcher.on(ReadyEvent::class.java)
            .flatMap { client.getGuildById(it.guilds.first().id) }
            .flatMap { it.channels }
            .filter { it.type  == Channel.Type.GUILD_TEXT }
            .cast(MessageChannel::class.java).blockFirst()

    val server = embeddedServer(Netty, port = System.getenv("PORT")?.toInt() ?: 4100) {
        install(ContentNegotiation) {
            jackson {
                enable(SerializationFeature.INDENT_OUTPUT)
            }
        }
        routing {
            get("/") {
                isCod = call.request.queryParameters["cod"] != null
                call.respondText("""
                    <!DOCTYPE html>
                    <html>
                    <body>
                    <h1>Codbot</h1>
                    <p>Último status:</p>
                    <p id="lastStatus"></p>
                    <script>
                    function updateStatus() {
                        document.getElementById("lastStatus").innerHTML = new Date().toLocaleTimeString();
                    }
                    setInterval(function() {
                      var xhttp = new XMLHttpRequest();
                      xhttp.onreadystatechange = function() {
                        if (this.status == 200) {
                            updateStatus();
                        }
                      };
                      xhttp.open("GET", "keep/alive", true);
                      xhttp.send();
                    }, 60000);
                    updateStatus();
                    </script>
                    </body>
                    </html>
                """.trimIndent(),
                ContentType.Text.Html)
            }
            get("/keep/alive") {
                call.respond(HttpStatusCode.OK, Unit)
            }
            post("/wod/roll/dices", treatRequest<WodDicePoolModel, WodDicePoolDto>(WodDicePoolBotMessage()) { WodDicePoolDto(it.amount, it.difficulty, it.isCanceller, it.isSpecialization) })
            post("/cod/roll/dices", treatRequest<CodDicePoolModel, CodDicePoolDto>(CodDicePoolBotMessage()) { CodDicePoolDto(it.amount, it.explosion, it.isCanceller) })
            post("/roll/initiative", treatRequest<InitiativeModel, Int>(InitiativeBotMessage()) { it.amount })
        }
    }

    server.start(wait = true)
}

@KtorExperimentalAPI
private inline fun <reified TDescription : IDescription, reified T : Any> treatRequest(botMessage: BotMessage<T>, crossinline action: (TDescription) -> T):
        suspend PipelineContext<Unit, ApplicationCall>.(Unit) -> Unit {
    return {
        val authorization = if (call.request.authorization() != null)
            Snowflake.of(call.request.authorization()!!.toBigInteger()) else null

        if (authorization != null && messageChannel != null) {
            val dto: TDescription = call.receive()
            botMessage.send(messageChannel!!, action(dto), authorization, dto.description).subscribe()
            call.respond(HttpStatusCode.OK)
        }

        call.respond(HttpStatusCode.Unauthorized)
    }
}

private fun Flux<MessageCreateEvent>.register(vararg processors: IProcessor): Flux<Unit> {
    return this.flatMap { event ->
        val processor = processors.firstOrNull { it.match(event) }
        return@flatMap processor?.go(event) ?: Flux.just(Unit)
    }
}

fun randomDice() = Random.nextInt(1,11)

fun ArrayList<Int>.format(): String =  if (this.isEmpty()) "-" else this.joinToString(" - ")

var messageChannel: MessageChannel? = null

var modifier: Int? = null

var isCod = false