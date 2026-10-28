package com.rivaisdolar.app.ui.placar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivaisdolar.app.domain.pt
import com.rivaisdolar.app.ui.components.GlassSurface
import com.rivaisdolar.app.ui.components.Simbolo
import com.rivaisdolar.app.ui.components.Texto
import com.rivaisdolar.app.ui.theme.Cores
import com.rivaisdolar.app.ui.theme.LocalReduzirMovimento
import com.rivaisdolar.app.ui.theme.Tipo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

/** Saldo de moedas no topo do placar; o número rola quando as moedas chegam. */
@Composable
fun ContadorMoedas(moedas: Int, modifier: Modifier = Modifier) {
    val reduzir = LocalReduzirMovimento.current
    val animado by animateIntAsState(moedas, if (reduzir) snap() else tween(700), label = "moedas")
    GlassSurface(modifier.height(38.dp), CircleShape) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Simbolo("toll", tamanho = 18.sp, cor = Cores.Ouro, descricao = "Moedas")
            Texto(animado.pt(), Tipo.numero)
        }
    }
}

/**
 * Moedas "voando" de [origem] até [destino] (coordenadas locais do contêiner), em arco, uma atrás
 * da outra. Some sozinho no fim.
 */
@Composable
fun VooDeMoedas(origem: Offset, destino: Offset, quantidade: Int) {
    val tamanho = with(LocalDensity.current) { 22.dp.toPx() }
    val progressos = remember { List(quantidade) { Animatable(0f) } }
    val desvios = remember { List(quantidade) { Offset(Random.nextFloat() * 120f - 60f, Random.nextFloat() * 60f - 30f) } }
    LaunchedEffect(Unit) {
        progressos.forEachIndexed { i, p ->
            launch {
                delay(i * 70L)
                p.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        progressos.forEachIndexed { i, p ->
            val t = p.value
            if (t <= 0f || t >= 1f) return@forEachIndexed
            val inicio = origem + desvios[i]
            // Curva quadrática com o ponto de controle acima da reta, para fazer o arco.
            val controle = Offset((inicio.x + destino.x) / 2 + desvios[i].x, minOf(inicio.y, destino.y) - 160f)
            val u = 1 - t
            val ponto = inicio * (u * u) + controle * (2 * u * t) + destino * (t * t)
            Box(
                Modifier
                    .offset { IntOffset((ponto.x - tamanho / 2).roundToInt(), (ponto.y - tamanho / 2).roundToInt()) }
                    .graphicsLayer {
                        alpha = if (t > 0.85f) (1f - t) / 0.15f else 1f
                        val escala = 1f - 0.35f * t
                        scaleX = escala
                        scaleY = escala
                    },
            ) { Simbolo("toll", tamanho = 22.sp, cor = Cores.Ouro) }
        }
    }
}

/** Decide quando disparar o voo: só quando o saldo sobe com a tela aberta. Devolve (saldo exibido, voos). */
@Composable
fun rememberChegadaDeMoedas(moedas: Int): Pair<Int, Int> {
    val reduzir = LocalReduzirMovimento.current
    var exibidas by remember { mutableIntStateOf(moedas) }
    var voos by remember { mutableIntStateOf(0) }
    LaunchedEffect(moedas) {
        if (moedas > exibidas && !reduzir) {
            voos++
            // O número só sobe quando as moedas chegam no contador.
            delay(750)
        }
        exibidas = moedas
    }
    return exibidas to voos
}
