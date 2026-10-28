package com.rivaisdolar.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.rivaisdolar.app.data.model.Carta
import com.rivaisdolar.app.data.model.Casa
import com.rivaisdolar.app.data.model.Conquista
import com.rivaisdolar.app.data.model.Notificacao
import com.rivaisdolar.app.data.model.Previa
import com.rivaisdolar.app.data.model.TipoCarta
import com.rivaisdolar.app.data.model.Troca
import com.rivaisdolar.app.data.remote.Historico
import com.rivaisdolar.app.ui.historico.HistoricoConteudo
import com.rivaisdolar.app.ui.tarefas.FolhaAtividade
import com.rivaisdolar.app.ui.tarefas.FolhaUsarCarta
import com.rivaisdolar.app.data.model.Multiplicador
import com.rivaisdolar.app.data.model.Registro
import com.rivaisdolar.app.data.model.Tarefa
import com.rivaisdolar.app.data.model.Temporada
import com.rivaisdolar.app.data.remote.CasaSnapshot
import com.rivaisdolar.app.data.remote.Jogador
import com.rivaisdolar.app.ui.cartas.CartasTela
import com.rivaisdolar.app.ui.components.Aba
import com.rivaisdolar.app.ui.components.BarraAbas
import com.rivaisdolar.app.ui.components.BordaInferior
import com.rivaisdolar.app.ui.components.FolhaVidro
import com.rivaisdolar.app.ui.components.LocalHaze
import com.rivaisdolar.app.ui.placar.PlacarTela
import com.rivaisdolar.app.ui.principal.AcoesJogo
import com.rivaisdolar.app.ui.tarefas.FolhaConcluir
import com.rivaisdolar.app.ui.tarefas.FolhaValidar
import com.rivaisdolar.app.ui.tarefas.TarefasTela
import com.rivaisdolar.app.ui.temporada.FimTemporadaConteudo
import com.rivaisdolar.app.ui.theme.Cores
import com.rivaisdolar.app.ui.theme.RivaisTema
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import java.time.Instant

/**
 * Capturas das telas principais com dados de exemplo (ficam em app/build/outputs/roborazzi).
 * Servem para comparar com o protótipo docs/conceito.html sem precisar de um aparelho.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class TelasScreenshotTest {
    @get:Rule val regra = createComposeRule()

    private val agora: Instant = Instant.parse("2026-10-28T00:04:00Z")
    private fun iso(i: Instant) = i.toString()

    private val tarefas = listOf(
        Tarefa("t1", "c", "Limpar banheiro", "Pesada", "bathtub", 45, 2, 1.5, "semanal", prazo = iso(agora.minus(Duration.ofDays(3))), diasParada = 3),
        Tarefa("t0", "c", "Limpar o fogão", "Cozinha", "cooking", 30, 1, 1.4, "semanal", status = "aguardando", reservadaPor = "bia", reservadaEm = iso(agora.minus(Duration.ofHours(1)))),
        Tarefa("t2", "c", "Lavar louça", "Cozinha", "restaurant", 20, 1, 1.3, "diaria", status = "reservada", reservadaPor = "vitor", reservadaEm = iso(agora.minus(Duration.ofHours(2)))),
        Tarefa("t3", "c", "Gaiola dos porquinhos", "Pets", "pets", 25, 1, 1.6, "2x_semana", prazo = iso(agora.plus(Duration.ofHours(10)))),
        Tarefa("t4", "c", "Estender roupa", "Lavanderia", "checkroom", 15, 1, 1.1, "diaria"),
        Tarefa("t5", "c", "Mercado da semana", "Rua", "shopping_cart", 60, 1, 1.0, "semanal", status = "reservada", reservadaPor = "bia", reservadaEm = iso(agora.minus(Duration.ofMinutes(18 * 60 + 18)))),
        Tarefa("t6", "c", "Tirar o lixo", "Rápida", "delete", 5, 1, 1.3, "diaria"),
        Tarefa("t7", "c", "Planejar cardápio", "Carga mental", "edit_note", 20, 0, 1.5, "semanal"),
        Tarefa("t8", "c", "Aspirar a sala", "Limpeza", "mop", 25, 1, 1.2, "semanal"),
        Tarefa("t9", "c", "Regar plantas", "Rápida", "potted_plant", 10, 1, 1.0, "3x_semana"),
    )

    private val registro = Registro(
        "r1", "c", "t0", "bia", "Limpar o fogão", 65,
        listOf(Multiplicador("bomba", "Bomba · parada há 1 dia", 1.1)), 70, 7, status = "pendente",
        criadoEm = iso(agora.minus(Duration.ofMinutes(40))),
    )

    private val snapshot = CasaSnapshot(
        meuId = "vitor",
        casa = Casa("c", "Apê da Vila", "482913"),
        jogadores = listOf(
            Jogador("vitor", "Vitor", "azul", 3, 2240, 124, 4, "moderada"),
            Jogador("bia", "Bia", "laranja", 3, 2150, 118, 2, "moderada"),
        ),
        tarefas = tarefas,
        temporada = Temporada("s7", "c", 7, iso(agora.minus(Duration.ofDays(2))), iso(agora.plus(Duration.ofDays(4))),
            placar = mapOf("vitor" to 1240, "bia" to 1185), metaAlvo = 2000, metaAtual = 1445, metaPremio = "pizza de sábado"),
        registros = listOf(registro),
        ultimaEncerrada = Temporada("s6", "c", 6, iso(agora.minus(Duration.ofDays(9))), iso(agora.minus(Duration.ofDays(2))),
            placar = mapOf("vitor" to 1410, "bia" to 1335), aposta = "Você escolhe o restaurante", metaAlvo = 2000,
            metaAtual = 2745, metaPremio = "pizza", vencedor = "vitor", encerrada = true),
        atualizadoEm = 0,
        cartas = listOf(
            Carta("k1", "c", "vitor", "dobradinha", "rara", iso(agora)),
            Carta("k2", "c", "vitor", "veto", "epica", iso(agora)),
            Carta("k3", "c", "bia", "hora_feliz", "epica", iso(agora.minus(Duration.ofDays(1))), usadaEm = iso(agora.minus(Duration.ofHours(5)))),
        ),
        trocas = listOf(Troca("x1", "c", "bia", "vitor", "t5", "t2", "pendente", criadaEm = iso(agora.minus(Duration.ofHours(1))))),
        conquistas = listOf(
            Conquista("vitor", "primeira_faxina", iso(agora)), Conquista("bia", "primeira_faxina", iso(agora)),
            Conquista("vitor", "madrugador", iso(agora)), Conquista("bia", "rei_do_rejunte", iso(agora)),
        ),
        notificacoes = listOf(
            Notificacao("n1", "virada", "Virada no placar", "Bia passou na frente com Limpar banheiro.", criadaEm = iso(agora.minus(Duration.ofMinutes(12)))),
            Notificacao("n2", "bomba", "Limpar banheiro virou bomba", "Parada há 1 dia: agora vale mais, e sobe a cada dia.", criadaEm = iso(agora.minus(Duration.ofHours(3))), lidaEm = iso(agora)),
        ),
    )

    private val historico = Historico(
        temporadas = listOf(
            Temporada("s3", "c", 3, iso(agora), iso(agora), mapOf("vitor" to 980, "bia" to 1120), vencedor = "bia", encerrada = true),
            Temporada("s4", "c", 4, iso(agora), iso(agora), mapOf("vitor" to 1310, "bia" to 1205), vencedor = "vitor", encerrada = true),
            Temporada("s5", "c", 5, iso(agora), iso(agora), mapOf("vitor" to 1150, "bia" to 1290), vencedor = "bia", encerrada = true),
            Temporada("s6", "c", 6, iso(agora), iso(agora), mapOf("vitor" to 1410, "bia" to 1335), vencedor = "vitor", encerrada = true),
        ),
        registrosDaSemana = (0..2).flatMap { d ->
            listOf(
                Registro("rv$d", "c", "t1", "vitor", "x", 0, emptyList(), listOf(220, 400, 380)[d], 0, status = "validado", criadoEm = iso(agora.minus(Duration.ofDays(2L - d)))),
                Registro("rb$d", "c", "t1", "bia", "x", 0, emptyList(), listOf(310, 330, 260)[d], 0, status = "validado", criadoEm = iso(agora.minus(Duration.ofDays(2L - d)))),
            )
        },
    )

    private val acoes = object : AcoesJogo {
        override fun reservar(t: Tarefa, ok: () -> Unit) = Unit
        override fun liberar(t: Tarefa, ok: () -> Unit) = Unit
        override fun concluir(t: Tarefa, foto: ByteArray?, ok: () -> Unit) = Unit
        override suspend fun previa(t: Tarefa) = Previa(
            40,
            listOf(Multiplicador("sequencia", "Sequência de 4 dias", 1.2), Multiplicador("dobradinha", "Carta Dobradinha", 2.0)),
            95,
            9,
        )
        override suspend fun foto(caminho: String): ByteArray? = null
        override fun removerTarefa(id: String, ok: () -> Unit) = Unit
        override fun comprar(tipo: TipoCarta, ok: () -> Unit) = Unit
        override fun usarCarta(carta: Carta, alvo: String?, alvo2: String?, ok: () -> Unit) = Unit
        override fun responderTroca(troca: Troca, aceitar: Boolean, ok: () -> Unit) = Unit
        override fun recusasNaSemana() = 0
        override fun marcarLidas() = Unit
        override fun validar(r: Registro, ok: () -> Unit) = Unit
        override fun contestar(r: Registro, comentario: String, ok: () -> Unit) = Unit
        override fun definirMeta(alvo: Int, premio: String, ok: () -> Unit) = Unit
        override fun contestacoesRestantes() = 2
    }

    /** Mesma montagem da PrincipalTela: conteúdo como fonte do blur, barra de vidro por cima. */
    @Composable
    private fun Principal(aba: Aba, folha: (@Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit)? = null, conteudo: @Composable () -> Unit) {
        RivaisTema(Cores.Azul) {
            val haze = rememberHazeState()
            Box(Modifier.fillMaxSize().background(Cores.Fundo)) {
                Box(Modifier.fillMaxSize().hazeSource(haze)) { conteudo() }
                BordaInferior(Modifier.align(Alignment.BottomCenter))
                CompositionLocalProvider(LocalHaze provides haze) {
                    BarraAbas(aba, {}, false, {}, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp))
                    FolhaVidro(folha?.let { Unit }, {}) { folha?.invoke(this) }
                }
            }
        }
    }

    private fun capturar(nome: String, tela: @Composable () -> Unit) {
        regra.setContent(tela)
        regra.mainClock.advanceTimeBy(2_000)
        regra.onRoot().captureRoboImage("build/outputs/roborazzi/$nome.png")
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onRoot() =
        onNode(androidx.compose.ui.test.isRoot())

    @Test
    fun placar() = capturar("1-placar") {
        Principal(Aba.Placar) {
            PlacarTela(snapshot, agora, false, 0, rememberLazyListState(), {}, {}, {}, {}, {})
        }
    }

    @Test
    fun tarefas() = capturar("2-tarefas") {
        Principal(Aba.Tarefas) {
            TarefasTela(snapshot, agora, rememberLazyListState(), null, {}, {})
        }
    }

    @Test
    fun concluir() = capturar("3-concluir") {
        Principal(Aba.Tarefas, folha = { FolhaConcluir(tarefas[1], snapshot, acoes, false) {} }) {
            TarefasTela(snapshot, agora, rememberLazyListState(), null, {}, {})
        }
    }

    @Test
    fun validar() = capturar("4-validar") {
        Principal(Aba.Placar, folha = { FolhaValidar(registro, snapshot, agora, acoes, false) {} }) {
            PlacarTela(snapshot, agora, false, 0, rememberLazyListState(), {}, {}, {}, {}, {})
        }
    }

    @Test
    fun fimDeTemporada() = capturar("5-fim-de-temporada") {
        RivaisTema(Cores.Azul) {
            FimTemporadaConteudo(snapshot, snapshot.ultimaEncerrada!!, false, {}, { _, _ -> }, {})
        }
    }

    @Test
    fun cartas() = capturar("6-cartas") {
        Principal(Aba.Cartas) { CartasTela(snapshot, agora, rememberLazyListState(), false, {}, {}) }
    }

    @Test
    fun usarTroca() = capturar("7-usar-veto") {
        Principal(Aba.Cartas, folha = { FolhaUsarCarta(snapshot.cartas[1], snapshot, agora, acoes, false) {} }) {
            CartasTela(snapshot, agora, rememberLazyListState(), false, {}, {})
        }
    }

    @Test
    fun atividade() = capturar("8-atividade") {
        Principal(Aba.Placar, folha = { FolhaAtividade(snapshot, agora, acoes, {}, {}, {}) }) {
            PlacarTela(snapshot, agora, false, 0, rememberLazyListState(), {}, {}, {}, {}, {})
        }
    }

    @Test
    fun historicoTela() = capturar("9-historico") {
        RivaisTema(Cores.Azul) { HistoricoConteudo(snapshot.copy(temporada = snapshot.temporada?.copy(inicio = iso(agora.minus(Duration.ofDays(2))))), historico, {}, agora) }
    }
}
