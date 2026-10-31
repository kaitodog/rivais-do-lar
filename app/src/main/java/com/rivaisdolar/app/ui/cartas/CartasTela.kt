package com.rivaisdolar.app.ui.cartas

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivaisdolar.app.data.model.Carta
import com.rivaisdolar.app.data.model.TipoCarta
import com.rivaisdolar.app.data.remote.CasaSnapshot
import com.rivaisdolar.app.domain.instanteOuNull
import com.rivaisdolar.app.domain.pt
import com.rivaisdolar.app.domain.relativo
import com.rivaisdolar.app.ui.components.BotaoTexto
import com.rivaisdolar.app.ui.components.CartaoSolido
import com.rivaisdolar.app.ui.components.Divisoria
import com.rivaisdolar.app.ui.components.Grupo
import com.rivaisdolar.app.ui.components.IconeTarefa
import com.rivaisdolar.app.ui.components.Linha
import com.rivaisdolar.app.ui.components.Secao
import com.rivaisdolar.app.ui.components.Simbolo
import com.rivaisdolar.app.ui.components.Texto
import com.rivaisdolar.app.ui.components.toque
import com.rivaisdolar.app.ui.juice.LocalInclinacao
import com.rivaisdolar.app.ui.theme.Cores
import com.rivaisdolar.app.ui.theme.Tipo
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun CasaSnapshot.usouCartaHoje(agora: Instant): Boolean {
    val zona = runCatching { ZoneId.of(casa.fuso) }.getOrDefault(ZoneId.systemDefault())
    val hoje = agora.atZone(zona).toLocalDate()
    return cartas.any { it.dono == meuId && it.usadaEm?.instanteOuNull()?.atZone(zona)?.toLocalDate() == hoje }
}

@Composable
private fun IconeCarta(tipo: TipoCarta?) {
    Box(
        Modifier.size(32.dp).clip(RoundedCornerShape(9.dp))
            .background(if (tipo?.epica == true) Cores.Ouro.copy(alpha = 0.16f) else Cores.Preenchimento),
        contentAlignment = Alignment.Center,
    ) { Simbolo(tipo?.icone ?: "playing_cards", tamanho = 19.sp, cor = if (tipo?.epica == true) Cores.Ouro else Cores.Texto) }
}

/** Mão de cartas: três espaços; épicas com contorno dourado. Também usada no fim de temporada. */
@Composable
fun MaoDeCartas(mao: List<Carta>, aoTocar: ((Carta) -> Unit)? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(TipoCarta.MAO_MAXIMA) { i ->
            val carta = mao.getOrNull(i)
            val tipo = carta?.let { TipoCarta.de(it.tipo) }
            val forma = RoundedCornerShape(16.dp)
            Column(
                Modifier
                    .weight(1f)
                    .height(66.dp)
                    .clip(forma)
                    .then(
                        when {
                            carta == null -> Modifier.border(1.dp, Cores.Separador, forma)
                            tipo?.epica == true -> Modifier.background(Cores.Superficie).border(1.5.dp, Cores.Ouro.copy(alpha = 0.7f), forma)
                            else -> Modifier.background(Cores.Superficie)
                        },
                    )
                    .then(if (carta != null && aoTocar != null) Modifier.toque { aoTocar(carta) } else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Simbolo(tipo?.icone ?: "add", tamanho = 19.sp, cor = when {
                    carta == null -> Cores.TextoTerciario
                    tipo?.epica == true -> Cores.Ouro
                    else -> Cores.Texto
                })
                Texto(tipo?.nome ?: "Vazio", Tipo.selo.copy(fontSize = 11.sp), cor = if (carta == null) Cores.TextoTerciario else Cores.Texto)
            }
        }
    }
}

/** Mão grande da aba Cartas: flip 3D no toque e foil que segue a inclinação. */
@Composable
fun MaoGrande(mao: List<Carta>, aoUsar: ((Carta) -> Unit)?, bloqueio: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(TipoCarta.MAO_MAXIMA) { i ->
            CartaFlip(
                carta = mao.getOrNull(i),
                aoUsar = aoUsar,
                bloqueio = bloqueio,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CartaFlip(
    carta: Carta?,
    aoUsar: ((Carta) -> Unit)?,
    bloqueio: String?,
    modifier: Modifier = Modifier,
) {
    val tipo = carta?.let { TipoCarta.de(it.tipo) }
    val forma = RoundedCornerShape(18.dp)
    if (carta == null || tipo == null) {
        Column(
            modifier.height(156.dp).clip(forma).border(1.dp, Cores.Separador, forma),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Simbolo("add", tamanho = 20.sp, cor = Cores.TextoTerciario)
            Texto("Vazio", Tipo.selo.copy(fontSize = 11.sp), cor = Cores.TextoTerciario, modifier = Modifier.padding(top = 4.dp))
        }
        return
    }

    var virada by rememberSaveable(carta.id) { mutableStateOf(false) }
    val rotacao by animateFloatAsState(if (virada) 180f else 0f, tween(620), label = "carta")
    val densidade = LocalDensity.current.density
    val inclinacao = LocalInclinacao.current
    val cor = when {
        tipo.epica -> Cores.Ouro
        tipo.raridade == "Rara" -> Cores.Azul
        else -> Cores.TextoSecundario
    }

    Box(
        modifier
            .height(156.dp)
            .graphicsLayer {
                rotationY = rotacao
                cameraDistance = 14f * densidade
            }
            .clip(forma)
            // Foil: uma faixa de luz que desliza com a inclinação do aparelho.
            .drawWithCache {
                val dx = inclinacao.valor.x
                val brilho = Brush.linearGradient(
                    0f to Color.Transparent,
                    0.35f to Color.White.copy(alpha = 0.13f),
                    0.55f to Color.Transparent,
                    start = Offset(0f, 0f),
                    end = Offset(size.width * (1.3f + dx * 0.35f), size.height),
                )
                onDrawWithContent {
                    drawContent()
                    drawRect(brilho)
                }
            }
            .toque {
                if (virada) {
                    if (aoUsar != null) aoUsar(carta)
                } else {
                    virada = true
                }
            },
    ) {
        if (rotacao <= 90f) {
            FrenteCarta(tipo, cor)
        } else {
            Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) {
                VersoCarta(tipo, cor, aoUsar != null, bloqueio)
            }
        }
    }
}

@Composable
private fun FrenteCarta(tipo: TipoCarta, cor: Color) {
    val forma = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxSize()
            .then(
                if (tipo.epica) {
                    Modifier.background(Brush.linearGradient(listOf(Color(0xFF332B1A), Color(0xFF1C1913))), forma)
                } else {
                    Modifier.background(Cores.Superficie)
                },
            )
            .border(1.dp, cor.copy(alpha = if (tipo.epica) 0.6f else 0.42f), forma)
            .padding(10.dp),
    ) {
        Texto(tipo.raridade.uppercase(), Tipo.selo.copy(fontSize = 8.sp), cor = cor)
        Box(
            Modifier.padding(top = 8.dp).size(30.dp).clip(RoundedCornerShape(9.dp)).background(cor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Simbolo(tipo.icone, tamanho = 18.sp, cor = cor) }
        Spacer(Modifier.weight(1f))
        Texto(tipo.nome, Tipo.rotulo.copy(fontSize = 12.sp), linhas = 2)
        Texto(
            tipo.efeito,
            Tipo.subtitulo.copy(fontSize = 9.5.sp, lineHeight = 12.sp),
            linhas = 2,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun VersoCarta(tipo: TipoCarta, cor: Color, habilitado: Boolean, bloqueio: String?) {
    val forma = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxSize()
            .background(Cores.SuperficieAlta)
            .border(1.dp, cor.copy(alpha = 0.45f), forma)
            .padding(10.dp),
    ) {
        Texto(tipo.raridade.uppercase(), Tipo.selo.copy(fontSize = 8.sp), cor = cor)
        Texto(tipo.nome, Tipo.rotulo.copy(fontSize = 12.sp), linhas = 2, modifier = Modifier.padding(top = 4.dp))
        Texto(
            tipo.efeito,
            Tipo.subtitulo.copy(fontSize = 9.5.sp, lineHeight = 12.sp),
            linhas = 5,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(CircleShape)
                .background(if (habilitado) cor.copy(alpha = 0.20f) else Cores.Preenchimento),
            contentAlignment = Alignment.Center,
        ) {
            Texto(
                when {
                    habilitado -> "Usar carta"
                    bloqueio != null -> bloqueio
                    else -> "Indisponível"
                },
                Tipo.selo.copy(fontSize = 9.5.sp),
                cor = if (habilitado) cor else Cores.TextoTerciario,
            )
        }
    }
}

/** Preço como botão: cápsula sólida com a moeda. */
@Composable
private fun BotaoComprar(custo: Int, habilitado: Boolean, aoTocar: () -> Unit) {
    Row(
        Modifier
            .height(32.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (habilitado) Cores.SuperficieAlta else Cores.Superficie)
            .border(1.dp, Cores.Separador, androidx.compose.foundation.shape.CircleShape)
            .toque(habilitado = habilitado, aoTocar = aoTocar)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Simbolo("toll", tamanho = 15.sp, cor = if (habilitado) Cores.Ouro else Cores.TextoTerciario, descricao = "Comprar por")
        Texto(custo.toString(), Tipo.numero.copy(fontSize = 14.sp), cor = if (habilitado) Cores.Texto else Cores.TextoTerciario)
    }
}

@Composable
fun CartasTela(
    s: CasaSnapshot,
    agora: Instant,
    lista: LazyListState,
    ocupado: Boolean,
    aoUsar: (Carta) -> Unit,
    aoComprar: (TipoCarta) -> Unit,
    aoArrastao: () -> Unit = {},
    aoNotaFiscal: () -> Unit = {},
) {
    val moedas = s.eu?.moedas ?: 0
    val mao = s.mao()
    val usouHoje = s.usouCartaHoje(agora)
    val usadas = s.cartas.filter { it.usadaEm != null }.sortedByDescending { it.usadaEm }.take(8)
    val zona = runCatching { ZoneId.of(s.casa.fuso) }.getOrDefault(ZoneId.systemDefault())
    val formatoHora = DateTimeFormatter.ofPattern("HH:mm")
    val efeitos = buildList {
        s.jogadores.forEach { j ->
            val meu = j.userId == s.meuId
            if (j.dobradinha) {
                add("library_add" to if (meu) "Sua Dobradinha armada" else "Dobradinha de ${j.nome}")
            }
            j.horaFelizAte?.instanteOuNull()?.takeIf { it.isAfter(agora) }?.let { ate ->
                add(
                    "hourglass_empty" to
                        ((if (meu) "Sua Hora Feliz" else "Hora Feliz de ${j.nome}") + " até " + ate.atZone(zona).format(formatoHora)),
                )
            }
            if (j.cafePassado) add("coffee" to if (meu) "Seu Café Passado armado" else "Café de ${j.nome}")
            if (j.baldeAguaFria) add("water_drop" to if (meu) "Balde na sua próxima tarefa" else "Balde em ${j.nome}")
            j.seguroAte?.instanteOuNull()?.takeIf { it.isAfter(agora) }?.let { ate ->
                add("verified_user" to ((if (meu) "Seu Seguro" else "Seguro de ${j.nome}") + " até " + ate.atZone(zona).format(formatoHora)))
            }
            j.notaFiscalAte?.instanteOuNull()?.takeIf { it.isAfter(agora) }?.let { ate ->
                add("request_quote" to ((if (meu) "Sua Nota Fiscal" else "Nota de ${j.nome}") + " até " + ate.atZone(zona).format(formatoHora)))
            }
        }
    }

    LazyColumn(
        state = lista,
        modifier = Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 50.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Texto("Cartas", Tipo.tituloGrande)
                Texto("Uma por dia · até 3 na mão · 1 moeda a cada 10 pontos", Tipo.pequenoSecundario, Modifier.padding(top = 3.dp))
            }
        }
        item {
            CartaoSolido {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Texto("Suas moedas", Tipo.subtitulo)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Simbolo("toll", tamanho = 26.sp, cor = Cores.Ouro)
                            Texto(" ${moedas.pt()}", Tipo.placar)
                        }
                    }
                    Texto(
                        when {
                            s.casa.modoTregua -> "Trégua: cartas\nguardadas"
                            usouHoje -> "Carta de hoje\njá usada"
                            mao.isEmpty() -> "Compre uma\ncarta na loja"
                            else -> "Pode usar uma\ncarta hoje"
                        },
                        Tipo.pequenoSecundario,
                        alinhamento = androidx.compose.ui.text.style.TextAlign.End,
                    )
                }
            }
        }
        item { Secao("Sua mão", Modifier.padding(top = 4.dp)) }
        item {
            MaoGrande(
                mao = mao,
                aoUsar = if (usouHoje || s.casa.modoTregua) null else aoUsar,
                bloqueio = if (s.casa.modoTregua) "Trégua" else if (usouHoje) "Hoje já usou" else null,
            )
        }
        if (efeitos.isNotEmpty()) {
            item { Secao("Efeitos ativos", Modifier.padding(top = 4.dp)) }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    efeitos.forEach { (icone, texto) ->
                        Row(
                            Modifier
                                .clip(CircleShape)
                                .background(Cores.Superficie)
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Simbolo(icone, tamanho = 15.sp, cor = if (icone == "hourglass_empty") Cores.Ouro else Cores.Texto)
                            Texto(texto, Tipo.pequeno.copy(fontSize = 12.sp))
                        }
                    }
                }
            }
        }
        item { Secao("Decisiva da temporada", Modifier.padding(top = 4.dp)) }
        item {
            CartaoSolido {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconeTarefa("weekend", cor = Cores.Ouro)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Texto("Arrastão do Sofá", Tipo.tituloCartao)
                        Texto("Rouba até 30 pontos · 1 por temporada", Tipo.subtitulo)
                    }
                    BotaoTexto("Usar", aoArrastao, cor = Cores.Ouro, habilitado = !ocupado)
                }
            }
        }
        if (s.eu?.notaFiscalAte?.instanteOuNull()?.isAfter(agora) == true) {
            item { Secao("Nota Fiscal", Modifier.padding(top = 4.dp)) }
            item {
                CartaoSolido {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconeTarefa("request_quote", cor = Cores.Ouro)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Texto("Cobrança de Aluguel", Tipo.tituloCartao)
                            Texto("Cobre 15 pontos de volta antes de expirar", Tipo.subtitulo)
                        }
                        BotaoTexto("Cobrar", aoNotaFiscal, cor = Cores.Ouro, habilitado = !ocupado)
                    }
                }
            }
        }
        item { Secao("Loja", Modifier.padding(top = 4.dp)) }
        item {
            Grupo {
                TipoCarta.entries.filter { it != TipoCarta.Arrastao }.forEachIndexed { i, c ->
                    if (i > 0) Divisoria()
                    val pode = moedas >= c.custo && mao.size < TipoCarta.MAO_MAXIMA && !ocupado
                    Linha(
                        titulo = c.nome,
                        subtitulo = "${c.raridade} · ${c.efeito}",
                        inicio = { IconeCarta(c) },
                        fim = { BotaoComprar(c.custo, pode) { aoComprar(c) } },
                    )
                }
            }
        }
        if (mao.size >= TipoCarta.MAO_MAXIMA) {
            item { Texto("Mão cheia: use uma carta antes de comprar outra.", Tipo.subtitulo, Modifier.padding(horizontal = 16.dp)) }
        }
        if (usadas.isNotEmpty()) {
            item { Secao("Últimas jogadas", Modifier.padding(top = 4.dp)) }
            item {
                Grupo {
                    usadas.forEachIndexed { i, c ->
                        if (i > 0) Divisoria()
                        val tipo = TipoCarta.de(c.tipo)
                        val quem = if (c.dono == s.meuId) "Você" else s.jogador(c.dono)?.nome ?: "Alguém"
                        val alvo = s.tarefas.firstOrNull { it.id == c.alvo }?.titulo
                        Linha(
                            titulo = "$quem usou ${tipo?.nome ?: c.tipo}",
                            subtitulo = listOfNotNull(alvo, c.usadaEm?.instanteOuNull()?.relativo(agora)).joinToString(" · "),
                            inicio = { IconeCarta(tipo) },
                        )
                    }
                }
            }
        }
    }
}
