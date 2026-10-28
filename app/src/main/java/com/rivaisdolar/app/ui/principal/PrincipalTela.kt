package com.rivaisdolar.app.ui.principal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rivaisdolar.app.ui.cartas.CartasTela
import com.rivaisdolar.app.ui.components.Aba
import com.rivaisdolar.app.ui.components.Aviso
import com.rivaisdolar.app.ui.components.BarraAbas
import com.rivaisdolar.app.ui.components.BordaInferior
import com.rivaisdolar.app.ui.components.FolhaVidro
import com.rivaisdolar.app.ui.components.Girando
import com.rivaisdolar.app.ui.components.LocalHaze
import com.rivaisdolar.app.ui.components.Texto
import com.rivaisdolar.app.ui.placar.PlacarTela
import com.rivaisdolar.app.ui.tarefas.FolhaAtividade
import com.rivaisdolar.app.ui.tarefas.FolhaConcluir
import com.rivaisdolar.app.ui.tarefas.FolhaDetalhe
import com.rivaisdolar.app.ui.tarefas.FolhaFiltro
import com.rivaisdolar.app.ui.tarefas.FolhaMeta
import com.rivaisdolar.app.ui.tarefas.FolhaValidar
import com.rivaisdolar.app.ui.tarefas.TarefasTela
import com.rivaisdolar.app.ui.theme.Cores
import com.rivaisdolar.app.ui.theme.Tipo
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

private sealed interface Folha {
    data class Detalhe(val tarefaId: String) : Folha
    data class Concluir(val tarefaId: String) : Folha
    data class Validar(val registroId: String) : Folha
    data object Atividade : Folha
    data object Meta : Folha
    data object Filtro : Folha
}

@Composable
fun PrincipalTela(
    vm: JogoViewModel,
    aoCasa: () -> Unit,
    aoNovaTarefa: () -> Unit,
    aoEditarTarefa: (String) -> Unit,
    aoFimTemporada: (String) -> Unit,
) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val offline by vm.offline.collectAsStateWithLifecycle()
    val naFila by vm.naFila.collectAsStateWithLifecycle()
    val ocupado by vm.ocupado.collectAsStateWithLifecycle()
    val aviso by vm.aviso.collectAsStateWithLifecycle()
    val agora by rememberAgora()

    var aba by rememberSaveable { mutableStateOf(Aba.Placar) }
    var folha by remember { mutableStateOf<Folha?>(null) }
    var categoria by rememberSaveable { mutableStateOf<String?>(null) }
    var recolhida by remember { mutableStateOf(false) }
    val listas = Aba.entries.associateWith { rememberLazyListState() }

    // A barra de abas encolhe ao rolar para baixo e volta ao rolar para cima.
    val rolagem = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -4f) recolhida = true else if (available.y > 4f) recolhida = false
                return Offset.Zero
            }
        }
    }
    val haze = rememberHazeState()

    LaunchedEffect(snapshot?.ultimaEncerrada?.id) {
        vm.temporadaNaoVista()?.let { aoFimTemporada(it.id) }
    }

    val s = snapshot
    if (s == null) {
        Box(Modifier.fillMaxSize().background(Cores.Fundo), contentAlignment = Alignment.Center) { Girando() }
        return
    }

    Box(Modifier.fillMaxSize().background(Cores.Fundo)) {
        Box(Modifier.fillMaxSize().hazeSource(haze).nestedScroll(rolagem)) {
            when (aba) {
                Aba.Placar -> PlacarTela(
                    s = s,
                    agora = agora,
                    offline = offline,
                    naFila = naFila,
                    lista = listas.getValue(Aba.Placar),
                    aoAbrirTarefa = { t ->
                        val pendente = s.registroPendenteDa(t.id)
                        folha = if (pendente != null && pendente.userId != s.meuId) Folha.Validar(pendente.id) else Folha.Detalhe(t.id)
                    },
                    aoValidar = { folha = Folha.Validar(it.id) },
                    aoNotificacoes = { folha = Folha.Atividade },
                    aoCasa = aoCasa,
                    aoMeta = { folha = Folha.Meta },
                )
                Aba.Tarefas -> TarefasTela(
                    s = s,
                    agora = agora,
                    lista = listas.getValue(Aba.Tarefas),
                    categoria = categoria,
                    aoFiltrar = { folha = Folha.Filtro },
                    aoAbrirTarefa = { t ->
                        val pendente = s.registroPendenteDa(t.id)
                        folha = if (pendente != null && pendente.userId != s.meuId) Folha.Validar(pendente.id) else Folha.Detalhe(t.id)
                    },
                )
                Aba.Cartas -> CartasTela(s, listas.getValue(Aba.Cartas))
            }
        }

        BordaInferior(Modifier.align(Alignment.BottomCenter))

        CompositionLocalProvider(LocalHaze provides haze) {
            BarraAbas(
                aba = aba,
                aoEscolher = {
                    aba = it
                    recolhida = false
                },
                recolhida = recolhida,
                aoAdicionar = aoNovaTarefa,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
            )

            FolhaVidro(folha, aoFechar = { folha = null }) { f ->
                val fechar = { folha = null }
                when (f) {
                    is Folha.Detalhe -> {
                        val t = s.tarefas.firstOrNull { it.id == f.tarefaId }
                        if (t == null) {
                            Texto("Essa tarefa não existe mais.", Tipo.pequenoSecundario)
                        } else {
                            FolhaDetalhe(
                                t, s, agora, vm, ocupado,
                                aoFechar = fechar,
                                aoConcluir = { folha = Folha.Concluir(t.id) },
                                aoValidar = { folha = Folha.Validar(it.id) },
                                aoEditar = {
                                    folha = null
                                    aoEditarTarefa(t.id)
                                },
                            )
                        }
                    }
                    is Folha.Concluir -> {
                        val t = s.tarefas.firstOrNull { it.id == f.tarefaId }
                        if (t == null) Texto("Essa tarefa não existe mais.", Tipo.pequenoSecundario)
                        else FolhaConcluir(t, s, vm, ocupado, fechar)
                    }
                    is Folha.Validar -> {
                        val r = s.registros.firstOrNull { it.id == f.registroId }
                        if (r == null || r.status != "pendente") {
                            Texto("Esse registro já foi resolvido.", Tipo.pequenoSecundario)
                        } else {
                            FolhaValidar(r, s, agora, vm, ocupado, fechar)
                        }
                    }
                    Folha.Atividade -> FolhaAtividade(s, agora, fechar) { folha = Folha.Validar(it.id) }
                    Folha.Meta -> FolhaMeta(s, vm, ocupado, fechar)
                    Folha.Filtro -> FolhaFiltro(s, categoria, {
                        categoria = it
                        folha = null
                    }, fechar)
                }
            }
        }

        Aviso(aviso, vm::limparAviso, Modifier.align(Alignment.TopCenter))
    }
}
