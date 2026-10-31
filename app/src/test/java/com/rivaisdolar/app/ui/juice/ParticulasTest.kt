package com.rivaisdolar.app.ui.juice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParticulasTest {
    private fun sistema() = SistemaParticulas().apply { definirTamanho(1080f, 1920f, 2f) }

    @Test
    fun `respeita o teto de particulas`() {
        val s = sistema()
        repeat(200) {
            s.emitir(Particula(TipoParticula.Fagulha, 10f, 10f, vida = 1f, vidaMax = 1f))
        }
        assertEquals(SistemaParticulas.LIMITE, s.quantidade)
    }

    @Test
    fun `atualizar apaga o que morreu e desliga o loop`() {
        val s = sistema()
        s.emitir(Particula(TipoParticula.Fagulha, 10f, 10f, vida = 0.01f, vidaMax = 0.01f))
        assertTrue(s.ativo)
        assertFalse(s.atualizar(0.02f))
        assertEquals(0, s.quantidade)
        assertFalse(s.ativo)
    }

    @Test
    fun `fagulha emite e liga o sistema`() {
        val s = sistema()
        Emissores.fagulha(s, androidx.compose.ui.geometry.Offset(500f, 500f), androidx.compose.ui.graphics.Color.White, 12)
        assertEquals(12, s.quantidade)
        assertTrue(s.ativo)
    }

    @Test
    fun `moeda chega ao destino e some`() {
        val s = sistema()
        Emissores.moedas(
            s,
            origem = androidx.compose.ui.geometry.Offset(100f, 800f),
            destino = androidx.compose.ui.geometry.Offset(200f, 100f),
            n = 1,
        )
        // Sem atraso aleatório garantido, avança bastante para qualquer moeda terminar.
        repeat(200) { s.atualizar(0.016f) }
        assertEquals(0, s.quantidade)
    }
}
