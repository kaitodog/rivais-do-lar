package com.rivaisdolar.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivaisdolar.app.ui.theme.Cores
import com.rivaisdolar.app.ui.theme.LocalDestaque
import com.rivaisdolar.app.ui.theme.Simbolos
import com.rivaisdolar.app.ui.theme.Tipo

// Formas da seção 4: cápsulas, cartões de 22dp e ícones de tarefa de 32dp com cantos de 9dp.
val FormaCartao = RoundedCornerShape(22.dp)
val FormaIcone = RoundedCornerShape(9.dp)
val FormaCampo = RoundedCornerShape(14.dp)

@Composable
fun Texto(
    texto: String,
    estilo: TextStyle,
    modifier: Modifier = Modifier,
    cor: Color = Color.Unspecified,
    linhas: Int = Int.MAX_VALUE,
    alinhamento: TextAlign? = null,
) {
    BasicText(
        text = texto,
        modifier = modifier,
        style = estilo.let { if (cor != Color.Unspecified) it.copy(color = cor) else it }
            .let { if (alinhamento != null) it.copy(textAlign = alinhamento) else it },
        maxLines = linhas,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Material Symbols Rounded, outline, peso 400, desenhado por ligadura. */
@Composable
fun Simbolo(
    nome: String,
    modifier: Modifier = Modifier,
    tamanho: TextUnit = 20.sp,
    cor: Color = Cores.Texto,
    descricao: String? = null,
) {
    BasicText(
        text = nome,
        modifier = modifier.clearAndSetSemantics { if (descricao != null) contentDescription = descricao },
        style = TextStyle(fontFamily = Simbolos, fontSize = tamanho, lineHeight = tamanho, color = cor, fontWeight = FontWeight.Normal),
        maxLines = 1,
    )
}

/** Toque com leve encolhimento, no lugar do ripple do Material. */
fun Modifier.toque(habilitado: Boolean = true, papel: Role = Role.Button, aoTocar: () -> Unit): Modifier = composed {
    val interacao = remember { MutableInteractionSource() }
    val pressionado by interacao.collectIsPressedAsState()
    val escala by animateFloatAsState(if (pressionado) 0.95f else 1f, label = "toque")
    this
        .graphicsLayer {
            scaleX = escala
            scaleY = escala
        }
        .clickable(interacao, indication = null, enabled = habilitado, role = papel, onClick = aoTocar)
}

/** Linha de lista: destaca o fundo enquanto pressionada. */
fun Modifier.toqueLinha(habilitado: Boolean = true, aoTocar: () -> Unit): Modifier = composed {
    val interacao = remember { MutableInteractionSource() }
    val pressionado by interacao.collectIsPressedAsState()
    this
        .background(if (pressionado) Color.White.copy(alpha = 0.07f) else Color.Transparent)
        .clickable(interacao, indication = null, enabled = habilitado, onClick = aoTocar)
}

@Composable
fun TituloGrande(titulo: String, subtitulo: String? = null, modifier: Modifier = Modifier) {
    Column(modifier) {
        Texto(titulo, Tipo.tituloGrande)
        if (subtitulo != null) {
            Spacer(Modifier.height(3.dp))
            Texto(subtitulo, Tipo.pequenoSecundario)
        }
    }
}

@Composable
fun Secao(texto: String, modifier: Modifier = Modifier) {
    Texto(texto.uppercase(), Tipo.secao, modifier.padding(start = 16.dp, bottom = 6.dp))
}

@Composable
fun CartaoSolido(modifier: Modifier = Modifier, conteudo: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(FormaCartao).background(Cores.Superficie), content = conteudo)
}

/** Lista agrupada: cartão sólido cujas linhas são separadas por divisórias de 0,5dp. */
@Composable
fun Grupo(modifier: Modifier = Modifier, conteudo: @Composable ColumnScope.() -> Unit) {
    CartaoSolido(modifier, conteudo)
}

@Composable
fun Divisoria(inicio: Dp = 58.dp) {
    Box(Modifier.padding(start = inicio).fillMaxWidth().height(0.5.dp).background(Cores.Separador))
}

@Composable
fun IconeTarefa(icone: String, bomba: Boolean = false, cor: Color? = null) {
    Box(
        Modifier.size(32.dp).clip(FormaIcone).background(
            when {
                bomba -> Cores.Alerta.copy(alpha = 0.18f)
                cor != null -> cor.copy(alpha = 0.16f)
                else -> Cores.Preenchimento
            },
        ),
        contentAlignment = Alignment.Center,
    ) {
        Simbolo(icone, tamanho = 19.sp, cor = if (bomba) Cores.Alerta else cor ?: Cores.Texto)
    }
}

@Composable
fun Linha(
    titulo: String,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    corSubtitulo: Color = Cores.TextoSecundario,
    inicio: (@Composable () -> Unit)? = null,
    depoisDoTitulo: (@Composable RowScope.() -> Unit)? = null,
    fim: (@Composable RowScope.() -> Unit)? = null,
    seta: Boolean = false,
    corTitulo: Color = Cores.Texto,
    aoTocar: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (aoTocar != null) Modifier.toqueLinha(aoTocar = aoTocar) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        inicio?.invoke()
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Texto(titulo, Tipo.linha, Modifier.weight(1f, fill = false), cor = corTitulo, linhas = 1)
                depoisDoTitulo?.invoke(this)
            }
            if (subtitulo != null) {
                Spacer(Modifier.height(1.dp))
                Texto(subtitulo, Tipo.subtitulo, cor = corSubtitulo, linhas = 2)
            }
        }
        fim?.invoke(this)
        if (seta) Simbolo("chevron_right", tamanho = 18.sp, cor = Cores.TextoTerciario)
    }
}

@Composable
fun Ponto(cor: Color) {
    Box(Modifier.size(7.dp).clip(CircleShape).background(cor))
}

@Composable
fun Avatar(nome: String, cor: Color, tamanho: Dp = 26.dp) {
    Box(Modifier.size(tamanho).clip(CircleShape).background(cor), contentAlignment = Alignment.Center) {
        Texto(
            nome.trim().take(1).uppercase().ifEmpty { "?" },
            Tipo.rotulo.copy(fontSize = (tamanho.value * 0.46f).sp, fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
fun Selo(texto: String, cor: Color) {
    Texto(
        texto,
        Tipo.selo,
        Modifier.clip(CircleShape).background(cor.copy(alpha = 0.18f)).padding(horizontal = 7.dp, vertical = 2.dp),
        cor = cor,
    )
}

@Composable
fun BarraProgresso(fracao: Float, modifier: Modifier = Modifier, cor: Color = Cores.Texto) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Cores.Preenchimento)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fracao.coerceIn(0f, 1f)).clip(RoundedCornerShape(3.dp)).background(cor))
    }
}

/** Barra do placar: cada jogador ocupa a fração proporcional aos seus pontos. */
@Composable
fun BarraDividida(a: Int, b: Int, corA: Color, corB: Color, modifier: Modifier = Modifier) {
    val total = (a + b).coerceAtLeast(1)
    val fracA = if (a + b == 0) 0.5f else (a.toFloat() / total).coerceIn(0.04f, 0.96f)
    val animada by animateFloatAsState(fracA, tween(900), label = "placar")
    Row(modifier.fillMaxWidth().height(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(Modifier.weight(animada).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(corA))
        Box(Modifier.weight(1f - animada).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(corB))
    }
}

/** Botão circular de vidro (38dp no topo das telas). */
@Composable
fun BotaoCirculo(
    icone: String,
    descricao: String,
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
    tamanho: Dp = 38.dp,
    marcador: Color? = null,
) {
    Box(modifier.size(tamanho)) {
        GlassSurface(Modifier.size(tamanho).toque(aoTocar = aoTocar)) {
            Simbolo(icone, tamanho = 19.sp, descricao = descricao)
        }
        if (marcador != null) {
            Box(Modifier.align(Alignment.TopEnd).size(10.dp).clip(CircleShape).background(marcador).border(1.5.dp, Cores.Fundo, CircleShape))
        }
    }
}

/** Fechar dentro de um sheet: só um círculo preenchido, nunca vidro sobre vidro. */
@Composable
fun BotaoFechar(aoTocar: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(32.dp).clip(CircleShape).background(Color(0x24787880)).toque(aoTocar = aoTocar),
        contentAlignment = Alignment.Center,
    ) {
        Simbolo("close", tamanho = 18.sp, cor = Cores.TextoSecundario, descricao = "Fechar")
    }
}

enum class EstiloBotao { Tingido, Ouro, Vidro, Solido }

@Composable
fun BotaoCapsula(
    texto: String,
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
    estilo: EstiloBotao = EstiloBotao.Tingido,
    habilitado: Boolean = true,
    carregando: Boolean = false,
    icone: String? = null,
    tinta: Color = LocalDestaque.current,
) {
    val ativo = habilitado && !carregando
    val conteudo: @Composable BoxScope.() -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (carregando) {
                Girando(tamanho = 18.dp, cor = Color.White)
            } else if (icone != null) {
                Simbolo(icone, tamanho = 19.sp)
            }
            Texto(texto, Tipo.botao)
        }
    }
    val base = modifier.fillMaxWidth().height(52.dp).graphicsLayer { alpha = if (habilitado) 1f else 0.45f }
        .toque(habilitado = ativo, aoTocar = aoTocar)
    when (estilo) {
        EstiloBotao.Tingido -> GlassSurface(base, CircleShape, tinta = tinta, conteudo = conteudo)
        EstiloBotao.Ouro -> GlassSurface(base, CircleShape, tinta = Cores.OuroBotao, conteudo = conteudo)
        EstiloBotao.Vidro -> GlassSurface(base, CircleShape, conteudo = conteudo)
        EstiloBotao.Solido -> Box(base.clip(CircleShape).background(Cores.SuperficieAlta), contentAlignment = Alignment.Center, content = conteudo)
    }
}

/** Ação secundária: só texto (permitido dentro de sheets de vidro). */
@Composable
fun BotaoTexto(texto: String, aoTocar: () -> Unit, modifier: Modifier = Modifier, cor: Color = Cores.Texto, habilitado: Boolean = true) {
    Box(
        modifier.widthIn(min = 44.dp).height(44.dp).toque(habilitado = habilitado, aoTocar = aoTocar).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Texto(texto, Tipo.botao.copy(fontSize = 15.sp), cor = if (habilitado) cor else Cores.TextoTerciario)
    }
}

/** Controle segmentado em cápsula de vidro. */
@Composable
fun Segmentado(opcoes: List<String>, selecionado: Int, aoEscolher: (Int) -> Unit, modifier: Modifier = Modifier) {
    GlassSurface(modifier.fillMaxWidth().height(36.dp), CircleShape, sombra = false) {
        Row(Modifier.fillMaxWidth().padding(3.dp)) {
            opcoes.forEachIndexed { i, opcao ->
                val ativo = i == selecionado
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(if (ativo) Color.White.copy(alpha = 0.16f) else Color.Transparent)
                        .toque(papel = Role.Tab) { aoEscolher(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Texto(opcao, Tipo.pequeno.copy(fontWeight = if (ativo) FontWeight.SemiBold else FontWeight.Medium), linhas = 1)
                }
            }
        }
    }
}

/** Fichas de escolha (sólidas, ficam dentro do conteúdo). */
@Composable
fun Fichas(opcoes: List<String>, selecionado: Int, aoEscolher: (Int) -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        opcoes.forEachIndexed { i, opcao ->
            val ativo = i == selecionado
            Box(
                Modifier
                    .height(34.dp)
                    .clip(CircleShape)
                    .background(if (ativo) Color.White else Cores.SuperficieAlta)
                    .toque { aoEscolher(i) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Texto(opcao, Tipo.pequeno.copy(fontWeight = FontWeight.SemiBold), cor = if (ativo) Color.Black else Cores.Texto)
            }
        }
    }
}

@Composable
fun Interruptor(ligado: Boolean, aoMudar: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val destaque = LocalDestaque.current
    val posicao by animateFloatAsState(if (ligado) 1f else 0f, label = "interruptor")
    Box(
        modifier
            .width(51.dp)
            .height(31.dp)
            .clip(CircleShape)
            .background(if (ligado) destaque else Cores.Preenchimento)
            .toque(papel = Role.Switch) { aoMudar(!ligado) }
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .graphicsLayer { translationX = posicao * 20.dp.toPx() }
                .size(27.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

@Composable
fun Girando(tamanho: Dp = 22.dp, cor: Color = Cores.TextoSecundario) {
    val transicao = rememberInfiniteTransition(label = "girando")
    val angulo by transicao.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "angulo")
    Canvas(Modifier.size(tamanho)) {
        val traco = size.minDimension * 0.12f
        drawArc(
            color = cor,
            startAngle = angulo,
            sweepAngle = 280f,
            useCenter = false,
            topLeft = Offset(traco / 2, traco / 2),
            size = Size(size.width - traco, size.height - traco),
            style = Stroke(traco, cap = StrokeCap.Round),
        )
    }
}

@Composable
fun Vazio(icone: String, titulo: String, texto: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Simbolo(icone, tamanho = 34.sp, cor = Cores.TextoTerciario)
        Spacer(Modifier.height(10.dp))
        Texto(titulo, Tipo.tituloCartao, alinhamento = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Texto(texto, Tipo.pequenoSecundario, alinhamento = TextAlign.Center)
    }
}
