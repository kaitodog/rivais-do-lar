package com.rivaisdolar.app.ui.vistorias

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rivaisdolar.app.data.model.Ocorrencia
import com.rivaisdolar.app.data.model.TipoOcorrencia
import com.rivaisdolar.app.data.remote.CasaSnapshot
import com.rivaisdolar.app.domain.instanteOuNull
import com.rivaisdolar.app.domain.pt
import com.rivaisdolar.app.domain.relativo
import com.rivaisdolar.app.ui.components.Aviso
import com.rivaisdolar.app.ui.components.BotaoCapsula
import com.rivaisdolar.app.ui.components.BotaoCirculo
import com.rivaisdolar.app.ui.components.BotaoTexto
import com.rivaisdolar.app.ui.components.CabecalhoFolha
import com.rivaisdolar.app.ui.components.CampoTexto
import com.rivaisdolar.app.ui.components.CartaoSolido
import com.rivaisdolar.app.ui.components.Divisoria
import com.rivaisdolar.app.ui.components.EstiloBotao
import com.rivaisdolar.app.ui.components.FolhaVidro
import com.rivaisdolar.app.ui.components.Grupo
import com.rivaisdolar.app.ui.components.IconeTarefa
import com.rivaisdolar.app.ui.components.Interruptor
import com.rivaisdolar.app.ui.components.Linha
import com.rivaisdolar.app.ui.components.LocalHaze
import com.rivaisdolar.app.ui.components.Secao
import com.rivaisdolar.app.ui.components.Segmentado
import com.rivaisdolar.app.ui.components.Simbolo
import com.rivaisdolar.app.ui.components.Texto
import com.rivaisdolar.app.ui.components.Vazio
import com.rivaisdolar.app.ui.components.toque
import com.rivaisdolar.app.ui.components.toqueLinha
import com.rivaisdolar.app.ui.juice.EventoJuice
import com.rivaisdolar.app.ui.juice.LocalJuice
import com.rivaisdolar.app.ui.principal.JogoViewModel
import com.rivaisdolar.app.ui.tarefas.prepararFoto
import com.rivaisdolar.app.ui.theme.Cores
import com.rivaisdolar.app.ui.theme.Tipo
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.io.File
import kotlinx.coroutines.launch

private val categorias = listOf("Quarto", "Cozinha", "Banheiro", "Sala", "Geral")
private val icones = listOf(
    "report", "checkroom", "restaurant", "delete", "bed",
    "water_drop", "shower", "lightbulb", "cleaning_services", "warning",
)
private val valores = listOf(5, 10, 15)

private sealed interface EdicaoVistoria {
    data object Nova : EdicaoVistoria
    data class Editar(val tipo: TipoOcorrencia) : EdicaoVistoria
}

private fun String.semAcento(): String =
    java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()

/** Tela de gerenciamento: liga/desliga, busca, agrupa por categoria e permite CRUD. */
@Composable
fun VistoriasTela(vm: JogoViewModel, aoVoltar: () -> Unit) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val ocupado by vm.ocupado.collectAsStateWithLifecycle()
    val aviso by vm.aviso.collectAsStateWithLifecycle()
    val s = snapshot ?: return
    val haze = rememberHazeState()
    var busca by rememberSaveable { mutableStateOf("") }
    var edicao by remember { mutableStateOf<EdicaoVistoria?>(null) }

    val filtradas = s.tiposVistoria.filter {
        busca.isBlank() || it.nome.semAcento().contains(busca.semAcento()) ||
            it.categoria.semAcento().contains(busca.semAcento())
    }

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
            item {
                Column {
                    Texto("Vistorias", Tipo.tituloGrande)
                    Texto(
                        "Nova vistoria para adicionar; toque numa existente para editar ou remover. Flagra vale na hora: o alvo perde os pontos e não tem conserto.",
                        Tipo.pequenoSecundario,
                        Modifier.padding(top = 3.dp),
                    )
                }
            }
            item {
                CartaoSolido {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Texto("Regras das vistorias", Tipo.tituloCartao)
                        Texto(
                            "2 vistorias por dia · 1 por tipo/dia · 50 pontos de perda por dia · 120 por temporada. A foto é obrigatória.",
                            Tipo.subtitulo,
                        )
                    }
                }
            }
            item {
                Grupo {
                    Linha(
                        titulo = "Vistorias ligadas",
                        subtitulo = if (s.casa.vistoriasAtivas) "Vocês podem flagrar um ao outro" else "Ninguém pode registrar flagras agora",
                        inicio = { IconeTarefa("flag", cor = if (s.casa.vistoriasAtivas) Cores.Alerta else Cores.TextoSecundario) },
                        fim = { Interruptor(s.casa.vistoriasAtivas, vm::definirVistorias) },
                    )
                }
            }
            item {
                CampoTexto(busca, { busca = it }, "Buscar vistoria ou categoria")
            }
            item {
                BotaoCapsula(
                    "Nova vistoria",
                    { edicao = EdicaoVistoria.Nova },
                    icone = "add",
                    estilo = EstiloBotao.Solido,
                )
            }
            if (filtradas.isEmpty()) {
                item {
                    CartaoSolido {
                        Vazio("flag", "Nenhuma vistoria", "Crie a primeira com o botão acima.")
                    }
                }
            } else {
                filtradas.groupBy { it.categoria }.toSortedMap().forEach { (categoria, lista) ->
                    item(key = "s-$categoria") { Secao(categoria) }
                    item(key = "g-$categoria") {
                        Grupo {
                            lista.forEachIndexed { i, tipo ->
                                if (i > 0) Divisoria()
                                Linha(
                                    titulo = tipo.nome,
                                    subtitulo = "${tipo.pontos} pontos · toque para editar",
                                    inicio = { IconeTarefa(tipo.icone, cor = if (tipo.pontos >= 15) Cores.Alerta else Cores.Texto) },
                                    fim = { Texto("−${tipo.pontos}", Tipo.numero, cor = Cores.Alerta) },
                                    seta = true,
                                    aoTocar = { edicao = EdicaoVistoria.Editar(tipo) },
                                )
                            }
                        }
                    }
                }
            }
            item {
                Texto(
                    "Limites: 2 vistorias por dia, 1 por tipo/dia, 50 pontos por dia e 120 por temporada. O reset da temporada limpa o B.O.",
                    Tipo.subtitulo,
                    Modifier.padding(horizontal = 4.dp),
                )
            }
        }

        CompositionLocalProvider(LocalHaze provides haze) {
            FolhaVidro(edicao, { edicao = null }) { e ->
                val tipo = (e as? EdicaoVistoria.Editar)?.tipo
                FormularioVistoria(
                    tipo = tipo,
                    ocupado = ocupado,
                    aoFechar = { edicao = null },
                    aoSalvar = { nome, categoria, pontos, icone ->
                        if (tipo == null) {
                            vm.criarTipoVistoria(nome, categoria, pontos, icone) { edicao = null }
                        } else {
                            vm.editarTipoVistoria(tipo, nome, categoria, pontos, icone) { edicao = null }
                        }
                    },
                    aoRemover = tipo?.let { { vm.removerTipoVistoria(it) { edicao = null } } },
                )
            }
        }
        Aviso(aviso, vm::limparAviso, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun ColumnScope.FormularioVistoria(
    tipo: TipoOcorrencia?,
    ocupado: Boolean,
    aoFechar: () -> Unit,
    aoSalvar: (String, String, Int, String) -> Unit,
    aoRemover: (() -> Unit)?,
) {
    var nome by remember(tipo?.id) { mutableStateOf(tipo?.nome.orEmpty()) }
    var categoria by remember(tipo?.id) { mutableStateOf(tipo?.categoria ?: "Geral") }
    var pontos by remember(tipo?.id) { mutableIntStateOf(tipo?.pontos ?: 10) }
    var icone by remember(tipo?.id) { mutableStateOf(tipo?.icone ?: "report") }
    var removendo by remember(tipo?.id) { mutableStateOf(false) }

    CabecalhoFolha(if (tipo == null) "Nova vistoria" else "Editar vistoria", aoFechar)
    CampoTexto(nome, { nome = it.take(40) }, "Ex.: Roupa no chão do banheiro", sublinhado = true)

    Texto("Categoria", Tipo.secao)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        categorias.forEach { c ->
            Chip(c, c == categoria) { categoria = c }
        }
    }

    Texto("Quanto custa", Tipo.secao)
    Segmentado(
        valores.map { "$it pts" },
        valores.indexOf(pontos).coerceAtLeast(0),
        { pontos = valores[it] },
    )

    Texto("Ícone", Tipo.secao)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        icones.forEach { i ->
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (i == icone) Cores.SuperficieAlta else Cores.Preenchimento)
                    .border(1.dp, if (i == icone) Cores.Texto else Cores.Separador, RoundedCornerShape(11.dp))
                    .toque { icone = i },
                contentAlignment = Alignment.Center,
            ) {
                Simbolo(i, tamanho = 19.sp, cor = if (i == icone) Cores.Texto else Cores.TextoSecundario)
            }
        }
    }

    BotaoCapsula(
        if (tipo == null) "Criar vistoria" else "Salvar",
        { aoSalvar(nome.trim(), categoria, pontos, icone) },
        habilitado = nome.trim().length >= 2,
        carregando = ocupado,
    )

    if (tipo != null && aoRemover != null) {
        if (removendo) {
            Texto(
                "Remover \"${tipo.nome}\"? O histórico de flagras continua; o tipo só some da lista.",
                Tipo.pequenoSecundario,
                Modifier.fillMaxWidth(),
                alinhamento = TextAlign.Center,
            )
            BotaoCapsula("Remover de vez", aoRemover, carregando = ocupado, tinta = Cores.Alerta)
            BotaoTexto("Cancelar", { removendo = false }, Modifier.align(Alignment.CenterHorizontally))
        } else {
            BotaoTexto(
                "Remover vistoria",
                { removendo = true },
                Modifier.align(Alignment.CenterHorizontally),
                cor = Cores.Alerta,
                habilitado = !ocupado,
            )
        }
    }
}

/** Sheet de flagra: escolhe o tipo, tira a foto e aplica a multa na hora. */
@Composable
fun ColumnScope.FolhaVistoria(
    s: CasaSnapshot,
    vm: JogoViewModel,
    ocupado: Boolean,
    aoFechar: () -> Unit,
    aoBoletim: () -> Unit,
) {
    val alvo = s.outro
    val juice = LocalJuice.current
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    var busca by remember { mutableStateOf("") }
    var categoria by remember { mutableStateOf<String?>(null) }
    var tipo by remember { mutableStateOf<TipoOcorrencia?>(null) }
    var foto by remember { mutableStateOf<ByteArray?>(null) }
    var preparando by remember { mutableStateOf(false) }
    var uriCamera by remember { mutableStateOf<Uri?>(null) }

    val arquivo = remember { File(context.cacheDir, "fotos").apply { mkdirs() }.resolve("vistoria.jpg") }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = uriCamera
        if (ok && uri != null) {
            preparando = true
            escopo.launch {
                foto = prepararFoto(context, uri)
                preparando = false
            }
        }
    }
    fun tirarFoto() {
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.arquivos", arquivo)
        }.getOrNull() ?: return
        uriCamera = uri
        camera.launch(uri)
    }

    CabecalhoFolha("Vistoria", aoFechar)
    if (alvo == null) {
        Texto("Você precisa de outra pessoa na casa para flagrar.", Tipo.pequenoSecundario, Modifier.fillMaxWidth(), alinhamento = TextAlign.Center)
        BotaoTexto("Ver B.O.", aoBoletim, Modifier.align(Alignment.CenterHorizontally))
        return
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Texto("Flagra para ${alvo.nome}", Tipo.linha, Modifier.weight(1f))
        BotaoTexto("Ver B.O.", aoBoletim)
    }
    Texto(
        "Vale na hora: ${alvo.nome} perde os pontos e não tem conserto. A foto é a prova.",
        Tipo.pequenoSecundario,
        Modifier.fillMaxWidth(),
    )

    CampoTexto(busca, { busca = it }, "Buscar vistoria ou categoria")
    val categoriasDisponiveis = s.tiposVistoria.map { it.categoria }.distinct().sorted()
    if (categoriasDisponiveis.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("Todas", categoria == null) { categoria = null }
            categoriasDisponiveis.forEach { c -> Chip(c, categoria == c) { categoria = c } }
        }
    }

    val filtradas = s.tiposVistoria.filter { t ->
        (categoria == null || t.categoria == categoria) &&
            (busca.isBlank() || t.nome.semAcento().contains(busca.semAcento()) || t.categoria.semAcento().contains(busca.semAcento()))
    }

    if (filtradas.isEmpty()) {
        Texto(
            "Nenhuma vistoria encontrada. Cadastre os tipos em Casa → Vistorias.",
            Tipo.pequenoSecundario,
            Modifier.fillMaxWidth(),
            alinhamento = TextAlign.Center,
        )
    } else {
        filtradas.groupBy { it.categoria }.toSortedMap().forEach { (cat, lista) ->
            Texto(cat.uppercase(), Tipo.secao)
            Column {
                lista.forEach { t ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toqueLinha { tipo = t; foto = null }
                            .padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconeTarefa(t.icone, cor = if (t.pontos >= 15) Cores.Alerta else Cores.Texto)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Texto(t.nome, Tipo.linha, linhas = 1)
                            Texto("${t.pontos} pontos", Tipo.subtitulo)
                        }
                        if (tipo?.id == t.id) Simbolo("check_circle", tamanho = 19.sp, cor = Cores.Alerta)
                        else Texto("−${t.pontos}", Tipo.numero, cor = Cores.Alerta)
                    }
                    Divisoria()
                }
            }
        }
    }

    val escolhido = tipo
    if (escolhido != null) {
        Texto("Foto do flagra", Tipo.secao)
        FotoFlagra(foto, preparando, aoTirar = ::tirarFoto, aoLimpar = { foto = null })
        BotaoCapsula(
            "Registrar flagra (−${escolhido.pontos})",
            {
                val bytes = foto ?: return@BotaoCapsula
                vm.registrarVistoria(escolhido, bytes) {
                    juice.disparar(EventoJuice.Vistoria)
                    aoFechar()
                }
            },
            habilitado = foto != null && !preparando,
            carregando = ocupado,
            tinta = Cores.Alerta,
        )
        Texto(
            "A foto é obrigatória. O desconto entra no teto de 50/dia e 120/temporada.",
            Tipo.subtitulo,
            Modifier.fillMaxWidth(),
            alinhamento = TextAlign.Center,
        )
    }
}

/** B.O. Doméstico: busca, filtro por categoria e agrupamento. */
@Composable
fun ColumnScope.FolhaBoletim(
    s: CasaSnapshot,
    agora: java.time.Instant,
    aoFechar: () -> Unit,
) {
    var busca by remember { mutableStateOf("") }
    var categoria by remember { mutableStateOf<String?>(null) }
    val categoriasDisponiveis = s.ocorrencias.map { it.tipoCategoria }.distinct().sorted()
    val filtradas = s.ocorrencias.filter { o ->
        (categoria == null || o.tipoCategoria == categoria) &&
            (busca.isBlank() ||
                o.tipoNome.semAcento().contains(busca.semAcento()) ||
                o.tipoCategoria.semAcento().contains(busca.semAcento()) ||
                (s.jogador(o.autor)?.nome ?: "").semAcento().contains(busca.semAcento()))
    }

    CabecalhoFolha("B.O. Doméstico", aoFechar)
    Texto(
        "${s.ocorrencias.size} flagras nesta temporada. A multa já foi aplicada.",
        Tipo.pequenoSecundario,
        Modifier.fillMaxWidth(),
    )
    CampoTexto(busca, { busca = it }, "Buscar flagra, categoria ou pessoa")
    if (categoriasDisponiveis.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("Todas", categoria == null) { categoria = null }
            categoriasDisponiveis.forEach { c -> Chip(c, categoria == c) { categoria = c } }
        }
    }

    if (filtradas.isEmpty()) {
        Texto("Nada no B.O. por aqui.", Tipo.pequenoSecundario, Modifier.fillMaxWidth(), alinhamento = TextAlign.Center)
        return
    }

    filtradas.groupBy { it.tipoCategoria }.toSortedMap().forEach { (cat, lista) ->
        Texto(cat.uppercase(), Tipo.secao)
        Column {
            lista.forEach { o ->
                val icone = s.tiposVistoria.firstOrNull { it.id == o.tipoId }?.icone ?: "report"
                val autor = s.jogador(o.autor)?.nome ?: "Alguém"
                val alvo = s.jogador(o.alvo)?.nome ?: "alguém"
                Linha(
                    titulo = o.tipoNome,
                    subtitulo = "$autor flagrou $alvo · ${o.criadaEm.instanteOuNull()?.relativo(agora) ?: ""}",
                    inicio = { IconeTarefa(icone, cor = Cores.Alerta) },
                    fim = { Texto("−${o.pontos}", Tipo.numero, cor = Cores.Alerta) },
                )
            }
        }
    }
}

@Composable
private fun FotoFlagra(
    foto: ByteArray?,
    preparando: Boolean,
    aoTirar: () -> Unit,
    aoLimpar: () -> Unit,
) {
    val imagem = remember(foto) {
        foto?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull() }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Cores.Preenchimento)
            .border(1.dp, Cores.Separador, RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (imagem != null) {
            Image(imagem, "Foto do flagra", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Simbolo("photo_camera", tamanho = 30.sp, cor = Cores.TextoSecundario)
                Texto(if (preparando) "Preparando a foto…" else "Sem foto ainda", Tipo.subtitulo, Modifier.padding(top = 4.dp))
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        BotaoTexto(if (foto == null) "Tirar foto" else "Trocar foto", aoTirar, cor = Cores.TextoSecundario)
        if (foto != null) BotaoTexto("Remover", aoLimpar, cor = Cores.TextoSecundario)
    }
}

@Composable
private fun Chip(texto: String, ativo: Boolean, aoTocar: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (ativo) Cores.SuperficieAlta else Cores.Superficie)
            .border(1.dp, if (ativo) Cores.Texto else Cores.Separador, CircleShape)
            .toque(aoTocar = aoTocar)
            .padding(horizontal = 11.dp, vertical = 7.dp),
    ) {
        Texto(texto, Tipo.pequeno.copy(fontSize = 11.sp), cor = if (ativo) Cores.Texto else Cores.TextoSecundario)
    }
}
