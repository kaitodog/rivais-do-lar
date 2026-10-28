package com.rivaisdolar.app.ui.temporada

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rivaisdolar.app.data.model.Temporada
import com.rivaisdolar.app.data.remote.CasaSnapshot
import com.rivaisdolar.app.domain.pt
import com.rivaisdolar.app.ui.components.Aviso
import com.rivaisdolar.app.ui.components.BarraProgresso
import com.rivaisdolar.app.ui.components.BotaoCapsula
import com.rivaisdolar.app.ui.components.BotaoCirculo
import com.rivaisdolar.app.ui.components.BotaoTexto
import com.rivaisdolar.app.ui.components.CabecalhoFolha
import com.rivaisdolar.app.ui.components.CampoTexto
import com.rivaisdolar.app.ui.components.CartaoSolido
import com.rivaisdolar.app.ui.components.EstiloBotao
import com.rivaisdolar.app.ui.components.FolhaVidro
import com.rivaisdolar.app.ui.components.LocalHaze
import com.rivaisdolar.app.ui.components.Simbolo
import com.rivaisdolar.app.ui.components.Texto
import com.rivaisdolar.app.ui.principal.JogoViewModel
import com.rivaisdolar.app.ui.theme.Cores
import com.rivaisdolar.app.ui.theme.LocalReduzirMovimento
import com.rivaisdolar.app.ui.theme.Tipo
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private enum class Resultado { Vitoria, Derrota, Empate, Solo }

@Composable
fun FimTemporadaTela(vm: JogoViewModel, temporadaId: String, aoFechar: () -> Unit) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val ocupado by vm.ocupado.collectAsStateWithLifecycle()
    val aviso by vm.aviso.collectAsStateWithLifecycle()
    val s = snapshot ?: return
    val t = s.ultimaEncerrada?.takeIf { it.id == temporadaId } ?: s.ultimaEncerrada
    if (t == null) {
        LaunchedEffect(Unit) { aoFechar() }
        return
    }
    Box(Modifier.fillMaxSize()) {
        FimTemporadaConteudo(
            s = s,
            t = t,
            ocupado = ocupado,
            aoResgatar = { vm.resgatar(t) },
            aoSalvarAposta = { texto, ok -> vm.definirAposta(texto, ok) },
            aoFechar = {
                vm.marcarVista(t)
                aoFechar()
            },
        )
        Aviso(aviso, vm::limparAviso, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
fun FimTemporadaConteudo(
    s: CasaSnapshot,
    t: Temporada,
    ocupado: Boolean,
    aoResgatar: () -> Unit,
    aoSalvarAposta: (String, () -> Unit) -> Unit,
    aoFechar: () -> Unit,
) {
    var apostando by remember { mutableStateOf<Unit?>(null) }
    val haze = rememberHazeState()

    val resultado = when {
        s.outro == null -> Resultado.Solo
        t.vencedor == null -> Resultado.Empate
        t.vencedor == s.meuId -> Resultado.Vitoria
        else -> Resultado.Derrota
    }
    val ouro = resultado == Resultado.Vitoria

    Box(Modifier.fillMaxSize().background(Cores.Fundo)) {
        Column(
            Modifier
                .fillMaxSize()
                .hazeSource(haze)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(44.dp))
            Comemoracao(t, s, resultado)
            Spacer(Modifier.height(4.dp))
            if (t.aposta != null) CartaoPremio(t, s, resultado, ocupado, aoResgatar)
            CartaoMetaFinal(t)
            Mao()
            BotaoCapsula(
                "Definir aposta da próxima",
                { apostando = Unit },
                Modifier.padding(top = 6.dp),
                estilo = if (ouro) EstiloBotao.Ouro else EstiloBotao.Tingido,
            )
            BotaoTexto("Ver o placar", aoFechar, Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(12.dp))
        }

        CompositionLocalProvider(LocalHaze provides haze) {
            BotaoCirculo("close", "Fechar", aoFechar, Modifier.statusBarsPadding().padding(start = 16.dp, top = 6.dp))
            FolhaVidro(apostando, { apostando = null }) {
                var texto by remember { mutableStateOf(s.temporada?.aposta.orEmpty()) }
                CabecalhoFolha("Aposta da semana", { apostando = null })
                Texto(
                    "Quem vencer a Temporada ${s.temporada?.numero ?: t.numero + 1} escolhe. Ex.: vencedor escolhe o restaurante.",
                    Tipo.pequenoSecundario,
                    Modifier.fillMaxWidth(),
                    alinhamento = TextAlign.Center,
                )
                CampoTexto(texto, { texto = it.take(120) }, "O prêmio de quem vencer", sublinhado = true, teclado = KeyboardType.Text)
                BotaoCapsula(
                    "Salvar aposta",
                    { aoSalvarAposta(texto) { apostando = null } },
                    estilo = if (ouro) EstiloBotao.Ouro else EstiloBotao.Tingido,
                    habilitado = texto.isNotBlank(),
                    carregando = ocupado,
                )
            }
        }
    }
}

@Composable
private fun Comemoracao(t: Temporada, s: CasaSnapshot, resultado: Resultado) {
    val reduzir = LocalReduzirMovimento.current
    val giro = rememberInfiniteTransition(label = "raios")
    val angulo by giro.animateFloat(0f, 360f, infiniteRepeatable(tween(20_000, easing = LinearEasing)), label = "angulo")
    val queda by giro.animateFloat(0f, 1f, infiniteRepeatable(tween(6_000, easing = LinearEasing)), label = "confete")
    val ouro = resultado == Resultado.Vitoria

    val eu = s.pontos(s.meuId)
    val outro = s.outro?.let { o -> t.placar[o.userId] ?: 0 } ?: 0
    val meus = t.placar[s.meuId] ?: eu
    val (titulo, legenda) = when (resultado) {
        Resultado.Vitoria -> "Vitória!" to "Temporada ${t.numero} · ${meus.pt()} a ${outro.pt()}"
        Resultado.Derrota -> "Quase!" to "Semana que vem é sua · ${meus.pt()} a ${outro.pt()}"
        Resultado.Empate -> "Empate!" to "Temporada ${t.numero} · ${meus.pt()} a ${outro.pt()}"
        Resultado.Solo -> "Fim da temporada" to "Temporada ${t.numero} · ${meus.pt()} pontos"
    }

    Box(Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.TopCenter) {
        if (ouro) {
            Canvas(Modifier.size(290.dp).padding(top = 0.dp)) {
                rotate(if (reduzir) 0f else angulo) { raios() }
            }
            Canvas(Modifier.fillMaxWidth().height(250.dp)) { confete(if (reduzir) 0f else queda) }
        }
        Column(Modifier.padding(top = 50.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Canvas(Modifier.size(100.dp)) { trofeu(ouro) }
            Texto(titulo, Tipo.vitoria, cor = if (ouro) Cores.Texto else Cores.Texto)
            Texto(legenda, Tipo.numeroPequeno.copy(fontSize = 13.sp))
        }
    }
}

/** Raios dourados em leque (repeating-conic-gradient do protótipo), apagando para as bordas. */
private fun DrawScope.raios() {
    val raio = size.minDimension / 2
    val centro = Offset(size.width / 2, size.height / 2)
    val fatia = 9f
    var a = 0f
    while (a < 360f) {
        val p = Path().apply {
            moveTo(centro.x, centro.y)
            val r1 = Math.toRadians(a.toDouble())
            val r2 = Math.toRadians((a + fatia).toDouble())
            lineTo(centro.x + raio * cos(r1).toFloat(), centro.y + raio * sin(r1).toFloat())
            lineTo(centro.x + raio * cos(r2).toFloat(), centro.y + raio * sin(r2).toFloat())
            close()
        }
        drawPath(
            p,
            Brush.radialGradient(
                0f to Cores.Ouro.copy(alpha = 0.26f),
                0.25f to Cores.Ouro.copy(alpha = 0.26f),
                1f to Color.Transparent,
                center = centro,
                radius = raio,
            ),
        )
        a += fatia * 2
    }
}

private val pedacos = listOf(
    Triple(0.12f, 0.16f, 25f), Triple(0.22f, 0.44f, -30f), Triple(0.86f, 0.22f, 50f),
    Triple(0.76f, 0.52f, -15f), Triple(0.42f, 0.06f, 70f), Triple(0.62f, 0.12f, -60f),
    Triple(0.06f, 0.62f, 10f), Triple(0.93f, 0.70f, 35f), Triple(0.33f, 0.80f, -45f),
)
private val coresConfete = listOf(Color.White, Cores.Ouro, Color(0xFFF6DE8D))

/** Confete branco e dourado caindo devagar. */
private fun DrawScope.confete(progresso: Float) {
    pedacos.forEachIndexed { i, (x, y, rot) ->
        val dy = ((y + progresso * 0.35f + i * 0.04f) % 1f) * size.height
        val px = x * size.width + sin((progresso * 2 * PI + i).toFloat()) * 6.dp.toPx()
        rotate(rot + progresso * 90f * (if (i % 2 == 0) 1 else -1), pivot = Offset(px, dy)) {
            drawRoundRect(
                coresConfete[i % coresConfete.size],
                topLeft = Offset(px, dy),
                size = Size(5.dp.toPx(), 9.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
            )
        }
    }
}

/** Troféu do protótipo (viewBox 100x100), dourado com gradiente; cinza para quem perdeu. */
private fun DrawScope.trofeu(ouro: Boolean) {
    val k = size.width / 100f
    // Gradiente por peça, como o objectBoundingBox do SVG do protótipo.
    fun gradiente(topo: Float, base: Float) = if (ouro) {
        Brush.verticalGradient(listOf(Color(0xFFFFF4CC), Cores.Ouro, Color(0xFFA97A0C)), startY = topo * k, endY = base * k)
    } else {
        Brush.verticalGradient(listOf(Color(0xFFE5E5EA), Color(0xFF8E8E93), Color(0xFF48484A)), startY = topo * k, endY = base * k)
    }
    val alca = if (ouro) Cores.Ouro else Color(0xFF8E8E93)
    scale(k, k, pivot = Offset.Zero) {
        translate(0f, 0f) {
            val asas = Path().apply {
                moveTo(22f, 24f); lineTo(10f, 24f); cubicTo(10f, 40f, 16f, 46f, 26f, 48f)
                moveTo(78f, 24f); lineTo(90f, 24f); cubicTo(90f, 40f, 84f, 46f, 74f, 48f)
            }
            drawPath(asas, alca, style = Stroke(6f, cap = StrokeCap.Round))
            val taca = Path().apply {
                moveTo(22f, 18f); lineTo(78f, 18f); lineTo(78f, 36f)
                cubicTo(78f, 54f, 66f, 64f, 50f, 64f)
                cubicTo(34f, 64f, 22f, 54f, 22f, 36f)
                close()
            }
            drawPath(taca, gradiente(18f, 64f))
            drawRect(if (ouro) Color(0xFFC9970F) else Color(0xFF636366), Offset(44f, 62f), Size(12f, 14f))
            drawRoundRect(gradiente(76f, 88f), Offset(30f, 76f), Size(40f, 12f), androidx.compose.ui.geometry.CornerRadius(4f))
            val estrela = Path().apply {
                moveTo(50f, 28f); relativeLineTo(4f, 8f); relativeLineTo(9f, 1f); relativeLineTo(-7f, 6f); relativeLineTo(2f, 9f)
                relativeLineTo(-8f, -5f); relativeLineTo(-8f, 5f); relativeLineTo(2f, -9f); relativeLineTo(-7f, -6f); relativeLineTo(9f, -1f); close()
            }
            drawPath(estrela, Color.White.copy(alpha = 0.9f))
        }
    }
}

@Composable
private fun CartaoPremio(t: Temporada, s: CasaSnapshot, resultado: Resultado, ocupado: Boolean, aoResgatar: () -> Unit) {
    val vencedor = s.jogador(t.vencedor)?.nome
    CartaoSolido {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(Cores.Ouro.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Simbolo("redeem", tamanho = 19.sp, cor = Cores.Ouro)
            }
            Column(Modifier.weight(1f)) {
                Texto(t.aposta.orEmpty(), Tipo.tituloCartao)
                Texto(
                    when {
                        t.premioResgatado -> "Prêmio resgatado"
                        resultado == Resultado.Vitoria -> "Prêmio da aposta desta semana"
                        resultado == Resultado.Derrota -> "Quem escolhe é $vencedor"
                        else -> "Ninguém levou a aposta"
                    },
                    Tipo.subtitulo,
                )
            }
            if (resultado == Resultado.Vitoria && !t.premioResgatado) {
                BotaoTexto("Resgatar", aoResgatar, cor = Cores.Ouro, habilitado = !ocupado)
            }
        }
    }
}

@Composable
private fun CartaoMetaFinal(t: Temporada) {
    val batida = t.metaAtual >= t.metaAlvo
    CartaoSolido {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Texto(
                    if (batida) "Meta da casa batida" + (t.metaPremio?.let { " · $it" } ?: "") else "Meta da casa ficou pra próxima",
                    Tipo.tituloCartao,
                    Modifier.weight(1f),
                )
                Texto("${t.metaAtual.pt()} / ${t.metaAlvo.pt()}", Tipo.numeroPequeno)
            }
            BarraProgresso(t.metaAtual.toFloat() / t.metaAlvo.coerceAtLeast(1))
        }
    }
}

/** Mão de cartas (Fase 3): por enquanto, três espaços vazios. */
@Composable
private fun Mao() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) {
            Column(
                Modifier.weight(1f).height(66.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, Cores.Separador, RoundedCornerShape(16.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Simbolo("add", tamanho = 19.sp, cor = Cores.TextoTerciario)
                Texto("Vazio", Tipo.selo.copy(fontSize = 11.sp), cor = Cores.TextoTerciario)
            }
        }
    }
}
