package com.rivaisdolar.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.rivaisdolar.app.domain.pt
import com.rivaisdolar.app.ui.auth.AuthFluxo
import com.rivaisdolar.app.ui.auth.FalhaTela
import com.rivaisdolar.app.ui.auth.SemCasaTela
import com.rivaisdolar.app.ui.auth.ServidorTela
import com.rivaisdolar.app.ui.casa.CasaTela
import com.rivaisdolar.app.ui.components.Aviso
import com.rivaisdolar.app.ui.components.BarraProgresso
import com.rivaisdolar.app.ui.components.BotaoCapsula
import com.rivaisdolar.app.ui.components.BotaoTexto
import com.rivaisdolar.app.ui.components.CabecalhoFolha
import com.rivaisdolar.app.ui.components.FolhaVidro
import com.rivaisdolar.app.ui.components.Girando
import com.rivaisdolar.app.ui.components.Texto
import com.rivaisdolar.app.ui.principal.JogoViewModel
import com.rivaisdolar.app.ui.principal.PrincipalTela
import com.rivaisdolar.app.ui.tarefas.EditorTarefaTela
import com.rivaisdolar.app.ui.temporada.FimTemporadaTela
import com.rivaisdolar.app.ui.theme.Cores
import com.rivaisdolar.app.ui.theme.RivaisTema
import com.rivaisdolar.app.ui.theme.Tipo
import kotlinx.serialization.Serializable

@Serializable private object RotaPrincipal
@Serializable private object RotaCasa
@Serializable private data class RotaEditor(val id: String? = null)
@Serializable private data class RotaFimTemporada(val id: String)

@Composable
fun RivaisRaiz(vm: RaizViewModel = hiltViewModel()) {
    val raiz by vm.raiz.collectAsStateWithLifecycle()
    val cor by vm.cor.collectAsStateWithLifecycle()
    val oferta by vm.oferta.collectAsStateWithLifecycle()
    val progresso by vm.progresso.collectAsStateWithLifecycle()
    val aviso by vm.aviso.collectAsStateWithLifecycle()

    RivaisTema(destaque = Cores.jogador(cor)) {
        Box(Modifier.fillMaxSize().background(Cores.Fundo)) {
            AnimatedContent(
                targetState = raiz,
                contentKey = { it::class },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "raiz",
            ) { estado ->
                when (estado) {
                    Raiz.Carregando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Girando() }
                    Raiz.SemServidor -> ServidorTela()
                    Raiz.Deslogado -> AuthFluxo()
                    Raiz.SemCasa -> SemCasaTela()
                    is Raiz.Falha -> FalhaTela(estado.mensagem, vm::tentarDeNovo, vm::sair)
                    Raiz.Pronto -> Jogo(aoProcurarAtualizacao = { vm.procurarAtualizacao(manual = true) })
                }
            }

            FolhaVidro(oferta, { vm.ignorarAtualizacao() }) { nova ->
                CabecalhoFolha("Versão ${nova.versao} disponível", vm::ignorarAtualizacao)
                if (nova.notas.isNotBlank()) Texto(nova.notas.take(800), Tipo.pequenoSecundario)
                val p = progresso
                if (p != null) {
                    BarraProgresso(p)
                    Texto("Baixando… ${(p * 100).toInt().pt()}%", Tipo.subtitulo, Modifier.fillMaxWidth(), alinhamento = TextAlign.Center)
                } else {
                    BotaoCapsula("Baixar e instalar", vm::instalarAtualizacao, icone = "download")
                    BotaoTexto("Agora não", vm::ignorarAtualizacao, Modifier.align(Alignment.CenterHorizontally), cor = Cores.TextoSecundario)
                }
            }
            Aviso(aviso, vm::limparAviso, Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun Jogo(aoProcurarAtualizacao: () -> Unit) {
    val nav = rememberNavController()
    val jogo: JogoViewModel = hiltViewModel()
    NavHost(
        nav,
        startDestination = RotaPrincipal,
        enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
    ) {
        composable<RotaPrincipal> {
            PrincipalTela(
                vm = jogo,
                aoCasa = { nav.navigate(RotaCasa) },
                aoNovaTarefa = { nav.navigate(RotaEditor()) },
                aoEditarTarefa = { nav.navigate(RotaEditor(it)) },
                aoFimTemporada = { nav.navigate(RotaFimTemporada(it)) },
            )
        }
        composable<RotaCasa> {
            CasaTela(
                vm = jogo,
                aoVoltar = { nav.popBackStack() },
                aoEditarTarefa = { nav.navigate(RotaEditor(it)) },
                aoUltimaTemporada = { nav.navigate(RotaFimTemporada(it)) },
                aoProcurarAtualizacao = aoProcurarAtualizacao,
            )
        }
        composable<RotaEditor> { entrada ->
            EditorTarefaTela(jogo, entrada.toRoute<RotaEditor>().id) { nav.popBackStack() }
        }
        composable<RotaFimTemporada> { entrada ->
            FimTemporadaTela(jogo, entrada.toRoute<RotaFimTemporada>().id) { nav.popBackStack() }
        }
    }
}
