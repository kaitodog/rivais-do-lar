package com.rivaisdolar.app.ui.historico

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rivaisdolar.app.data.model.Conquistas
import com.rivaisdolar.app.data.model.Registro
import com.rivaisdolar.app.data.model.Temporada
import com.rivaisdolar.app.data.remote.CasaSnapshot
import com.rivaisdolar.app.data.remote.Historico
import com.rivaisdolar.app.domain.instanteOuNull
import com.rivaisdolar.app.domain.pt
import com.rivaisdolar.app.ui.components.BotaoCirculo
import com.rivaisdolar.app.ui.components.BotaoTexto
import com.rivaisdolar.app.ui.components.CartaoSolido
import com.rivaisdolar.app.ui.components.Girando
import com.rivaisdolar.app.ui.components.Ponto
import com.rivaisdolar.app.ui.components.Secao
import com.rivaisdolar.app.ui.components.Simbolo
import com.rivaisdolar.app.ui.components.Texto
import com.rivaisdolar.app.ui.principal.JogoViewModel
import com.rivaisdolar.app.ui.theme.Cores
import com.rivaisdolar.app.ui.theme.Tipo
import java.time.DayOfWeek
import java.time.ZoneId

@Composable
fun HistoricoTela(vm: JogoViewModel, aoVoltar: () -> Unit) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val s = snapshot ?: return
    val historico by produceState(initialValue = null as Historico?, s.temporada?.id) {
        value = runCatching { vm.historico() }.getOrDefault(Historico(emptyList(), emptyList()))
    }
    HistoricoConteudo(s, historico, aoVoltar)
}

private val diasDaSemana = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")

/** Pontos acumulados por dia (segunda a hoje) de cada jogador. */
fun acumuladoDaSemana(s: CasaSnapshot, registros: List<Registro>, agora: java.time.Instant = java.time.Instant.now()): List<List<Int>> {
    val zona = runCatching { ZoneId.of(s.casa.fuso) }.getOrDefault(ZoneId.systemDefault())
    val inicio = s.temporada?.inicio?.instanteOuNull()?.atZone(zona)?.toLocalDate() ?: return s.jogadores.map { emptyList() }
    val hoje = agora.atZone(zona).toLocalDate()
    val ultimo = (hoje.toEpochDay() - inicio.toEpochDay()).toInt().coerceIn(0, 6)
    return s.jogadores.map { j ->
        val porDia = IntArray(7)
        registros.filter { it.userId == j.userId }.forEach { r ->
            val d = r.criadoEm.instanteOuNull()?.atZone(zona)?.toLocalDate() ?: return@forEach
            val i = (d.toEpochDay() - inicio.toEpochDay()).toInt()
            if (i in 0..6) porDia[i] += r.pontosFinais
        }
        porDia.toList().take(ultimo + 1).runningReduce(Int::plus)
    }
}

@Composable
fun HistoricoConteudo(s: CasaSnapshot, historico: Historico?, aoVoltar: () -> Unit, agora: java.time.Instant = java.time.Instant.now()) {
    var temporadaSelecionada by remember { mutableStateOf<Int?>(null) }
    var diaSelecionado by remember { mutableStateOf<Int?>(null) }
    var tabelaTemporadas by remember { mutableStateOf(false) }
    var tabelaSemana by remember { mutableStateOf(false) }
    val series = s.jogadores.map { Serie(it.nome, corDeGrafico(it.cor)) }

    Box(Modifier.fillMaxSize().background(Cores.Fundo)) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Row(Modifier.fillMaxWidth().height(38.dp)) { BotaoCirculo("arrow_back", "Voltar", aoVoltar) } }
            item { Texto("Histórico", Tipo.tituloGrande) }

            if (historico == null) {
                item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { Girando() } }
                return@LazyColumn
            }

            // Vitórias: número grande, sem gráfico.
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    s.jogadores.forEach { j ->
                        val vitorias = historico.temporadas.count { it.vencedor == j.userId }
                        CartaoSolido(Modifier.weight(1f)) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Ponto(Cores.jogador(j.cor))
                                    Texto(j.nome, Tipo.rotulo, linhas = 1)
                                }
                                Texto(vitorias.pt(), Tipo.placar)
                                Texto(if (vitorias == 1) "temporada vencida" else "temporadas vencidas", Tipo.subtitulo)
                            }
                        }
                    }
                }
            }

            item { Secao("Pontos por temporada", Modifier.padding(top = 4.dp)) }
            item {
                val temporadas: List<Temporada> = (historico.temporadas.takeLast(7) + listOfNotNull(s.temporada)).distinctBy { it.id }
                val grupos = temporadas.map { t ->
                    GrupoColunas(if (t.encerrada) "T${t.numero}" else "T${t.numero} agora", s.jogadores.map { t.placar[it.userId] ?: 0 })
                }
                CartaoSolido {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Legenda(series)
                        val sel = temporadaSelecionada?.let { grupos.getOrNull(it) }
                        Texto(
                            sel?.let { g -> g.rotulo + " · " + s.jogadores.mapIndexed { i, j -> "${j.nome} ${g.valores[i].pt()}" }.joinToString(" · ") }
                                ?: "Toque numa temporada para ver os pontos",
                            Tipo.subtitulo.copy(color = if (sel != null) Cores.Texto else Cores.TextoSecundario),
                        )
                        if (tabelaTemporadas) {
                            TabelaDados(listOf("Temporada") + s.jogadores.map { it.nome }, grupos.map { g -> listOf(g.rotulo) + g.valores.map { it.pt() } })
                        } else {
                            ColunasAgrupadas(grupos, series, temporadaSelecionada, { temporadaSelecionada = it })
                        }
                        BotaoTexto(if (tabelaTemporadas) "Ver gráfico" else "Ver em tabela", { tabelaTemporadas = !tabelaTemporadas }, cor = Cores.TextoSecundario)
                    }
                }
            }

            item { Secao("Esta semana, dia a dia", Modifier.padding(top = 4.dp)) }
            item {
                val valores = acumuladoDaSemana(s, historico.registrosDaSemana, agora)
                CartaoSolido {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Legenda(series)
                        val d = diaSelecionado
                        Texto(
                            if (d != null) {
                                diasDaSemana[d] + " · " + s.jogadores.mapIndexed { i, j -> "${j.nome} ${(valores.getOrNull(i)?.getOrNull(d) ?: 0).pt()}" }.joinToString(" · ")
                            } else {
                                "Acumulado até agora · " + s.jogadores.mapIndexed { i, j -> "${j.nome} ${(valores.getOrNull(i)?.lastOrNull() ?: 0).pt()}" }.joinToString(" · ")
                            },
                            Tipo.subtitulo.copy(color = Cores.Texto),
                        )
                        if (tabelaSemana) {
                            val n = valores.maxOfOrNull { it.size } ?: 0
                            TabelaDados(listOf("Dia") + s.jogadores.map { it.nome }, (0 until n).map { i -> listOf(diasDaSemana[i]) + valores.map { (it.getOrNull(i) ?: 0).pt() } })
                        } else {
                            LinhasAcumuladas(diasDaSemana, valores, series, diaSelecionado, { diaSelecionado = it })
                        }
                        BotaoTexto(if (tabelaSemana) "Ver gráfico" else "Ver em tabela", { tabelaSemana = !tabelaSemana }, cor = Cores.TextoSecundario)
                    }
                }
            }

            item { Secao("Conquistas", Modifier.padding(top = 4.dp)) }
            Conquistas.todas.chunked(2).forEach { par ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        par.forEach { c ->
                            val donos = s.conquistas.filter { it.codigo == c.codigo }.mapNotNull { k -> s.jogador(k.userId) }
                            val obtida = donos.isNotEmpty()
                            CartaoSolido(Modifier.weight(1f)) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier.size(32.dp).clip(RoundedCornerShape(9.dp))
                                                .background(if (obtida) Cores.Ouro.copy(alpha = 0.16f) else Cores.Preenchimento),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Simbolo(if (obtida) c.icone else "lock", tamanho = 18.sp, cor = if (obtida) Cores.Ouro else Cores.TextoTerciario)
                                        }
                                        Box(Modifier.weight(1f))
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { donos.forEach { Ponto(Cores.jogador(it.cor)) } }
                                    }
                                    Texto(c.nome, Tipo.tituloCartao, cor = if (obtida) Cores.Texto else Cores.TextoSecundario, linhas = 1)
                                    Texto(c.descricao, Tipo.subtitulo, linhas = 2)
                                }
                            }
                        }
                        if (par.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

