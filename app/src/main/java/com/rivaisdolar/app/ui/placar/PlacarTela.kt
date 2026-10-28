package com.rivaisdolar.app.ui.placar

import android.content.Intent
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivaisdolar.app.data.model.Registro
import com.rivaisdolar.app.data.model.Tarefa
import com.rivaisdolar.app.data.remote.CasaSnapshot
import com.rivaisdolar.app.data.remote.Jogador
import com.rivaisdolar.app.domain.instanteOuNull
import com.rivaisdolar.app.domain.plural
import com.rivaisdolar.app.domain.pt
import com.rivaisdolar.app.domain.relativo
import com.rivaisdolar.app.ui.components.Avatar
import com.rivaisdolar.app.ui.components.BarraDividida
import com.rivaisdolar.app.ui.components.BarraProgresso
import com.rivaisdolar.app.ui.components.BotaoCapsula
import com.rivaisdolar.app.ui.components.BotaoCirculo
import com.rivaisdolar.app.ui.components.CartaoSolido
import com.rivaisdolar.app.ui.components.Divisoria
import com.rivaisdolar.app.ui.components.EstiloBotao
import com.rivaisdolar.app.ui.components.Grupo
import com.rivaisdolar.app.ui.components.IconeTarefa
import com.rivaisdolar.app.ui.components.Linha
import com.rivaisdolar.app.ui.components.Ponto
import com.rivaisdolar.app.ui.components.Secao
import com.rivaisdolar.app.ui.components.Selo
import com.rivaisdolar.app.ui.components.Simbolo
import com.rivaisdolar.app.ui.components.Texto
import com.rivaisdolar.app.ui.components.TituloGrande
import com.rivaisdolar.app.ui.components.Vazio
import com.rivaisdolar.app.ui.components.toque
import com.rivaisdolar.app.ui.principal.bomba
import com.rivaisdolar.app.ui.principal.legenda
import com.rivaisdolar.app.ui.principal.pontosVale
import com.rivaisdolar.app.ui.principal.subtituloTemporada
import com.rivaisdolar.app.ui.principal.tarefasDeHoje
import com.rivaisdolar.app.ui.theme.Cores
import com.rivaisdolar.app.ui.theme.LocalReduzirMovimento
import com.rivaisdolar.app.ui.theme.Tipo
import java.time.Instant

@Composable
fun PlacarTela(
    s: CasaSnapshot,
    agora: Instant,
    offline: Boolean,
    naFila: Int,
    lista: LazyListState,
    aoAbrirTarefa: (Tarefa) -> Unit,
    aoValidar: (Registro) -> Unit,
    aoNotificacoes: () -> Unit,
    aoCasa: () -> Unit,
    aoMeta: () -> Unit,
) {
    val pendentes = s.pendentesParaMim
    val hoje = tarefasDeHoje(s, agora)
    LazyColumn(
        state = lista,
        modifier = Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().height(38.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                BotaoCirculo("notifications", "Pedidos de validação", aoNotificacoes, marcador = if (pendentes.isNotEmpty()) Cores.Alerta else null)
                BotaoCirculo("person", "Casa e perfil", aoCasa)
            }
        }
        item { TituloGrande("Placar", subtituloTemporada(s.temporada, agora)) }
        item { CartaoPlacar(s) }
        if (s.outro == null) item { CartaoConvite(s.casa.codigoConvite) }
        item { CartaoMeta(s, aoMeta) }
        if (s.casa.modoTregua) {
            item {
                Texto(
                    "Modo trégua ligado: a competição está pausada.",
                    Tipo.pequenoSecundario,
                    Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        if (pendentes.isNotEmpty()) {
            item { Secao("Pra você validar", Modifier.padding(top = 4.dp)) }
            item {
                Grupo {
                    pendentes.forEachIndexed { i, r ->
                        if (i > 0) Divisoria()
                        val tarefa = s.tarefas.firstOrNull { it.id == r.tarefaId }
                        Linha(
                            titulo = r.titulo,
                            subtitulo = "${s.jogador(r.userId)?.nome ?: "Alguém"} concluiu ${r.criadoEm.instanteOuNull()?.relativo(agora) ?: ""}",
                            inicio = { IconeTarefa(tarefa?.icone ?: "task_alt") },
                            fim = { Texto("+${r.pontosFinais}", Tipo.numero) },
                            seta = true,
                            aoTocar = { aoValidar(r) },
                        )
                    }
                }
            }
        }
        item { Secao("Hoje", Modifier.padding(top = 4.dp)) }
        item {
            if (hoje.isEmpty()) {
                CartaoSolido { Vazio("check_circle", "Nada pendente pra hoje", "Toque em + para cadastrar tarefas ou veja todas na aba Tarefas.") }
            } else {
                Grupo {
                    hoje.forEachIndexed { i, t ->
                        if (i > 0) Divisoria()
                        LinhaTarefa(t, s, agora, doPlacar = true, aoTocar = { aoAbrirTarefa(t) })
                    }
                }
            }
        }
        if (offline || naFila > 0) {
            item {
                Texto(
                    buildString {
                        if (offline) append("Sem conexão · mostrando os dados salvos no aparelho")
                        if (naFila > 0) {
                            if (isNotEmpty()) append("\n")
                            append(plural(naFila, "ação esperando", "ações esperando") + " a internet voltar")
                        }
                    },
                    Tipo.subtitulo,
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    alinhamento = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

/** Linha de tarefa usada no placar e na aba Tarefas. */
@Composable
fun LinhaTarefa(t: Tarefa, s: CasaSnapshot, agora: Instant, doPlacar: Boolean, aoTocar: () -> Unit) {
    val bomba = t.bomba(agora) && t.status != "aguardando"
    val leg = legenda(t, s, agora, doPlacar)
    val dono = s.jogador(t.reservadaPor)
    Linha(
        titulo = t.titulo,
        subtitulo = leg.texto,
        corSubtitulo = leg.cor,
        inicio = { IconeTarefa(t.icone, bomba = bomba) },
        depoisDoTitulo = if (dono != null && t.status != "livre") ({ Ponto(Cores.jogador(dono.cor)) }) else null,
        fim = { Texto(t.pontosVale().pt(), Tipo.numero, cor = if (bomba) Cores.Alerta else Cores.Texto) },
        seta = !doPlacar,
        aoTocar = aoTocar,
    )
}

@Composable
private fun CartaoPlacar(s: CasaSnapshot) {
    val a = s.jogadores.getOrNull(0)
    val b = s.jogadores.getOrNull(1)
    val pa = s.pontos(a?.userId)
    val pb = s.pontos(b?.userId)
    val lider = when {
        b == null || pa == pb -> null
        pa > pb -> a
        else -> b
    }
    CartaoSolido {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LadoPlacar(a, pa, lider != null && lider == a, Modifier.weight(1f), direita = false)
                Texto("vs", Tipo.rotulo.copy(fontSize = 12.sp), cor = Cores.TextoTerciario)
                LadoPlacar(b, pb, lider != null && lider == b, Modifier.weight(1f), direita = true)
            }
            BarraDividida(pa, pb, Cores.jogador(a?.cor), if (b == null) Cores.Preenchimento else Cores.jogador(b.cor))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val eu = s.eu
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (eu != null && eu.sequenciaDias > 0) {
                        Simbolo("local_fire_department", tamanho = 14.sp, cor = Cores.TextoSecundario)
                        Texto("Sequência de ${plural(eu.sequenciaDias, "dia", "dias")}", Tipo.subtitulo)
                    } else if (eu != null) {
                        Texto("Nível ${eu.nivel} · ${eu.moedas.pt()} moedas", Tipo.subtitulo)
                    }
                }
                val diferenca = kotlin.math.abs(pa - pb)
                Texto(
                    when {
                        b == null -> "Esperando rival"
                        diferenca == 0 -> "Empatados"
                        else -> "+${diferenca.pt()} pts"
                    },
                    Tipo.numeroPequeno,
                )
            }
        }
    }
}

@Composable
private fun LadoPlacar(j: Jogador?, pontos: Int, naFrente: Boolean, modifier: Modifier, direita: Boolean) {
    val reduzir = LocalReduzirMovimento.current
    // Pontos sobem rolando quando um registro é validado.
    val animado by animateIntAsState(pontos, if (reduzir) snap() else tween(1100), label = "pontos")
    val cor = if (j == null) Cores.SuperficieAlta else Cores.jogador(j.cor)
    Column(modifier, horizontalAlignment = if (direita) Alignment.End else Alignment.Start, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            if (direita) {
                Texto(j?.nome ?: "Convidado", Tipo.rotulo, cor = if (j == null) Cores.TextoTerciario else Cores.Texto, linhas = 1)
                Avatar(j?.nome ?: "?", cor)
            } else {
                Avatar(j?.nome ?: "?", cor)
                Texto(j?.nome ?: "Convidado", Tipo.rotulo, linhas = 1)
            }
        }
        Texto(animado.pt(), Tipo.placar, cor = if (j == null) Cores.TextoTerciario else Cores.Texto)
        Box(Modifier.height(18.dp)) {
            if (naFrente && j != null) Selo("Na frente", Cores.jogador(j.cor))
        }
    }
}

@Composable
private fun CartaoMeta(s: CasaSnapshot, aoTocar: () -> Unit) {
    val t = s.temporada
    val atual = t?.metaAtual ?: 0
    val alvo = t?.metaAlvo ?: 2000
    CartaoSolido(Modifier.toque(aoTocar = aoTocar)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Texto(
                    if (atual >= alvo) "Meta da casa batida" else "Meta da casa · ${t?.metaPremio ?: "toque pra definir o prêmio"}",
                    Tipo.tituloCartao,
                    Modifier.weight(1f),
                    linhas = 1,
                )
                Texto("${atual.pt()} / ${alvo.pt()}", Tipo.numeroPequeno)
            }
            BarraProgresso(atual.toFloat() / alvo.coerceAtLeast(1))
        }
    }
}

@Composable
private fun CartaoConvite(codigo: String) {
    val context = LocalContext.current
    CartaoSolido {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Texto("Chame quem mora com você", Tipo.tituloCartao)
            Texto("No outro celular, crie uma conta e entre com este código:", Tipo.pequenoSecundario)
            Texto(codigo.chunked(3).joinToString(" "), Tipo.placar.copy(letterSpacing = 4.sp))
            BotaoCapsula(
                "Compartilhar código",
                aoTocar = {
                    val envio = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, "Bora competir na faxina? Baixe o Rivais do Lar e entre na minha casa com o código $codigo")
                    context.startActivity(Intent.createChooser(envio, "Compartilhar código"))
                },
                estilo = EstiloBotao.Solido,
                icone = "ios_share",
            )
        }
    }
}

