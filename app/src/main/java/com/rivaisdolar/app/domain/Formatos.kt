package com.rivaisdolar.app.domain

import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale

val PT_BR: Locale = Locale.forLanguageTag("pt-BR")

private val inteiros = NumberFormat.getIntegerInstance(PT_BR)

fun Int.pt(): String = inteiros.format(this)

/** 1.5 -> "1,5" */
fun Double.decimal(): String = String.format(PT_BR, "%.1f", this)

fun String.instante(): Instant = OffsetDateTime.parse(this).toInstant()

fun String.instanteOuNull(): Instant? = runCatching { instante() }.getOrNull()

/** "5h 42min", "12min", "2 dias" */
fun Duration.curta(): String {
    val total = toMinutes().coerceAtLeast(0)
    val dias = total / (60 * 24)
    val horas = (total / 60) % 24
    val min = total % 60
    return when {
        dias >= 2 -> "$dias dias"
        dias == 1L -> "1 dia ${horas}h"
        horas > 0 -> "${horas}h ${min}min"
        else -> "${min}min"
    }
}

/** "agora", "há 12 min", "há 3 h", "há 2 dias" */
fun Instant.relativo(agora: Instant = Instant.now()): String {
    val min = Duration.between(this, agora).toMinutes()
    return when {
        min < 1 -> "agora"
        min < 60 -> "há $min min"
        min < 60 * 24 -> "há ${min / 60} h"
        min < 60 * 48 -> "ontem"
        else -> "há ${min / (60 * 24)} dias"
    }
}

fun plural(n: Int, singular: String, plural: String) = if (n == 1) "$n $singular" else "$n $plural"
