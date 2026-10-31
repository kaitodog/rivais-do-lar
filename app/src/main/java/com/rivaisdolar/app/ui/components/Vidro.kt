package com.rivaisdolar.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.rivaisdolar.app.ui.juice.LocalInclinacao
import com.rivaisdolar.app.ui.theme.Cores
import com.rivaisdolar.app.ui.theme.LocalReduzirMovimento
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
    // Grão fino: dá materialidade ao vidro em vez de um borrão liso.
    noiseFactor(0.015f)
    backgroundColor(Cores.Fundo)
    colorEffects(
        listOf(HazeColorEffect.colorFilter(ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.9f) }))),
    )
}

/**
 * Liquid Glass recriado: blur do que está atrás (Haze), véu escuro, borda com reflexo que reage à
 * inclinação, brilho interno no topo, caustica tingida e sombra difusa. [tinta] gera a variante
 * tingida da ação principal.
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
    val reduzir = LocalReduzirMovimento.current
    val inclinacao = LocalInclinacao.current
    val borrar = haze != null && podeBorrar
    val cor = when {
        tinta != null -> tinta.copy(alpha = 0.82f)
        fundo != null && (borrar || haze == null) -> fundo
        haze != null && !podeBorrar -> Cores.VidroFallback
        else -> Cores.Vidro
    }
    val brilho = if (tinta != null) 0.45f else 0.14f

    val densidade = LocalDensity.current
    val direcao = LocalLayoutDirection.current

    // Pulso de refração: a lente engrossa no toque e volta. Só no Android 13+ (AGSL).
    var pressionado by remember { mutableStateOf(false) }
    val pulso = remember { Animatable(0f) }
    LaunchedEffect(pressionado) {
        if (reduzir) {
            pulso.snapTo(0f)
        } else if (pressionado) {
            pulso.animateTo(1f, tween(150))
        } else {
            pulso.animateTo(0f, tween(240))
        }
    }

    val observarToque = if (podeBorrar && !reduzir) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressionado = true
                waitForUpOrCancellation()
                pressionado = false
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(if (sombra) Modifier.shadow(14.dp, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black) else Modifier)
            .clip(shape)
            .then(observarToque),
        contentAlignment = alinhamento,
    ) {
        // Camada de fundo: blur do que está atrás, com a lente AGSL na borda (Android 13+).
        Box(
            Modifier
                .matchParentSize()
                .then(
                    if (haze != null && podeBorrar) {
                        Modifier
                            .lenteDeVidro(
                                raioPx = { w, h ->
                                    when (val o = shape.createOutline(Size(w, h), direcao, densidade)) {
                                        is Outline.Rounded -> o.roundRect.topLeftCornerRadius.x
                                        else -> 0f
                                    }
                                },
                                bordaPx = { with(densidade) { (8.dp + 4.dp * pulso.value).toPx() } },
                            )
                            .hazeBlur(HazeInput.Sources(haze), estiloBlur)
                    } else {
                        Modifier
                    },
                )
                .background(cor)
                .drawWithCache {
                    val dx = inclinacao.valor.x
                    val dy = inclinacao.valor.y
                    val borda = Brush.linearGradient(
                        0f to Color.White.copy(alpha = 0.5f),
                        0.35f to Color.White.copy(alpha = 0.06f),
                        0.55f to Color.Transparent,
                        1f to Color.White.copy(alpha = 0.25f),
                        start = Offset(size.width * (-0.15f + dx * 0.10f), size.height * (-0.15f + dy * 0.10f)),
                        end = Offset(size.width * (1.15f + dx * 0.10f), size.height * (1.15f + dy * 0.10f)),
                    )
                    val contorno = shape.createOutline(size, direcao, this)
                    onDrawWithContent {
                        drawContent()
                        // Brilho interno no topo, deslocado pela inclinação.
                        drawRect(
                            Brush.radialGradient(
                                listOf(Color.White.copy(alpha = brilho), Color.Transparent),
                                center = Offset(size.width * (0.3f + dx * 0.10f), size.height * (dy * 0.06f)),
                                radius = maxOf(size.width, size.height) * 0.6f,
                            ),
                        )
                        // Caustica: a luz do jogador atravessa o vidro tingido por baixo.
                        if (tinta != null) {
                            drawRect(
                                Brush.radialGradient(
                                    listOf(tinta.copy(alpha = 0.20f), Color.Transparent),
                                    center = Offset(size.width * (0.80f + dx * 0.06f), size.height * (1.05f + dy * 0.06f)),
                                    radius = maxOf(size.width, size.height) * 0.95f,
                                ),
                            )
                        }
                        drawOutline(contorno, borda, style = Stroke(width = 1.dp.toPx()))
                    }
                },
        )
        conteudo()
    }
}
