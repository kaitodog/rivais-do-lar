package com.rivaisdolar.app.ui.tarefas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivaisdolar.app.data.model.Esforcos
import com.rivaisdolar.app.data.model.Recorrencias
import com.rivaisdolar.app.data.model.Registro
import com.rivaisdolar.app.data.model.Tarefa
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
import com.rivaisdolar.app.ui.components.IconeTarefa
import com.rivaisdolar.app.ui.components.Simbolo
import com.rivaisdolar.app.ui.components.Texto
import com.rivaisdolar.app.ui.principal.AcoesJogo
import com.rivaisdolar.app.ui.principal.bomba
import com.rivaisdolar.app.ui.principal.legenda
import com.rivaisdolar.app.ui.principal.pontosVale
import com.rivaisdolar.app.ui.principal.roubavelEm
import com.rivaisdolar.app.ui.theme.Cores
import com.rivaisdolar.app.ui.theme.Tipo
import java.time.Instant

/** Linha de extrato dentro de um sheet: só texto e divisória (nada de vidro sobre vidro). */
@Composable
private fun LinhaExtrato(rotulo: String, valor: String, ultima: Boolean = false) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Texto(rotulo, Tipo.pequenoSecundario.copy(fontSize = 13.5.sp), Modifier.weight(1f))
            Texto(valor, Tipo.numero.copy(fontSize = 13.5.sp))
        }
        if (!ultima) Box(Modifier.fillMaxWidth().height(0.5.dp).background(Cores.Separador))
    }
}

private fun mult(valor: Double) = "× ${valor.decimal()}"

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

/** Detalhe e ações da tarefa (reservar, concluir, largar, editar). */
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

    CabecalhoFolha(t.titulo, aoFechar)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconeTarefa(t.icone, bomba = t.bomba(agora) && t.status != "aguardando")
        Column(Modifier.weight(1f)) {
            Texto("${t.categoria} · ${t.minutos} min · ${Esforcos.rotulo(t.esforco)}", Tipo.pequeno)
            Texto(leg.texto, Tipo.subtitulo, cor = leg.cor)
        }
    }
    Column {
        LinhaExtrato("Base · ${t.minutos} min × ${Pontuacao.multiplicadorEsforco(t.esforco).toDouble().decimal()} × ${t.chatice.decimal()}", Pontuacao.pontos(t.minutos, t.esforco, t.chatice).pt())
        if (t.diasParada > 0) LinhaExtrato("Bomba · parada há ${plural(t.diasParada, "dia", "dias")}", mult(Pontuacao.bonusBomba(t.diasParada).toDouble()))
        LinhaExtrato("Repete", Recorrencias.rotulo(t.recorrencia), ultima = true)
    }
    Total(t.pontosVale(), Pontuacao.moedas(t.pontosVale()), "Vale")

    when {
        t.status == "livre" -> {
            BotaoCapsula("Reservar", { vm.reservar(t) { haptico.performHapticFeedback(HapticFeedbackType.Confirm); aoFechar() } }, carregando = ocupado)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                BotaoTexto("Já fiz agora", aoConcluir)
                BotaoTexto("Editar", aoEditar)
            }
        }
        t.status == "reservada" && minha -> {
            BotaoCapsula("Concluir", aoConcluir)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                BotaoTexto("Largar tarefa", { vm.liberar(t, aoFechar) }, habilitado = !ocupado)
                BotaoTexto("Editar", aoEditar)
            }
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
            BotaoTexto("Editar", aoEditar, Modifier.align(Alignment.CenterHorizontally))
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
}

/** Concluir tarefa: foto do depois, extrato da conta, total e envio para validação. */
@Composable
fun ColumnScope.FolhaConcluir(
    t: Tarefa,
    s: CasaSnapshot,
    vm: AcoesJogo,
    ocupado: Boolean,
    aoFechar: () -> Unit,
) {
    val haptico = LocalHapticFeedback.current
    val pontos = t.pontosVale()
    CabecalhoFolha(t.titulo, aoFechar)
    Box(
        Modifier
            .fillMaxWidth()
            .height(118.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF2C2C30), Color(0xFF1C1C1E)))),
        contentAlignment = Alignment.Center,
    ) {
        Simbolo("image", tamanho = 30.sp, cor = Color(0x59EBEBF5))
        Row(Modifier.align(Alignment.BottomStart).padding(start = 11.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Simbolo("photo_camera", tamanho = 13.sp, cor = Color(0xA6EBEBF5))
            Texto(" Foto do depois chega na próxima versão", Tipo.selo.copy(fontSize = 11.sp), cor = Color(0xA6EBEBF5))
        }
    }
    Column {
        val base = Pontuacao.pontos(t.minutos, t.esforco, t.chatice)
        LinhaExtrato(
            "Base · ${t.minutos} min × ${Pontuacao.multiplicadorEsforco(t.esforco).toDouble().decimal()} × ${t.chatice.decimal()}",
            base.pt(),
            ultima = t.diasParada == 0,
        )
        if (t.diasParada > 0) {
            LinhaExtrato("Bomba · parada há ${plural(t.diasParada, "dia", "dias")}", mult(Pontuacao.bonusBomba(t.diasParada).toDouble()), ultima = true)
        }
    }
    Total(pontos, Pontuacao.moedas(pontos), "Você ganha")
    BotaoCapsula(
        "Enviar pra validar",
        {
            vm.concluir(t) {
                haptico.performHapticFeedback(HapticFeedbackType.Confirm)
                aoFechar()
            }
        },
        carregando = ocupado,
    )
    Texto(
        "${s.outro?.nome ?: "A outra pessoa"} confirma com um toque. Sem resposta em 12h, vale sozinho.",
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

    CabecalhoFolha(r.titulo, aoFechar)
    Texto(
        "$autor concluiu ${r.criadoEm.instanteOuNull()?.relativo(agora) ?: ""}. Confere se ficou feito?",
        Tipo.pequenoSecundario,
        Modifier.fillMaxWidth(),
        alinhamento = TextAlign.Center,
    )
    Column {
        LinhaExtrato("Base", r.pontosBase.pt(), ultima = r.multiplicadores.isEmpty())
        r.multiplicadores.forEachIndexed { i, m -> LinhaExtrato(m.rotulo, mult(m.valor), ultima = i == r.multiplicadores.lastIndex) }
    }
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

/** Pedidos de validação e o que aconteceu por último. */
@Composable
fun ColumnScope.FolhaAtividade(s: CasaSnapshot, agora: Instant, aoFechar: () -> Unit, aoValidar: (Registro) -> Unit) {
    CabecalhoFolha("Atividade", aoFechar)
    val pendentes = s.pendentesParaMim
    Texto("PRA VOCÊ VALIDAR", Tipo.secao)
    if (pendentes.isEmpty()) {
        Texto("Nada esperando por você.", Tipo.pequenoSecundario)
    } else {
        Column {
            pendentes.forEachIndexed { i, r ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Texto(r.titulo, Tipo.linha)
                        Texto("${s.jogador(r.userId)?.nome} · ${r.criadoEm.instanteOuNull()?.relativo(agora) ?: ""}", Tipo.subtitulo)
                    }
                    BotaoTexto("+${r.pontosFinais}", { aoValidar(r) })
                }
                if (i < pendentes.lastIndex) Box(Modifier.fillMaxWidth().height(0.5.dp).background(Cores.Separador))
            }
        }
    }
    Texto("ÚLTIMOS REGISTROS", Tipo.secao, Modifier.padding(top = 6.dp))
    val recentes = s.registros.filter { it.status != "pendente" }.take(12)
    if (recentes.isEmpty()) Texto("Ninguém concluiu nada ainda. Bora?", Tipo.pequenoSecundario)
    Column {
        recentes.forEachIndexed { i, r ->
            val nome = s.jogador(r.userId)?.nome ?: "Alguém"
            val texto = when (r.status) {
                "validado" -> "$nome fez ${r.titulo}"
                else -> "${s.jogador(r.contestadoPor)?.nome ?: "Alguém"} contestou ${r.titulo}" +
                    (r.comentario?.let { ": \"$it\"" } ?: "")
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Texto(texto, Tipo.pequeno, linhas = 2)
                    Texto(r.criadoEm.instanteOuNull()?.relativo(agora) ?: "", Tipo.subtitulo)
                }
                Texto(if (r.status == "validado") "+${r.pontosFinais}" else "0", Tipo.numero, cor = if (r.status == "validado") Cores.Texto else Cores.TextoTerciario)
            }
            if (i < recentes.lastIndex) Box(Modifier.fillMaxWidth().height(0.5.dp).background(Cores.Separador))
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
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BotaoTexto(c ?: "Todas", { aoEscolher(c) }, Modifier.weight(1f))
                if (c == atual) Simbolo("check", tamanho = 18.sp)
            }
            if (i < categorias.lastIndex) Box(Modifier.fillMaxWidth().height(0.5.dp).background(Cores.Separador))
        }
    }
}
