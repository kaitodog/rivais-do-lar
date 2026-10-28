package com.rivaisdolar.app.ui.tarefas

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.rivaisdolar.app.data.model.Carta
import com.rivaisdolar.app.data.model.Esforcos
import com.rivaisdolar.app.data.model.Multiplicador
import com.rivaisdolar.app.data.model.Recorrencias
import com.rivaisdolar.app.data.model.Registro
import com.rivaisdolar.app.data.model.Tarefa
import com.rivaisdolar.app.data.model.TipoCarta
import com.rivaisdolar.app.data.model.Troca
import com.rivaisdolar.app.data.remote.CasaSnapshot
import com.rivaisdolar.app.domain.Pontuacao
import com.rivaisdolar.app.domain.curta
import com.rivaisdolar.app.domain.decimal
import com.rivaisdolar.app.domain.instanteOuNull
import com.rivaisdolar.app.domain.plural
import com.rivaisdolar.app.domain.pt
import com.rivaisdolar.app.domain.relativo
import com.rivaisdolar.app.ui.components.BotaoCapsula
import com.rivaisdolar.app.ui.components.BotaoTexto
import com.rivaisdolar.app.ui.components.CabecalhoFolha
import com.rivaisdolar.app.ui.components.CampoTexto
import com.rivaisdolar.app.ui.components.EstiloBotao
import com.rivaisdolar.app.ui.components.IconeTarefa
import com.rivaisdolar.app.ui.components.Ponto
import com.rivaisdolar.app.ui.components.Simbolo
import com.rivaisdolar.app.ui.components.Texto
import com.rivaisdolar.app.ui.components.toqueLinha
import com.rivaisdolar.app.ui.principal.AcoesJogo
import com.rivaisdolar.app.ui.principal.alvoDoVeto
import com.rivaisdolar.app.ui.principal.bomba
import com.rivaisdolar.app.ui.principal.legenda
import com.rivaisdolar.app.ui.principal.pontosVale
import com.rivaisdolar.app.ui.principal.roubavelEm
import com.rivaisdolar.app.ui.principal.vetadaPor
import com.rivaisdolar.app.ui.theme.Cores
import com.rivaisdolar.app.ui.theme.Tipo
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant

// Dentro de um sheet de vidro o conteúdo é só texto e divisórias: nada de vidro sobre vidro.

@Composable
private fun Fio() {
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(Cores.Separador))
}

@Composable
private fun LinhaExtrato(rotulo: String, valor: String, ultima: Boolean = false) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Texto(rotulo, Tipo.pequenoSecundario.copy(fontSize = 13.5.sp), Modifier.weight(1f))
            Texto(valor, Tipo.numero.copy(fontSize = 13.5.sp))
        }
        if (!ultima) Fio()
    }
}

private fun mult(valor: Double) = "× ${valor.decimal()}"

private fun Tarefa.rotuloBase() =
    "Base · $minutos min × ${Pontuacao.multiplicadorEsforco(esforco).toDouble().decimal()} × ${chatice.decimal()}"

@Composable
private fun Extrato(rotuloBase: String, base: Int, multiplicadores: List<Multiplicador>, observacoes: List<String> = emptyList()) {
    Column {
        LinhaExtrato(rotuloBase, base.pt(), ultima = multiplicadores.isEmpty() && observacoes.isEmpty())
        multiplicadores.forEachIndexed { i, m ->
            LinhaExtrato(m.rotulo, mult(m.valor), ultima = i == multiplicadores.lastIndex && observacoes.isEmpty())
        }
        observacoes.forEach { Texto(it, Tipo.subtitulo, Modifier.padding(vertical = 6.dp, horizontal = 2.dp)) }
    }
}

@Composable
private fun Total(pontos: Int, moedas: Int, rotulo: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Texto(rotulo, Tipo.subtitulo.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium))
            Texto("+${pontos.pt()}", Tipo.total)
        }
        Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Simbolo("toll", tamanho = 15.sp, cor = Cores.TextoSecundario)
            Texto(" +${plural(moedas, "moeda", "moedas")}", Tipo.pequenoSecundario)
        }
    }
}

@Composable
private fun FotoMoldura(imagem: ImageBitmap?, altura: Int = 160, vazio: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(altura.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF2C2C30), Color(0xFF1C1C1E)))),
        contentAlignment = Alignment.Center,
    ) {
        if (imagem != null) {
            Image(imagem, "Foto do depois", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            vazio()
        }
    }
}

private fun ByteArray.imagem(): ImageBitmap? = runCatching { BitmapFactory.decodeByteArray(this, 0, size)?.asImageBitmap() }.getOrNull()

/** Detalhe e ações da tarefa (reservar, concluir, largar, editar, remover). */
@Composable
fun ColumnScope.FolhaDetalhe(
    t: Tarefa,
    s: CasaSnapshot,
    agora: Instant,
    vm: AcoesJogo,
    ocupado: Boolean,
    aoFechar: () -> Unit,
    aoConcluir: () -> Unit,
    aoValidar: (Registro) -> Unit,
    aoEditar: () -> Unit,
) {
    val minha = t.reservadaPor == s.meuId
    val pendente = s.registroPendenteDa(t.id)
    val leg = legenda(t, s, agora, doPlacar = false)
    val haptico = LocalHapticFeedback.current
    val vetadaPorMim = t.vetadaPor(agora) == s.meuId
    var removendo by remember(t.id) { mutableStateOf(false) }

    CabecalhoFolha(t.titulo, aoFechar)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconeTarefa(t.icone, bomba = t.bomba(agora) && t.status != "aguardando")
        Column(Modifier.weight(1f)) {
            Texto("${t.categoria} · ${t.minutos} min · ${Esforcos.rotulo(t.esforco)}", Tipo.pequeno)
            Texto(leg.texto, Tipo.subtitulo, cor = leg.cor)
        }
    }
    Column {
        LinhaExtrato(t.rotuloBase(), Pontuacao.pontos(t.minutos, t.esforco, t.chatice).pt())
        if (t.diasParada > 0) LinhaExtrato("Bomba · parada há ${plural(t.diasParada, "dia", "dias")}", mult(Pontuacao.bonusBomba(t.diasParada).toDouble()))
        LinhaExtrato("Repete", Recorrencias.rotulo(t.recorrencia), ultima = true)
    }
    Total(t.pontosVale(), Pontuacao.moedas(t.pontosVale()), "Vale")

    if (removendo) {
        Texto(
            "Remover \"${t.titulo}\"? Ela sai da lista da casa; os pontos que já valeram continuam no histórico.",
            Tipo.pequenoSecundario,
            Modifier.fillMaxWidth(),
            alinhamento = TextAlign.Center,
        )
        BotaoCapsula("Remover tarefa", { vm.removerTarefa(t.id, aoFechar) }, carregando = ocupado, icone = "delete")
        BotaoTexto("Cancelar", { removendo = false }, Modifier.align(Alignment.CenterHorizontally))
        return
    }

    when {
        t.status == "livre" && vetadaPorMim -> {
            Texto("Você vetou essa tarefa nesta temporada: ela fica com a outra pessoa.", Tipo.pequenoSecundario, Modifier.fillMaxWidth(), alinhamento = TextAlign.Center)
        }
        t.status == "livre" -> {
            BotaoCapsula("Reservar", { vm.reservar(t) { haptico.performHapticFeedback(HapticFeedbackType.Confirm); aoFechar() } }, carregando = ocupado)
            BotaoTexto("Já fiz agora", aoConcluir, Modifier.align(Alignment.CenterHorizontally))
        }
        t.status == "reservada" && minha -> {
            BotaoCapsula("Concluir", aoConcluir)
            BotaoTexto("Largar tarefa", { vm.liberar(t, aoFechar) }, Modifier.align(Alignment.CenterHorizontally), habilitado = !ocupado)
        }
        t.status == "reservada" -> {
            val resta = t.roubavelEm(agora)
            Texto(
                "${s.jogador(t.reservadaPor)?.nome ?: "A outra pessoa"} reservou esta tarefa." +
                    (resta?.let { " Se não concluir, ela fica livre em ${it.curta()}." } ?: ""),
                Tipo.pequenoSecundario,
                alinhamento = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        pendente != null && pendente.userId != s.meuId -> {
            BotaoCapsula("Revisar e validar", { aoValidar(pendente) })
        }
        else -> {
            Texto(
                "Esperando ${s.outro?.nome ?: "a outra pessoa"} validar. Sem resposta em 12h, vale sozinho.",
                Tipo.pequenoSecundario,
                alinhamento = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        BotaoTexto("Editar", aoEditar, cor = Cores.TextoSecundario)
        if (t.status != "aguardando") BotaoTexto("Remover", { removendo = true }, cor = Cores.TextoSecundario)
    }
}

/** Concluir: foto do depois (câmera ou galeria), extrato calculado pelo servidor e envio. */
@Composable
fun ColumnScope.FolhaConcluir(
    t: Tarefa,
    s: CasaSnapshot,
    vm: AcoesJogo,
    ocupado: Boolean,
    aoFechar: () -> Unit,
) {
    val haptico = LocalHapticFeedback.current
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    var foto by remember(t.id) { mutableStateOf<ByteArray?>(null) }
    var preparando by remember { mutableStateOf(false) }
    val imagem = remember(foto) { foto?.imagem() }
    val previa by produceState(initialValue = null as com.rivaisdolar.app.data.model.Previa?, t.id) { value = vm.previa(t) }

    val arquivoCamera = remember { File(context.cacheDir, "fotos").apply { mkdirs() }.resolve("camera.jpg") }
    val uriCamera = remember { FileProvider.getUriForFile(context, "${context.packageName}.arquivos", arquivoCamera) }
    fun receber(uri: android.net.Uri) {
        preparando = true
        escopo.launch {
            foto = prepararFoto(context, uri)
            preparando = false
        }
    }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(::receber) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) receber(uriCamera) }

    CabecalhoFolha(t.titulo, aoFechar)
    FotoMoldura(imagem, altura = if (imagem != null) 180 else 118) {
        Simbolo("image", tamanho = 30.sp, cor = Color(0x59EBEBF5))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        if (preparando) {
            Texto("Preparando a foto…", Tipo.pequenoSecundario)
        } else {
            BotaoTexto(if (imagem == null) "Tirar foto do depois" else "Tirar outra", { camera.launch(uriCamera) }, cor = Cores.TextoSecundario)
            BotaoTexto("Galeria", { galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, cor = Cores.TextoSecundario)
            if (imagem != null) BotaoTexto("Sem foto", { foto = null }, cor = Cores.TextoSecundario)
        }
    }

    // Sem conexão a prévia não chega: mostra a conta local (base e bomba) até o servidor responder.
    val p = previa
    if (p != null) {
        Extrato(t.rotuloBase(), p.base, p.multiplicadores)
        Total(p.pontos, p.moedas, "Você ganha")
    } else {
        Extrato(
            t.rotuloBase(),
            Pontuacao.pontos(t.minutos, t.esforco, t.chatice),
            if (t.diasParada > 0) listOf(Multiplicador("bomba", "Bomba · parada há ${plural(t.diasParada, "dia", "dias")}", Pontuacao.bonusBomba(t.diasParada).toDouble())) else emptyList(),
        )
        Total(t.pontosVale(), Pontuacao.moedas(t.pontosVale()), "Você ganha")
    }
    BotaoCapsula(
        "Enviar pra validar",
        {
            vm.concluir(t, foto) {
                haptico.performHapticFeedback(HapticFeedbackType.Confirm)
                aoFechar()
            }
        },
        carregando = ocupado,
        habilitado = !preparando,
    )
    Texto(
        "${s.outro?.nome ?: "A outra pessoa"} confirma com um toque. Sem resposta em 12h, vale sozinho." +
            " Teto de ${Pontuacao.TETO_DIARIO} pontos por dia.",
        Tipo.subtitulo,
        Modifier.fillMaxWidth(),
        alinhamento = TextAlign.Center,
    )
}

/** Validar com um toque ou contestar com comentário (máximo 2 por semana). */
@Composable
fun ColumnScope.FolhaValidar(
    r: Registro,
    s: CasaSnapshot,
    agora: Instant,
    vm: AcoesJogo,
    ocupado: Boolean,
    aoFechar: () -> Unit,
) {
    val haptico = LocalHapticFeedback.current
    var contestando by remember(r.id) { mutableStateOf(false) }
    var comentario by remember(r.id) { mutableStateOf("") }
    val restantes = vm.contestacoesRestantes()
    val autor = s.jogador(r.userId)?.nome ?: "A outra pessoa"
    val bytes by produceState(initialValue = null as ByteArray?, r.fotoPath) { value = r.fotoPath?.let { vm.foto(it) } }
    val imagem = remember(bytes) { bytes?.imagem() }
    val tarefa = s.tarefas.firstOrNull { it.id == r.tarefaId }

    CabecalhoFolha(r.titulo, aoFechar)
    if (r.fotoPath != null) {
        FotoMoldura(imagem, altura = 200) { Texto("Carregando a foto…", Tipo.pequenoSecundario) }
    }
    Texto(
        "$autor concluiu ${r.criadoEm.instanteOuNull()?.relativo(agora) ?: ""}. Confere se ficou feito?",
        Tipo.pequenoSecundario,
        Modifier.fillMaxWidth(),
        alinhamento = TextAlign.Center,
    )
    Extrato(tarefa?.rotuloBase() ?: "Base", r.pontosBase, r.multiplicadores)
    Total(r.pontosFinais, r.moedas, "$autor ganha")

    if (!contestando) {
        BotaoCapsula(
            "Validar",
            {
                vm.validar(r) {
                    haptico.performHapticFeedback(HapticFeedbackType.Confirm)
                    aoFechar()
                }
            },
            carregando = ocupado,
            icone = "check",
        )
        BotaoTexto(
            if (restantes > 0) "Contestar ($restantes ${if (restantes == 1) "restante" else "restantes"} na semana)" else "Sem contestações nesta semana",
            { contestando = true },
            Modifier.align(Alignment.CenterHorizontally),
            habilitado = restantes > 0,
        )
    } else {
        CampoTexto(comentario, { comentario = it.take(200) }, "O que ficou faltando?", sublinhado = true, linhas = 3, teclado = KeyboardType.Text)
        BotaoCapsula(
            "Enviar contestação",
            {
                vm.contestar(r, comentario) {
                    haptico.performHapticFeedback(HapticFeedbackType.Reject)
                    aoFechar()
                }
            },
            habilitado = comentario.trim().length >= 3,
            carregando = ocupado,
        )
        BotaoTexto("Voltar", { contestando = false }, Modifier.align(Alignment.CenterHorizontally))
    }
}

/** Notificações (o mesmo texto do push), pedidos de validação e trocas. */
@Composable
fun ColumnScope.FolhaAtividade(
    s: CasaSnapshot,
    agora: Instant,
    vm: AcoesJogo,
    aoFechar: () -> Unit,
    aoValidar: (Registro) -> Unit,
    aoTroca: (Troca) -> Unit,
) {
    LaunchedEffect(Unit) { vm.marcarLidas() }
    CabecalhoFolha("Atividade", aoFechar)
    val pendentes = s.pendentesParaMim
    val troca = s.trocaParaMim
    if (pendentes.isNotEmpty() || troca != null) {
        Texto("ESPERANDO VOCÊ", Tipo.secao)
        Column {
            troca?.let { tr ->
                Row(Modifier.fillMaxWidth().toqueLinha { aoTroca(tr) }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Texto("${s.jogador(tr.propostaPor)?.nome} quer trocar", Tipo.linha)
                        Texto("${s.tarefas.firstOrNull { it.id == tr.tarefaMinha }?.titulo} pela sua ${s.tarefas.firstOrNull { it.id == tr.tarefaDele }?.titulo}", Tipo.subtitulo)
                    }
                    Simbolo("chevron_right", tamanho = 18.sp, cor = Cores.TextoTerciario)
                }
                if (pendentes.isNotEmpty()) Fio()
            }
            pendentes.forEachIndexed { i, r ->
                Row(Modifier.fillMaxWidth().toqueLinha { aoValidar(r) }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Texto(r.titulo, Tipo.linha)
                        Texto("${s.jogador(r.userId)?.nome} · ${r.criadoEm.instanteOuNull()?.relativo(agora) ?: ""}", Tipo.subtitulo)
                    }
                    Texto("+${r.pontosFinais}", Tipo.numero)
                }
                if (i < pendentes.lastIndex) Fio()
            }
        }
    }
    Texto("NOTIFICAÇÕES", Tipo.secao, Modifier.padding(top = 6.dp))
    if (s.notificacoes.isEmpty()) Texto("Nada por aqui ainda. As viradas, bombas e pedidos de validação aparecem nesta lista.", Tipo.pequenoSecundario)
    Column {
        s.notificacoes.take(25).forEachIndexed { i, n ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 6.dp, end = 10.dp).size(7.dp)) {
                    if (n.lidaEm == null) Ponto(Cores.jogador(s.eu?.cor))
                }
                Column(Modifier.weight(1f)) {
                    Texto(n.titulo, Tipo.pequeno.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
                    Texto(n.corpo, Tipo.pequenoSecundario, linhas = 3)
                }
                Texto(n.criadaEm.instanteOuNull()?.relativo(agora) ?: "", Tipo.subtitulo, Modifier.padding(start = 8.dp))
            }
            if (i < minOf(s.notificacoes.size, 25) - 1) Fio()
        }
    }
}

@Composable
fun ColumnScope.FolhaMeta(s: CasaSnapshot, vm: AcoesJogo, ocupado: Boolean, aoFechar: () -> Unit) {
    var alvo by remember { mutableStateOf((s.temporada?.metaAlvo ?: 2000).toString()) }
    var premio by remember { mutableStateOf(s.temporada?.metaPremio.orEmpty()) }
    CabecalhoFolha("Meta da casa", aoFechar)
    Texto(
        "A soma dos pontos dos dois na semana. Bateu o alvo, os dois ganham o prêmio coletivo.",
        Tipo.pequenoSecundario,
        Modifier.fillMaxWidth(),
        alinhamento = TextAlign.Center,
    )
    CampoTexto(premio, { premio = it.take(60) }, "Prêmio (ex.: pizza de sábado)", sublinhado = true)
    CampoTexto(alvo, { alvo = it.filter(Char::isDigit).take(6) }, "Alvo em pontos", sublinhado = true, teclado = KeyboardType.Number)
    BotaoCapsula(
        "Salvar meta",
        { vm.definirMeta(alvo.toIntOrNull() ?: 0, premio, aoFechar) },
        habilitado = (alvo.toIntOrNull() ?: 0) >= 100,
        carregando = ocupado,
    )
}

@Composable
fun ColumnScope.FolhaFiltro(s: CasaSnapshot, atual: String?, aoEscolher: (String?) -> Unit, aoFechar: () -> Unit) {
    CabecalhoFolha("Categorias", aoFechar)
    val categorias = listOf<String?>(null) + s.tarefas.map { it.categoria }.distinct().sorted()
    Column {
        categorias.forEachIndexed { i, c ->
            Row(Modifier.fillMaxWidth().toqueLinha { aoEscolher(c) }.padding(vertical = 12.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Texto(c ?: "Todas", Tipo.linha, Modifier.weight(1f))
                if (c == atual) Simbolo("check", tamanho = 18.sp)
            }
            if (i < categorias.lastIndex) Fio()
        }
    }
}

/** Proposta de troca recebida: aceitar ou recusar (só 1 recusa por semana). */
@Composable
fun ColumnScope.FolhaTroca(tr: Troca, s: CasaSnapshot, vm: AcoesJogo, ocupado: Boolean, aoFechar: () -> Unit) {
    val quem = s.jogador(tr.propostaPor)?.nome ?: "A outra pessoa"
    val recebo = s.tarefas.firstOrNull { it.id == tr.tarefaMinha }
    val entrego = s.tarefas.firstOrNull { it.id == tr.tarefaDele }
    val podeRecusar = vm.recusasNaSemana() == 0
    CabecalhoFolha("$quem quer trocar", aoFechar)
    Column {
        LinhaExtrato("Você recebe", "${recebo?.titulo ?: "?"} · ${recebo?.pontosVale() ?: 0}")
        LinhaExtrato("Você entrega", "${entrego?.titulo ?: "?"} · ${entrego?.pontosVale() ?: 0}", ultima = true)
    }
    Texto("Sem resposta em 12h, a troca acontece sozinha.", Tipo.subtitulo, Modifier.fillMaxWidth(), alinhamento = TextAlign.Center)
    BotaoCapsula("Aceitar troca", { vm.responderTroca(tr, true, aoFechar) }, carregando = ocupado)
    BotaoTexto(
        if (podeRecusar) "Recusar (1 por semana)" else "Você já recusou uma troca nesta semana",
        { vm.responderTroca(tr, false, aoFechar) },
        Modifier.align(Alignment.CenterHorizontally),
        habilitado = podeRecusar && !ocupado,
    )
}

/** Usar uma carta da mão, escolhendo o alvo quando o efeito pede. */
@Composable
fun ColumnScope.FolhaUsarCarta(carta: Carta, s: CasaSnapshot, agora: Instant, vm: AcoesJogo, ocupado: Boolean, aoFechar: () -> Unit) {
    val tipo = TipoCarta.de(carta.tipo) ?: return
    var alvo by remember(carta.id) { mutableStateOf<String?>(null) }
    var alvo2 by remember(carta.id) { mutableStateOf<String?>(null) }
    val haptico = LocalHapticFeedback.current
    val usar = { vm.usarCarta(carta, alvo, alvo2) { haptico.performHapticFeedback(HapticFeedbackType.Confirm); aoFechar() } }

    CabecalhoFolha(tipo.nome, aoFechar)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Simbolo(tipo.icone, tamanho = 32.sp, cor = if (tipo.epica) Cores.Ouro else Cores.Texto)
    }
    Texto(tipo.efeito, Tipo.pequenoSecundario, Modifier.fillMaxWidth(), alinhamento = TextAlign.Center)

    @Composable
    fun Escolha(titulo: String, opcoes: List<Tarefa>, selecionada: String?, aoEscolher: (String) -> Unit) {
        Texto(titulo.uppercase(), Tipo.secao, Modifier.padding(top = 4.dp))
        if (opcoes.isEmpty()) {
            Texto("Nenhuma tarefa serve agora.", Tipo.pequenoSecundario)
        }
        Column {
            opcoes.forEachIndexed { i, t ->
                Row(Modifier.fillMaxWidth().toqueLinha { aoEscolher(t.id) }.padding(vertical = 10.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Texto(t.titulo, Tipo.linha, Modifier.weight(1f), linhas = 1)
                    Texto(t.pontosVale().pt(), Tipo.numero, Modifier.padding(horizontal = 10.dp))
                    Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                        if (t.id == selecionada) Simbolo("check", tamanho = 18.sp)
                    }
                }
                if (i < opcoes.lastIndex) Fio()
            }
        }
    }

    when (tipo) {
        TipoCarta.Holofote -> {
            Escolha("Qual tarefa vira bomba", s.tarefas.filter { it.status != "aguardando" }.sortedByDescending { it.pontosVale() }, alvo) { alvo = it }
            BotaoCapsula("Acender o holofote", usar, habilitado = alvo != null, carregando = ocupado)
        }
        TipoCarta.Escudo -> {
            Escolha("Qual reserva proteger", s.tarefas.filter { it.status == "reservada" && it.reservadaPor == s.meuId }, alvo) { alvo = it }
            BotaoCapsula("Usar Escudo", usar, habilitado = alvo != null, carregando = ocupado)
        }
        TipoCarta.Troca -> {
            Escolha("Você entrega", s.tarefas.filter { it.status == "reservada" && it.reservadaPor == s.meuId }, alvo) { alvo = it }
            Escolha("Você pega de ${s.outro?.nome ?: "quem mora com você"}", s.tarefas.filter { it.status == "reservada" && it.reservadaPor == s.outro?.userId }, alvo2) { alvo2 = it }
            BotaoCapsula("Propor troca", usar, habilitado = alvo != null && alvo2 != null, carregando = ocupado)
        }
        TipoCarta.Veto -> {
            val vetada = alvoDoVeto(s, agora)
            Texto(
                vetada?.let { "A tarefa mais odiada agora é ${it.titulo} (chatice ${it.chatice.decimal()}). Você não pega ela até o fim da temporada." }
                    ?: "Não há tarefa para vetar agora.",
                Tipo.pequeno,
                Modifier.fillMaxWidth(),
                alinhamento = TextAlign.Center,
            )
            BotaoCapsula("Vetar", usar, habilitado = vetada != null, carregando = ocupado, estilo = EstiloBotao.Ouro)
        }
        TipoCarta.HoraFeliz -> BotaoCapsula("Começar a Hora Feliz", usar, carregando = ocupado, estilo = EstiloBotao.Ouro)
        TipoCarta.Dobradinha -> BotaoCapsula(
            if (s.eu?.dobradinha == true) "Dobradinha já armada" else "Armar Dobradinha",
            usar,
            habilitado = s.eu?.dobradinha != true,
            carregando = ocupado,
        )
    }
}
