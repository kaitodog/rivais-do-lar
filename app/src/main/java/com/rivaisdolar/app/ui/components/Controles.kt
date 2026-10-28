package com.rivaisdolar.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivaisdolar.app.ui.theme.Cores
import com.rivaisdolar.app.ui.theme.LocalDestaque
import com.rivaisdolar.app.ui.theme.Tipo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Campo de texto sólido (fica no conteúdo). [sublinhado] é a versão plana usada dentro de sheets. */
@Composable
fun CampoTexto(
    valor: String,
    aoMudar: (String) -> Unit,
    dica: String,
    modifier: Modifier = Modifier,
    teclado: KeyboardType = KeyboardType.Text,
    senha: Boolean = false,
    sublinhado: Boolean = false,
    linhas: Int = 1,
    habilitado: Boolean = true,
) {
    var mostrar by remember { mutableStateOf(false) }
    val destaque = LocalDestaque.current
    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (sublinhado) Modifier else Modifier.clip(FormaCampo).background(Cores.Superficie),
            )
            .heightIn(min = 50.dp)
            .padding(horizontal = if (sublinhado) 2.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
            if (valor.isEmpty()) Texto(dica, Tipo.corpo, cor = Cores.TextoTerciario, linhas = 1)
            BasicTextField(
                value = valor,
                onValueChange = aoMudar,
                enabled = habilitado,
                singleLine = linhas == 1,
                maxLines = linhas,
                textStyle = Tipo.corpo,
                cursorBrush = SolidColor(destaque),
                keyboardOptions = KeyboardOptions(keyboardType = if (senha) KeyboardType.Password else teclado),
                visualTransformation = if (senha && !mostrar) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (senha) {
            Simbolo(
                if (mostrar) "visibility_off" else "visibility",
                Modifier.padding(start = 8.dp).toque { mostrar = !mostrar },
                tamanho = 20.sp,
                cor = Cores.TextoSecundario,
                descricao = if (mostrar) "Esconder senha" else "Mostrar senha",
            )
        }
    }
    if (sublinhado) Box(Modifier.fillMaxWidth().height(0.5.dp).background(Cores.Separador))
}

/** Slider no estilo do protótipo: trilha fina e polegar em cápsula branca. */
@Composable
fun Deslizador(
    valor: Float,
    faixa: ClosedFloatingPointRange<Float>,
    passo: Float,
    aoMudar: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val destaque = LocalDestaque.current
    var pressionado by remember { mutableStateOf(false) }
    val escala by animateFloatAsState(if (pressionado) 1.2f else 1f, label = "polegar")
    BoxWithConstraints(modifier.fillMaxWidth().height(32.dp)) {
        val largura = constraints.maxWidth.toFloat()
        val polegarPx = with(LocalDensity.current) { 38.dp.toPx() }
        val util = (largura - polegarPx).coerceAtLeast(1f)
        val fracao = ((valor - faixa.start) / (faixa.endInclusive - faixa.start)).coerceIn(0f, 1f)

        fun emValor(x: Float): Float {
            val f = ((x - polegarPx / 2) / util).coerceIn(0f, 1f)
            val bruto = faixa.start + f * (faixa.endInclusive - faixa.start)
            return (Math.round((bruto - faixa.start) / passo) * passo + faixa.start).coerceIn(faixa.start, faixa.endInclusive)
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(faixa, passo) {
                    detectTapGestures(onPress = {
                        pressionado = true
                        aoMudar(emValor(it.x))
                        tryAwaitRelease()
                        pressionado = false
                    })
                }
                .pointerInput(faixa, passo) {
                    detectHorizontalDragGestures(
                        onDragStart = { pressionado = true },
                        onDragEnd = { pressionado = false },
                        onDragCancel = { pressionado = false },
                    ) { change, _ -> aoMudar(emValor(change.position.x)) }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.12f)))
            Box(
                Modifier
                    .width(with(LocalDensity.current) { (polegarPx / 2 + fracao * util).toDp() })
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(destaque),
            )
            Box(
                Modifier
                    .offset { IntOffset((fracao * util).roundToInt(), 0) }
                    .graphicsLayer {
                        scaleX = escala
                        scaleY = escala
                    }
                    .width(38.dp)
                    .height(24.dp)
                    .clip(CircleShape)
                    .background(if (pressionado) Color.White.copy(alpha = 0.75f) else Color.White),
            )
        }
    }
}

/**
 * Bottom sheet de vidro: fundo escurecido, margem de 7dp, raio de 38dp concêntrico com a tela,
 * puxador e arrasto para fechar. Guarda o último item para animar a saída.
 */
@Composable
fun <T : Any> FolhaVidro(
    item: T?,
    aoFechar: () -> Unit,
    conteudo: @Composable ColumnScope.(T) -> Unit,
) {
    var ultimo by remember { mutableStateOf(item) }
    if (item != null) ultimo = item
    val visivel = item != null
    val escurecer by animateFloatAsState(if (visivel) 1f else 0f, label = "escurecer")

    if (escurecer > 0.01f) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f * escurecer))
                .pointerInput(Unit) { detectTapGestures { aoFechar() } },
        )
    }
    BackHandler(enabled = visivel, onBack = aoFechar)

    AnimatedVisibility(
        visible = visivel,
        enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 420f)) { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
        val arraste = remember { Animatable(0f) }
        val escopo = rememberCoroutineScope()
        Box(Modifier.fillMaxSize().statusBarsPadding().imePadding(), contentAlignment = Alignment.BottomCenter) {
            GlassSurface(
                Modifier
                    .padding(7.dp)
                    .offset { IntOffset(0, arraste.value.roundToInt()) }
                    .fillMaxWidth()
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { d -> escopo.launch { arraste.snapTo((arraste.value + d).coerceAtLeast(0f)) } },
                        onDragStopped = { v ->
                            if (arraste.value > 220f || v > 2400f) aoFechar() else arraste.animateTo(0f)
                        },
                    )
                    .pointerInput(Unit) { detectTapGestures { } },
                shape = RoundedCornerShape(38.dp),
                fundo = Cores.VidroFolha,
                alinhamento = Alignment.TopCenter,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(5.dp).clip(CircleShape)
                            .background(Cores.TextoTerciario),
                    )
                    ultimo?.let { conteudo(it) }
                }
            }
        }
    }
}

/** Cabeçalho padrão do sheet: fechar à esquerda, título no centro. */
@Composable
fun CabecalhoFolha(titulo: String, aoFechar: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(38.dp)) { BotaoFechar(aoFechar) }
        Texto(titulo, Tipo.tituloFolha, Modifier.weight(1f), alinhamento = androidx.compose.ui.text.style.TextAlign.Center, linhas = 2)
        Spacer(Modifier.width(38.dp))
    }
}

enum class Aba(val rotulo: String, val icone: String) {
    Placar("Placar", "trophy"),
    Tarefas("Tarefas", "checklist"),
    Cartas("Cartas", "playing_cards"),
}

/**
 * Barra de abas flutuante em cápsula de vidro, com o "+" tingido ao lado. Encolhe (só ícones) ao
 * rolar para baixo.
 */
@Composable
fun BarraAbas(
    aba: Aba,
    aoEscolher: (Aba) -> Unit,
    recolhida: Boolean,
    aoAdicionar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val destaque = LocalDestaque.current
    val altura by animateDpAsState(if (recolhida) 50.dp else 58.dp, label = "altura")
    val largura by animateFloatAsState(if (recolhida) 0.66f else 1f, label = "largura")
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            GlassSurface(Modifier.fillMaxWidth(largura).height(altura), CircleShape) {
                Row(Modifier.fillMaxSize().padding(4.dp)) {
                    Aba.entries.forEach { item ->
                        val ativa = item == aba
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(if (ativa) Color.White.copy(alpha = 0.10f) else Color.Transparent)
                                .toque(papel = Role.Tab) { aoEscolher(item) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Simbolo(item.icone, tamanho = 22.sp, cor = if (ativa) destaque else Cores.Texto, descricao = item.rotulo)
                            AnimatedVisibility(!recolhida, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                                Texto(item.rotulo, Tipo.aba, Modifier.padding(top = 2.dp), cor = if (ativa) destaque else Cores.Texto)
                            }
                        }
                    }
                }
            }
        }
        GlassSurface(Modifier.size(altura).toque(aoTocar = aoAdicionar), CircleShape, tinta = destaque) {
            Simbolo("add", tamanho = 26.sp, descricao = "Nova tarefa")
        }
    }
}

/** Degradê preto na borda inferior: a lista some suavemente por baixo da barra. */
@Composable
fun BordaInferior(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(130.dp)
            .background(Brush.verticalGradient(0f to Color.Transparent, 0.7f to Color.Black.copy(alpha = 0.8f), 1f to Color.Black.copy(alpha = 0.9f))),
    )
}

/** Aviso curto no topo (erros e confirmações). Sólido, some sozinho. */
@Composable
fun Aviso(texto: String?, aoSumir: () -> Unit, modifier: Modifier = Modifier) {
    var ultimo by remember { mutableStateOf(texto) }
    if (texto != null) ultimo = texto
    LaunchedEffect(texto) {
        if (texto != null) {
            delay(3800)
            aoSumir()
        }
    }
    AnimatedVisibility(
        visible = texto != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Texto(
                ultimo.orEmpty(),
                Tipo.pequeno.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Cores.SuperficieAlta)
                    .pointerInput(Unit) { detectTapGestures { aoSumir() } }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                alinhamento = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

