package com.rivaisdolar.app.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Espelho da função `calcular_pontos` do servidor, usado só para exibir valores antes da validação.
 * O valor que conta é sempre o calculado no Postgres. BigDecimal evita que 27,5 vire 27,4999.
 */
object Pontuacao {
    private val CINCO = BigDecimal(5)
    private val TETO_BOMBA = BigDecimal("2.0")
    private val DEZ_PORCENTO = BigDecimal("0.1")

    const val TETO_DIARIO = 400
    const val XP_POR_NIVEL = 1000

    fun multiplicadorEsforco(esforco: Int): BigDecimal = when (esforco) {
        0 -> BigDecimal("1.0")
        1 -> BigDecimal("1.5")
        else -> BigDecimal("2.0")
    }

    fun bonusBomba(diasParada: Int): BigDecimal =
        (BigDecimal.ONE + DEZ_PORCENTO * BigDecimal(diasParada.coerceAtLeast(0))).min(TETO_BOMBA)

    fun pontos(
        minutos: Int,
        esforco: Int,
        chatice: Double,
        diasParada: Int = 0,
        multiplicador: BigDecimal = BigDecimal.ONE,
    ): Int {
        val chat = BigDecimal.valueOf(chatice).setScale(1, RoundingMode.HALF_UP)
        val bruto = BigDecimal(minutos) * multiplicadorEsforco(esforco) * chat * bonusBomba(diasParada) * multiplicador
        val arredondado = bruto.divide(CINCO).setScale(0, RoundingMode.HALF_UP).toInt() * 5
        return maxOf(5, arredondado)
    }

    fun moedas(pontos: Int): Int = pontos / 10

    fun nivel(xp: Int): Int = 1 + xp / XP_POR_NIVEL
}
