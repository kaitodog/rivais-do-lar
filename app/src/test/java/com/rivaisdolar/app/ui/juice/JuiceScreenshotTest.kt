package com.rivaisdolar.app.ui.juice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.rivaisdolar.app.ui.theme.Cores
import com.rivaisdolar.app.ui.theme.RivaisTema
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Confere que o renderer de partículas desenha de verdade (e não só compila). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class JuiceScreenshotTest {
    @get:Rule val regra = createComposeRule()

    @Test
    fun particulas() {
        val sistema = SistemaParticulas().apply {
            definirTamanho(1080f, 2340f, 3f)
            Emissores.fagulha(this, Offset(540f, 820f), Cores.Azul, 40, 1.2f)
            Emissores.confete(this, 60)
            Emissores.moedas(this, Offset(540f, 1500f), Offset(160f, 180f), 8)
            Emissores.anel(this, Offset(540f, 820f), Cores.Ouro)
            Emissores.brasa(this, Offset(140f, 900f), 8)
            repeat(8) { atualizar(0.04f) }
        }

        regra.setContent {
            RivaisTema {
                Box(Modifier.fillMaxSize().background(Cores.Fundo)) {
                    Canvas(Modifier.fillMaxSize()) { sistema.desenhar(this) }
                }
            }
        }
        regra.mainClock.advanceTimeBy(200)
        regra.onRoot().captureRoboImage("build/outputs/roborazzi/10-particulas.png")
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onRoot() =
        onNode(androidx.compose.ui.test.isRoot())
}
