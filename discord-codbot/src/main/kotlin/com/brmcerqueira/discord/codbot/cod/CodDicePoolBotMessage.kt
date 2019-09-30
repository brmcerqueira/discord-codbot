package com.brmcerqueira.discord.codbot.cod

import com.brmcerqueira.discord.codbot.BotMessage
import com.brmcerqueira.discord.codbot.DicePoolDto
import com.brmcerqueira.discord.codbot.modifier
import kotlin.math.abs

class CodDicePoolBotMessage : BotMessage<DicePoolDto>() {
    override fun buildMessage(dto: DicePoolDto, stringBuffer: StringBuffer) {
        val modifierValue = modifier

        if (modifier != null) {
            modifier = null
        }

        val dicePool = CodDicePool(dto, modifierValue)

        stringBuffer.appendln("```md")
        stringBuffer.append("[ Dados: ")

        if (modifierValue != null) {
            stringBuffer.append(dto.amount + modifierValue)
            stringBuffer.append(" = ")
            stringBuffer.append(dto.amount)
            stringBuffer.append(if (modifierValue > 0) " + " else " - ")
            stringBuffer.append(abs(modifierValue))
            stringBuffer.append(" ")
        }
        else {
            stringBuffer.append(dto.amount)
            stringBuffer.append(" ")
        }

        stringBuffer.append("]")

        if (dto.explosion in 8..10) {
            stringBuffer.appendln("( Explosão: ${dto.explosion} )")
        }
        else {
            stringBuffer.appendln("( Sem explosão )")
        }

        if (dto.isCanceller) {
            stringBuffer.appendln("> O '1' cancela sucesso.")
        }

        stringBuffer.appendln("# Resultado")
        stringBuffer.appendln("[ ${dicePool.successDices.format()} ][ ${dicePool.failureDices.format()} ]")

        if (dicePool.isCriticalFailure) {
            stringBuffer.appendln("/* Falha Crítica *")
        }
        else {
            stringBuffer.appendln("< Sucessos = ${dicePool.successes} >")
        }

        stringBuffer.append("```")
    }

    private fun ArrayList<Int>.format(): String =  if (this.isEmpty()) "-" else this.joinToString(" - ")
}