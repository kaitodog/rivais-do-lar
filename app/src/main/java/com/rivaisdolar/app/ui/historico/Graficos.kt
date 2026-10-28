package com.rivaisdolar.app.ui.historico

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivaisdolar.app.domain.pt
import com.rivaisdolar.app.ui.components.Texto
import com.rivaisdolar.app.ui.theme.Cores
import com.rivaisdolar.app.ui.theme.Tipo
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.pow

/**
 * Cores das marcas nos gráficos. O laranja da identidade (#FF9F0A) é claro demais para marcas sobre
 * o fundo escuro; #D07800 é o passo vizinho que passa no validador de paleta (luminosidade, CVD e
 * contraste). Textos nunca usam essas cores.
 */
fun corDeGrafico(cor: String?): Color = if (cor == "laranja") Color(0xFFD07800) else Cores.Azul

data class Serie(val nome: String, val cor: Color)

/** Legenda (sempre presente com duas séries): bolinha colorida + nome em cor de texto. */
@Composable
fun Legenda(series: List<Serie>) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        series.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(s.cor))
                Texto(s.nome, Tipo.subtitulo.copy(color = Cores.Texto))
            }
        }
    }
}

/** Topo "redondo" do eixo: 1.410 vira 1.500 (passos de 1, 2, 2,5 ou 5 vezes a potência de 10). */
fun topoDoEixo(maximo: Int): Int {
    if (maximo <= 0) return 100
    val potencia = 10.0.pow(kotlin.math.floor(log10(maximo.toDouble())))
    val passo = listOf(1.0, 2.0, 2.5, 5.0, 10.0).first { it * potencia >= maximo / 1.0 }
    return (ceil(maximo / (passo * potencia / 2)) * (passo * potencia / 2)).toInt()
}

private fun compacto(v: Int): String = if (v >= 1000) "${(v / 100) / 10.0}".replace(".0", "").replace('.', ',') + " mil" else v.toString()

private fun DrawScope.grade(medidor: TextMeasurer, topo: Int, base: Float, alto: Float, esquerda: Float) {
    listOf(0, topo / 2, topo).forEach { v ->
        val y = base - (v.toFloat() / topo) * (base - alto)
        drawLine(Cores.Separador.copy(alpha = 0.5f), Offset(esquerda, y), Offset(size.width, y), strokeWidth = 1f)
        val texto = medidor.measure(compacto(v), Tipo.numeroPequeno.copy(fontSize = 10.sp, color = Cores.TextoTerciario))
        drawText(texto, topLeft = Offset(0f, y - texto.size.height / 2))
    }
}

data class GrupoColunas(val rotulo: String, val valores: List<Int>)

/**
 * Colunas agrupadas (uma por jogador) por temporada. Toque num grupo para ler os valores.
 * Colunas com no máximo 24dp, topo arredondado de 4dp, base reta e 2dp de folga entre vizinhas.
 */
@Composable
fun ColunasAgrupadas(
    grupos: List<GrupoColunas>,
    series: List<Serie>,
    selecionado: Int?,
    aoSelecionar: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val medidor = rememberTextMeasurer()
    val topo = topoDoEixo(grupos.flatMap { it.valores }.maxOrNull() ?: 0)
    val descricao = grupos.joinToString("; ") { g -> g.rotulo + ": " + g.valores.mapIndexed { i, v -> "${series.getOrNull(i)?.nome} ${v.pt()}" }.joinToString(", ") }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(190.dp)
            .semantics { contentDescription = "Pontos por temporada. $descricao" }
            .pointerInput(grupos.size) {
                detectTapGestures { p ->
                    val esquerda = 34.dp.toPx()
                    val largura = (size.width - esquerda) / grupos.size.coerceAtLeast(1)
                    val i = ((p.x - esquerda) / largura).toInt()
                    aoSelecionar(if (i in grupos.indices && i != selecionado) i else null)
                }
            },
    ) {
        val esquerda = 34.dp.toPx()
        val base = size.height - 22.dp.toPx()
        val alto = 8.dp.toPx()
        grade(medidor, topo, base, alto, esquerda)
        val largura = (size.width - esquerda) / grupos.size.coerceAtLeast(1)
        val folga = 2.dp.toPx()
        val coluna = minOf(24.dp.toPx(), (largura * 0.7f - folga) / series.size.coerceAtLeast(1))
        val raio = 4.dp.toPx()
        grupos.forEachIndexed { gi, g ->
            val centro = esquerda + largura * gi + largura / 2
            val total = coluna * g.valores.size + folga * (g.valores.size - 1)
            val apagado = selecionado != null && selecionado != gi
            g.valores.forEachIndexed { si, v ->
                val x = centro - total / 2 + si * (coluna + folga)
                val h = (v.toFloat() / topo) * (base - alto)
                if (h > 0f) {
                    val forma = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = x, top = base - h, right = x + coluna, bottom = base,
                                topLeftCornerRadius = CornerRadius(minOf(raio, h)), topRightCornerRadius = CornerRadius(minOf(raio, h)),
                                bottomLeftCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero,
                            ),
                        )
                    }
                    drawPath(forma, series.getOrNull(si)?.cor?.copy(alpha = if (apagado) 0.35f else 1f) ?: Color.Gray)
                }
            }
            val rotulo = medidor.measure(g.rotulo, Tipo.numeroPequeno.copy(fontSize = 10.sp, color = if (selecionado == gi) Cores.Texto else Cores.TextoSecundario))
            drawText(rotulo, topLeft = Offset(centro - rotulo.size.width / 2, base + 6.dp.toPx()))
        }
    }
}

/**
 * Linhas acumuladas da semana (2dp, juntas e pontas redondas), com bolinha de 8dp e anel da cor
 * do fundo no último ponto. Toque num dia para a linha-guia e a leitura.
 */
@Composable
fun LinhasAcumuladas(
    dias: List<String>,
    valores: List<List<Int>>,
    series: List<Serie>,
    selecionado: Int?,
    aoSelecionar: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val medidor = rememberTextMeasurer()
    val topo = topoDoEixo(valores.flatten().maxOrNull() ?: 0)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(190.dp)
            .semantics { contentDescription = "Pontos acumulados na semana: " + series.mapIndexed { i, s -> "${s.nome} ${valores.getOrNull(i)?.lastOrNull() ?: 0}" }.joinToString(", ") }
            .pointerInput(dias.size) {
                detectTapGestures { p ->
                    val esquerda = 34.dp.toPx()
                    val passo = (size.width - esquerda - 12.dp.toPx()) / (dias.size - 1).coerceAtLeast(1)
                    val i = Math.round((p.x - esquerda) / passo)
                    val ultimo = (valores.maxOfOrNull { it.size } ?: 0) - 1
                    aoSelecionar(if (i in 0..ultimo && i != selecionado) i else null)
                }
            },
    ) {
        val esquerda = 34.dp.toPx()
        val base = size.height - 22.dp.toPx()
        val alto = 8.dp.toPx()
        grade(medidor, topo, base, alto, esquerda)
        val passo = (size.width - esquerda - 12.dp.toPx()) / (dias.size - 1).coerceAtLeast(1)
        fun ponto(i: Int, v: Int) = Offset(esquerda + i * passo, base - (v.toFloat() / topo) * (base - alto))

        dias.forEachIndexed { i, d ->
            val t = medidor.measure(d, Tipo.numeroPequeno.copy(fontSize = 10.sp, color = if (i == selecionado) Cores.Texto else Cores.TextoSecundario))
            drawText(t, topLeft = Offset(esquerda + i * passo - t.size.width / 2, base + 6.dp.toPx()))
        }
        if (selecionado != null) {
            val x = esquerda + selecionado * passo
            drawLine(Cores.TextoTerciario, Offset(x, alto), Offset(x, base), strokeWidth = 1f)
        }
        valores.forEachIndexed { si, serie ->
            if (serie.isEmpty()) return@forEachIndexed
            val cor = series.getOrNull(si)?.cor ?: Color.Gray
            val caminho = Path().apply {
                serie.forEachIndexed { i, v -> ponto(i, v).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
            }
            drawPath(caminho, cor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            val marcado = selecionado?.takeIf { it < serie.size } ?: serie.lastIndex
            val fim = ponto(marcado, serie[marcado])
            drawCircle(Cores.Superficie, radius = 6.dp.toPx(), center = fim)
            drawCircle(cor, radius = 4.dp.toPx(), center = fim)
        }
    }
}

/** Visão em tabela dos mesmos dados (acessibilidade e leitura exata). */
@Composable
fun TabelaDados(cabecalho: List<String>, linhas: List<List<String>>) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            cabecalho.forEachIndexed { i, c ->
                Texto(c, Tipo.secao, Modifier.weight(1f), alinhamento = if (i == 0) null else androidx.compose.ui.text.style.TextAlign.End)
            }
        }
        linhas.forEach { l ->
            Row(Modifier.fillMaxWidth()) {
                l.forEachIndexed { i, c ->
                    Texto(c, if (i == 0) Tipo.pequeno else Tipo.numero.copy(fontSize = 13.sp), Modifier.weight(1f), alinhamento = if (i == 0) null else androidx.compose.ui.text.style.TextAlign.End)
                }
            }
        }
    }
}

