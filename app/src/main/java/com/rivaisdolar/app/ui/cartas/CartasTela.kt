package com.rivaisdolar.app.ui.cartas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.rivaisdolar.app.ui.components.Linha
import com.rivaisdolar.app.ui.components.Secao
import com.rivaisdolar.app.ui.components.Simbolo
import com.rivaisdolar.app.ui.components.Texto
import com.rivaisdolar.app.ui.components.toque
import com.rivaisdolar.app.ui.theme.Cores
import com.rivaisdolar.app.ui.theme.Tipo
import java.time.Instant
import java.time.ZoneId

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
) {
    val moedas = s.eu?.moedas ?: 0
    val mao = s.mao()
    val usouHoje = s.usouCartaHoje(agora)
    val usadas = s.cartas.filter { it.usadaEm != null }.sortedByDescending { it.usadaEm }.take(8)

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
            MaoDeCartas(mao, aoTocar = if (usouHoje || s.casa.modoTregua) null else aoUsar)
        }
        item { Secao("Loja", Modifier.padding(top = 4.dp)) }
        item {
            Grupo {
                TipoCarta.entries.forEachIndexed { i, c ->
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
