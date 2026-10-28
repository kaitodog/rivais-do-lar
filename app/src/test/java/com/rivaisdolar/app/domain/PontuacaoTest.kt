package com.rivaisdolar.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class PontuacaoTest {

    @Test
    fun `exemplos do plano`() {
        assertEquals(40, Pontuacao.pontos(minutos = 20, esforco = 1, chatice = 1.3))
        assertEquals(135, Pontuacao.pontos(minutos = 45, esforco = 2, chatice = 1.5))
    }

    @Test
    fun `tarefas do protótipo`() {
        assertEquals(175, Pontuacao.pontos(45, 2, 1.5, diasParada = 3)) // banheiro bomba
        assertEquals(60, Pontuacao.pontos(25, 1, 1.6)) // gaiola
        assertEquals(25, Pontuacao.pontos(15, 1, 1.1)) // roupa
        assertEquals(10, Pontuacao.pontos(5, 1, 1.3)) // lixo
        assertEquals(90, Pontuacao.pontos(60, 1, 1.0)) // mercado
        assertEquals(30, Pontuacao.pontos(20, 0, 1.5)) // cardápio
        assertEquals(45, Pontuacao.pontos(25, 1, 1.2)) // aspirar
    }

    @Test
    fun `mínimo de 5 pontos e meio arredonda pra cima`() {
        assertEquals(5, Pontuacao.pontos(1, 0, 1.0))
        assertEquals(30, Pontuacao.pontos(25, 0, 1.1)) // 27,5 -> 30
    }

    @Test
    fun `bônus bomba sobe 10 por cento ao dia com teto x2`() {
        assertEquals(BigDecimal("1.0"), Pontuacao.bonusBomba(0))
        assertEquals(BigDecimal("1.3"), Pontuacao.bonusBomba(3))
        assertEquals(0, BigDecimal("2.0").compareTo(Pontuacao.bonusBomba(15)))
        assertEquals(80, Pontuacao.pontos(20, 1, 1.3, diasParada = 15))
    }

    @Test
    fun `multiplicador do jogador entra na conta`() {
        assertEquals(80, Pontuacao.pontos(20, 1, 1.3, multiplicador = BigDecimal(2)))
    }

    @Test
    fun `moedas e nível`() {
        assertEquals(4, Pontuacao.moedas(40))
        assertEquals(13, Pontuacao.moedas(135))
        assertEquals(1, Pontuacao.nivel(999))
        assertEquals(2, Pontuacao.nivel(1000))
    }

    @Test
    fun `chatice em double não sofre com ponto flutuante`() {
        // 0.1 * 13 em double vira 1.3000000000000003; o resultado tem que ser o mesmo de 1.3.
        assertEquals(Pontuacao.pontos(20, 1, 1.3), Pontuacao.pontos(20, 1, 0.1 * 13))
    }

    /**
     * A grade em resources/pontos_servidor.csv foi gerada rodando `calcular_pontos` no Postgres
     * (supabase/tests/gerar_grade_pontos.sql). O app tem que mostrar exatamente o que o servidor grava.
     */
    @Test
    fun `app bate com o servidor em toda a grade`() {
        val linhas = javaClass.classLoader!!.getResourceAsStream("pontos_servidor.csv")!!
            .bufferedReader().readLines().drop(1).filter { it.isNotBlank() }
        assertTrue(linhas.size > 5000)
        val divergentes = linhas.mapNotNull { linha ->
            val (m, e, c, d, p) = linha.split(',')
            val app = Pontuacao.pontos(m.toInt(), e.toInt(), c.toDouble(), d.toInt())
            if (app != p.toInt()) "$linha (app=$app)" else null
        }
        assertEquals("Divergências com o servidor: ${divergentes.take(5)}", 0, divergentes.size)
    }
}
