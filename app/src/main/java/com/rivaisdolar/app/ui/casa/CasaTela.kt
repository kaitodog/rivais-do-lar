package com.rivaisdolar.app.ui.casa

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import com.rivaisdolar.app.BuildConfig
import com.rivaisdolar.app.push.Push
import com.rivaisdolar.app.domain.pt
import com.rivaisdolar.app.ui.components.Aviso
import com.rivaisdolar.app.ui.components.Avatar
import com.rivaisdolar.app.ui.components.BotaoCapsula
import com.rivaisdolar.app.ui.components.BotaoCirculo
import com.rivaisdolar.app.ui.components.BotaoTexto
import com.rivaisdolar.app.ui.components.CabecalhoFolha
import com.rivaisdolar.app.ui.components.CampoTexto
import com.rivaisdolar.app.ui.components.Divisoria
import com.rivaisdolar.app.ui.components.FolhaVidro
import com.rivaisdolar.app.ui.components.Grupo
import com.rivaisdolar.app.ui.components.IconeTarefa
import com.rivaisdolar.app.ui.components.Interruptor
import com.rivaisdolar.app.ui.components.Linha
import com.rivaisdolar.app.ui.components.LocalHaze
import com.rivaisdolar.app.ui.components.Ponto
import com.rivaisdolar.app.ui.components.Secao
import com.rivaisdolar.app.ui.components.Segmentado
import com.rivaisdolar.app.ui.components.Simbolo
import com.rivaisdolar.app.ui.components.Texto
import com.rivaisdolar.app.ui.principal.JogoViewModel
import com.rivaisdolar.app.ui.principal.pontosVale
import com.rivaisdolar.app.ui.theme.Cores
import com.rivaisdolar.app.ui.theme.Tipo
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

private sealed interface Edicao {
    data object Nome : Edicao
    data object NomeCasa : Edicao
    data object Aposta : Edicao
    data object SairCasa : Edicao
}

private val niveis = listOf("desligada" to "Desligada", "moderada" to "Moderada", "sem_piedade" to "Sem piedade")

@Composable
fun CasaTela(
    vm: JogoViewModel,
    aoVoltar: () -> Unit,
    aoEditarTarefa: (String?) -> Unit,
    aoUltimaTemporada: (String) -> Unit,
    aoHistorico: () -> Unit,
    aoProcurarAtualizacao: () -> Unit,
) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val ocupado by vm.ocupado.collectAsStateWithLifecycle()
    val aviso by vm.aviso.collectAsStateWithLifecycle()
    val s = snapshot ?: return
    val context = LocalContext.current
    var edicao by remember { mutableStateOf<Edicao?>(null) }
    val haze = rememberHazeState()
    val eu = s.eu

    Box(Modifier.fillMaxSize().background(Cores.Fundo)) {
        LazyColumn(
            Modifier.fillMaxSize().hazeSource(haze).statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().height(38.dp)) {
                    BotaoCirculo("arrow_back", "Voltar", aoVoltar)
                }
            }
            item { Texto("Casa", Tipo.tituloGrande) }

            item { Secao("Perfil") }
            item {
                Grupo {
                    Linha(
                        titulo = eu?.nome ?: "Você",
                        subtitulo = vm.email ?: "Toque para mudar o nome",
                        inicio = { Avatar(eu?.nome ?: "?", Cores.jogador(eu?.cor), 32.dp) },
                        seta = true,
                        aoTocar = { edicao = Edicao.Nome },
                    )
                    Divisoria()
                    Linha(
                        titulo = "Sua cor",
                        inicio = { IconeTarefa("palette") },
                        fim = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("azul" to "Azul", "laranja" to "Laranja").forEach { (cor, nome) ->
                                    BotaoTexto(nome, { vm.definirCor(cor) }, cor = if (eu?.cor == cor) Cores.jogador(cor) else Cores.TextoSecundario)
                                }
                            }
                        },
                    )
                    Divisoria()
                    Linha(
                        titulo = "Nível ${eu?.nivel ?: 1}",
                        subtitulo = "${(eu?.xp ?: 0).pt()} XP · ${(eu?.moedas ?: 0).pt()} moedas",
                        inicio = { IconeTarefa("military_tech") },
                    )
                    Divisoria()
                    Linha("Sair da conta", inicio = { IconeTarefa("logout") }, aoTocar = vm::sairDaConta)
                }
            }

            item { Secao("Casa", Modifier.padding(top = 4.dp)) }
            item {
                Grupo {
                    Linha(s.casa.nome, subtitulo = "Nome da casa", inicio = { IconeTarefa("home") }, seta = true, aoTocar = { edicao = Edicao.NomeCasa })
                    Divisoria()
                    Linha(
                        titulo = s.casa.codigoConvite.chunked(3).joinToString(" "),
                        subtitulo = if (s.outro == null) "Código de convite · toque para compartilhar" else "Código de convite",
                        inicio = { IconeTarefa("key") },
                        fim = { Simbolo("ios_share", cor = Cores.TextoSecundario) },
                        aoTocar = { compartilharCodigo(context, s.casa.codigoConvite) },
                    )
                    s.jogadores.forEach { j ->
                        Divisoria()
                        Linha(
                            titulo = j.nome + if (j.userId == s.meuId) " (você)" else "",
                            subtitulo = "Nível ${j.nivel} · ${j.xp.pt()} XP",
                            inicio = { Avatar(j.nome, Cores.jogador(j.cor), 32.dp) },
                            depoisDoTitulo = { Ponto(Cores.jogador(j.cor)) },
                        )
                    }
                    Divisoria()
                    Linha("Sair da casa", inicio = { IconeTarefa("door_open") }, aoTocar = { edicao = Edicao.SairCasa })
                }
            }

            item { Secao("Temporada ${s.temporada?.numero ?: ""}", Modifier.padding(top = 4.dp)) }
            item {
                Grupo {
                    Linha(
                        titulo = s.temporada?.aposta ?: "Definir a aposta",
                        subtitulo = "Aposta da semana: quem vencer leva",
                        inicio = { IconeTarefa("redeem", cor = Cores.Ouro) },
                        seta = true,
                        aoTocar = { edicao = Edicao.Aposta },
                    )
                    Divisoria()
                    Linha(
                        titulo = "Histórico e conquistas",
                        subtitulo = "Temporadas anteriores, semana dia a dia e títulos",
                        inicio = { IconeTarefa("bar_chart") },
                        seta = true,
                        aoTocar = aoHistorico,
                    )
                    s.ultimaEncerrada?.let { t ->
                        Divisoria()
                        Linha(
                            titulo = "Temporada ${t.numero}",
                            subtitulo = "Resultado da última temporada",
                            inicio = { IconeTarefa("emoji_events", cor = Cores.Ouro) },
                            seta = true,
                            aoTocar = { aoUltimaTemporada(t.id) },
                        )
                    }
                }
            }

            item {
                Column(Modifier.padding(top = 4.dp)) {
                    Secao("Tarefas da casa")
                    Texto(
                        "Crie as tarefas do jeito de vocês. Toque numa tarefa para editar ou remover.",
                        Tipo.subtitulo,
                        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
                    )
                }
            }
            item {
                Grupo {
                    Linha("Criar tarefa", subtitulo = "Nome, categoria, ícone, tempo e chatice", inicio = { IconeTarefa("add") }, seta = true, aoTocar = { aoEditarTarefa(null) })
                    s.tarefas.sortedBy { it.titulo }.forEach { t ->
                        Divisoria()
                        Linha(
                            titulo = t.titulo,
                            subtitulo = "${t.categoria} · ${t.minutos} min",
                            inicio = { IconeTarefa(t.icone) },
                            fim = { Texto(t.pontosVale().pt(), Tipo.numero) },
                            seta = true,
                            aoTocar = { aoEditarTarefa(t.id) },
                        )
                    }
                }
            }

            item { Secao("Preferências", Modifier.padding(top = 4.dp)) }
            item {
                Grupo {
                    Box(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Texto("Provocações", Tipo.linha)
                            Segmentado(
                                niveis.map { it.second },
                                niveis.indexOfFirst { it.first == eu?.provocacao }.coerceAtLeast(0),
                                { vm.definirProvocacao(niveis[it].first) },
                            )
                            Texto(
                                when (eu?.provocacao) {
                                    "desligada" -> "Nada de push. As novidades ficam só no sino do placar."
                                    "sem_piedade" -> "Tudo: viradas, bombas, reservas quase roubáveis e cartas, no tom mais implicante."
                                    else -> "O essencial: viradas, pedidos de validação, trocas e fim de temporada."
                                },
                                Tipo.subtitulo,
                            )
                            if (!Push.configurado) {
                                Texto("Este APK foi gerado sem Firebase: as notificações aparecem só dentro do app.", Tipo.subtitulo)
                            } else if (!Push.permitido(context)) {
                                BotaoTexto("Permitir notificações no celular", {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                                    )
                                }, cor = Cores.TextoSecundario)
                            }
                        }
                    }
                    Divisoria(14.dp)
                    Linha(
                        titulo = "Modo trégua",
                        subtitulo = "Pausa a competição: o placar para, moedas e cartas ficam guardadas e tudo conta só para a meta da casa.",
                        fim = { Interruptor(s.casa.modoTregua, vm::definirTregua) },
                    )
                }
            }

            item { Secao("App", Modifier.padding(top = 4.dp)) }
            item {
                Grupo {
                    Linha("Procurar atualização", subtitulo = "Versão ${BuildConfig.VERSION_NAME}", inicio = { IconeTarefa("system_update") }, aoTocar = aoProcurarAtualizacao)
                }
            }
        }

        CompositionLocalProvider(LocalHaze provides haze) {
            FolhaVidro(edicao, { edicao = null }) { e ->
                val fechar = { edicao = null }
                when (e) {
                    Edicao.Nome -> EditarTexto("Seu nome", eu?.nome.orEmpty(), "Como você aparece no placar", 40, ocupado, fechar) {
                        vm.atualizarNome(it, fechar)
                    }
                    Edicao.NomeCasa -> EditarTexto("Nome da casa", s.casa.nome, "Ex.: Apê da Vila", 40, ocupado, fechar) {
                        vm.atualizarNomeCasa(it, fechar)
                    }
                    Edicao.Aposta -> EditarTexto("Aposta da semana", s.temporada?.aposta.orEmpty(), "Ex.: vencedor escolhe o restaurante", 120, ocupado, fechar) {
                        vm.definirAposta(it, fechar)
                    }
                    Edicao.SairCasa -> {
                        CabecalhoFolha("Sair da casa?", fechar)
                        Texto(
                            "Seu placar e suas reservas ficam para trás. Para voltar, use o código de convite de novo.",
                            Tipo.pequenoSecundario,
                            Modifier.fillMaxWidth(),
                            alinhamento = TextAlign.Center,
                        )
                        BotaoCapsula("Sair da casa", { vm.sairDaCasa(fechar) }, carregando = ocupado)
                        BotaoTexto("Cancelar", fechar, Modifier.align(Alignment.CenterHorizontally))
                    }
                }
            }
        }
        Aviso(aviso, vm::limparAviso, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.EditarTexto(
    titulo: String,
    inicial: String,
    dica: String,
    limite: Int,
    ocupado: Boolean,
    aoFechar: () -> Unit,
    aoSalvar: (String) -> Unit,
) {
    var texto by remember { mutableStateOf(inicial) }
    CabecalhoFolha(titulo, aoFechar)
    CampoTexto(texto, { texto = it.take(limite) }, dica, sublinhado = true)
    Box(Modifier.width(1.dp))
    BotaoCapsula("Salvar", { aoSalvar(texto.trim()) }, habilitado = texto.isNotBlank(), carregando = ocupado)
}

private fun compartilharCodigo(context: Context, codigo: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Código", codigo))
    val envio = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, "Bora competir na faxina? Baixe o Rivais do Lar e entre na minha casa com o código $codigo")
    context.startActivity(Intent.createChooser(envio, "Compartilhar código"))
}
