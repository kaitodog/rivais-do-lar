package com.rivaisdolar.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.rivaisdolar.app.ui.theme.Cores
import com.rivaisdolar.app.ui.theme.LocalVidroReal
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * Conteúdo que fica atrás da camada de controles. Só quem está na camada de controles (barra de
 * abas, "+", sheets) recebe este estado; controles dentro do próprio conteúdo não têm o que borrar.
 */
val LocalHaze = compositionLocalOf<HazeState?> { null }

private val estiloBlur = HazeBlurStyle {
    blurRadius(20.dp)
    noiseFactor(0f)
    backgroundColor(Cores.Fundo)
    colorEffects(
        listOf(HazeColorEffect.colorFilter(ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.9f) }))),
    )
}

// Reflexo especular: branco no canto superior esquerdo, some no meio, volta fraco embaixo à direita.
private val bordaEspecular = Brush.linearGradient(
    0f to Color.White.copy(alpha = 0.5f),
    0.35f to Color.White.copy(alpha = 0.06f),
    0.55f to Color.Transparent,
    1f to Color.White.copy(alpha = 0.25f),
    start = Offset.Zero,
    end = Offset.Infinite,
)

/**
 * Liquid Glass recriado: blur do que está atrás (Haze), véu escuro, borda com reflexo, brilho
 * interno no topo e sombra difusa. [tinta] gera a variante tingida da ação principal.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    tinta: Color? = null,
    fundo: Color? = null,
    sombra: Boolean = true,
    alinhamento: Alignment = Alignment.Center,
    conteudo: @Composable BoxScope.() -> Unit,
) {
    val haze = LocalHaze.current
    val podeBorrar = LocalVidroReal.current
    val borrar = haze != null && podeBorrar
    val cor = when {
        tinta != null -> tinta.copy(alpha = 0.82f)
        fundo != null && (borrar || haze == null) -> fundo
        haze != null && !podeBorrar -> Cores.VidroFallback
        else -> Cores.Vidro
    }
    val brilho = if (tinta != null) 0.45f else 0.14f

    Box(
        modifier = modifier
            .then(if (sombra) Modifier.shadow(14.dp, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black) else Modifier)
            .clip(shape)
            .then(haze?.takeIf { borrar }?.let { Modifier.hazeBlur(HazeInput.Sources(it), estiloBlur) } ?: Modifier)
            .background(cor)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = brilho), Color.Transparent),
                        center = Offset(size.width * 0.3f, 0f),
                        radius = maxOf(size.width, size.height) * 0.6f,
                    ),
                )
            }
            .border(1.dp, bordaEspecular, shape),
        contentAlignment = alinhamento,
        content = conteudo,
    )
}
