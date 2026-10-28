package com.rivaisdolar.app.data

import com.rivaisdolar.app.data.remote.AtualizacaoRepository.Companion.versaoMaior
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AtualizacaoTest {
    @Test
    fun `compara versões semânticas`() {
        assertTrue(versaoMaior("0.2.0", "0.1.0"))
        assertTrue(versaoMaior("0.10.0", "0.9.3"))
        assertTrue(versaoMaior("1.0", "0.99.99"))
        assertTrue(versaoMaior("0.1.1", "0.1"))
        assertFalse(versaoMaior("0.1.0", "0.1.0"))
        assertFalse(versaoMaior("0.1.0", "0.2.0"))
        assertFalse(versaoMaior("0.1.0-beta", "0.1.0"))
    }
}
