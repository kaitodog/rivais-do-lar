package com.rivaisdolar.app.ui.tarefas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rivaisdolar.app.data.model.Esforcos
import com.rivaisdolar.app.data.model.Recorrencias
import com.rivaisdolar.app.data.model.TarefaCadastro
import com.rivaisdolar.app.domain.Pontuacao
import com.rivaisdolar.app.domain.decimal
import com.rivaisdolar.app.domain.instanteOuNull
import com.rivaisdolar.app.domain.pt
import com.rivaisdolar.app.ui.components.Aviso
import com.rivaisdolar.app.ui.components.BotaoCapsula
import com.rivaisdolar.app.ui.components.BotaoCirculo
import com.rivaisdolar.app.ui.components.BotaoTexto
import com.rivaisdolar.app.ui.components.CampoTexto
import com.rivaisdolar.app.ui.components.CartaoSolido
import com.rivaisdolar.app.ui.components.Deslizador
import com.rivaisdolar.app.ui.components.Fichas
import com.rivaisdolar.app.ui.components.FormaIcone
import com.rivaisdolar.app.ui.components.Secao
import com.rivaisdolar.app.ui.components.Segmentado
import com.rivaisdolar.app.ui.components.Simbolo
import com.rivaisdolar.app.ui.components.Texto
import com.rivaisdolar.app.ui.components.toque
import com.rivaisdolar.app.ui.principal.JogoViewModel
import com.rivaisdolar.app.ui.theme.Cores
import com.rivaisdolar.app.ui.theme.LocalDestaque
import com.rivaisdolar.app.ui.theme.Tipo
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val categoriasPadrao = listOf("Cozinha", "Limpeza", "Pesada", "Rápida", "Lavanderia", "Pets", "Rua", "Carga mental", "Crianças", "Contas", "Manutenção")

// Agrupados por assunto (6 por linha no seletor).
private val icones = listOf(
    "restaurant", "kitchen", "cooking", "soup_kitchen", "countertops", "local_dining",
    "dishwasher_gen", "microwave", "coffee_maker", "blender", "cleaning_services", "mop",
    "vacuum", "bathtub", "shower", "wc", "wash", "sanitizer",
    "delete", "delete_sweep", "recycling", "compost", "local_laundry_service", "laundry",
    "checkroom", "dry_cleaning", "iron", "styler", "dry", "bed",
    "king_bed", "window", "blinds", "chair", "weekend", "lightbulb",
    "pets", "cruelty_free", "potted_plant", "yard", "grass", "water_drop",
    "shopping_cart", "shopping_basket", "grocery", "local_shipping", "package_2", "mail",
    "edit_note", "receipt_long", "payments", "credit_card", "savings", "calendar_month",
    "child_care", "child_friendly", "toys", "backpack", "school", "medication",
    "plumbing", "build", "handyman", "format_paint", "directions_car", "local_car_wash",
)

private enum class Prazo(val rotulo: String, val dias: Long?) {
    Manter("Manter", null), Sem("Sem prazo", null), Hoje("Hoje", 0), Amanha("Amanhã", 1), TresDias("Em 3 dias", 3), Semana("Em 1 semana", 7)
}

@Composable
fun EditorTarefaTela(vm: JogoViewModel, tarefaId: String?, aoFechar: () -> Unit) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val ocupado by vm.ocupado.collectAsStateWithLifecycle()
    val aviso by vm.aviso.collectAsStateWithLifecycle()
    val s = snapshot ?: return
    val original = tarefaId?.let { id -> s.tarefas.firstOrNull { it.id == id } }
    val zona = runCatching { ZoneId.of(s.casa.fuso) }.getOrDefault(ZoneId.systemDefault())

    var titulo by rememberSaveable { mutableStateOf(original?.titulo.orEmpty()) }
    var categoria by rememberSaveable { mutableStateOf(original?.categoria ?: "Limpeza") }
    var categoriaPropria by rememberSaveable { mutableStateOf(false) }
    var removendo by rememberSaveable { mutableStateOf(false) }
    // Categorias padrão + as que a casa já inventou.
    val categorias = (categoriasPadrao + s.tarefas.map { it.categoria } + listOfNotNull(original?.categoria)).distinct()
    var icone by rememberSaveable { mutableStateOf(original?.icone ?: "cleaning_services") }
    var minutos by rememberSaveable { mutableFloatStateOf((original?.minutos ?: 20).toFloat()) }
    var esforco by rememberSaveable { mutableIntStateOf(original?.esforco ?: 1) }
    var chatice by rememberSaveable { mutableFloatStateOf((original?.chatice ?: 1.2).toFloat()) }
    var recorrencia by rememberSaveable { mutableStateOf(original?.recorrencia ?: "semanal") }
    val temPrazo = original?.prazo != null
    var prazo by rememberSaveable { mutableStateOf(if (temPrazo) Prazo.Manter else Prazo.Sem) }

    val chat = Math.round(chatice * 10) / 10.0
    val pontos = Pontuacao.pontos(minutos.toInt(), esforco, chat)
    val opcoesPrazo = Prazo.entries.filter { it != Prazo.Manter || temPrazo }
    val rotuloManter = original?.prazo?.instanteOuNull()?.atZone(zona)?.format(DateTimeFormatter.ofPattern("dd/MM"))

    Box(Modifier.fillMaxSize().background(Cores.Fundo)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp).height(38.dp)) { BotaoCirculo("close", "Fechar", aoFechar) }
            Texto(if (original == null) "Nova tarefa" else "Editar tarefa", Tipo.tituloGrande)

            CampoTexto(titulo, { titulo = it.take(60) }, "Nome da tarefa (ex.: Lavar a louça)")

            Secao("Vale")
            CartaoSolido {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Texto("${minutos.toInt()} min × ${Pontuacao.multiplicadorEsforco(esforco).toDouble().decimal()} × ${chat.decimal()}", Tipo.pequenoSecundario)
                        Texto("${pontos.pt()} pontos", Tipo.placar)
                    }
                    Texto("${Pontuacao.moedas(pontos)} moedas", Tipo.pequenoSecundario)
                }
            }

            Secao("Tempo estimado · ${minutos.toInt()} min")
            CartaoSolido { Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { Deslizador(minutos, 5f..180f, 5f, { minutos = it }) } }

            Secao("Esforço físico")
            Segmentado(Esforcos.rotulos, esforco, { esforco = it })

            Secao("Chatice · × ${chat.decimal()}")
            CartaoSolido { Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { Deslizador(chatice, 1f..1.6f, 0.1f, { chatice = it }) } }

            Secao("Categoria")
            Fichas(
                categorias + "Outra…",
                if (categoriaPropria) categorias.size else categorias.indexOf(categoria),
                {
                    if (it == categorias.size) {
                        categoriaPropria = true
                        categoria = ""
                    } else {
                        categoriaPropria = false
                        categoria = categorias[it]
                    }
                },
            )
            if (categoriaPropria) {
                CampoTexto(categoria, { categoria = it.take(30) }, "Nome da categoria (ex.: Horta, Carro, Bebê)")
            }

            Secao("Ícone")
            CartaoSolido {
                FlowRow(
                    Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 6,
                ) {
                    icones.forEach { nome ->
                        val ativo = nome == icone
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(FormaIcone)
                                .background(if (ativo) LocalDestaque.current.copy(alpha = 0.22f) else Cores.Preenchimento)
                                .then(if (ativo) Modifier.border(1.5.dp, LocalDestaque.current, FormaIcone) else Modifier)
                                .toque { icone = nome },
                            contentAlignment = Alignment.Center,
                        ) { Simbolo(nome, tamanho = 22.sp, cor = if (ativo) LocalDestaque.current else Cores.Texto) }
                    }
                }
            }

            Secao("Repete")
            Fichas(Recorrencias.todas.map { it.second }, Recorrencias.todas.indexOfFirst { it.first == recorrencia }, { recorrencia = Recorrencias.todas[it].first })

            Secao("Prazo")
            Fichas(
                opcoesPrazo.map { if (it == Prazo.Manter) "Manter ($rotuloManter)" else it.rotulo },
                opcoesPrazo.indexOf(prazo),
                { prazo = opcoesPrazo[it] },
            )
            Texto("Passou do prazo, vira bomba: +10% por dia parada, até dobrar.", Tipo.subtitulo, Modifier.padding(horizontal = 16.dp))

            Spacer(Modifier.height(4.dp))
            BotaoCapsula(
                "Salvar",
                {
                    val novoPrazo = when (prazo) {
                        Prazo.Manter -> original?.prazo
                        Prazo.Sem -> null
                        else -> LocalDate.now(zona).plusDays(prazo.dias ?: 0).atTime(23, 59).atZone(zona).toOffsetDateTime().toString()
                    }
                    vm.salvarTarefa(
                        original?.id,
                        TarefaCadastro(titulo.trim(), categoria.trim(), icone, minutos.toInt(), esforco, chat, recorrencia, novoPrazo),
                        aoFechar,
                    )
                },
                habilitado = titulo.isNotBlank() && categoria.isNotBlank(),
                carregando = ocupado,
            )
            if (original != null && removendo) {
                CartaoSolido {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Texto("Remover \"${original.titulo}\"?", Tipo.tituloCartao)
                        Texto("Ela sai da lista da casa. Os pontos que já valeram continuam no placar e no histórico.", Tipo.pequenoSecundario)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            BotaoTexto("Cancelar", { removendo = false }, cor = Cores.TextoSecundario)
                            BotaoTexto("Remover", { vm.removerTarefa(original.id, aoFechar) }, habilitado = !ocupado)
                        }
                    }
                }
            } else if (original != null) {
                BotaoTexto("Remover tarefa", { removendo = true }, Modifier.align(Alignment.CenterHorizontally), cor = Cores.TextoSecundario)
                if (original.status != "livre") {
                    Texto(
                        "Ela está reservada ou esperando validação; mudanças valem para a próxima vez.",
                        Tipo.subtitulo,
                        Modifier.fillMaxWidth(),
                        alinhamento = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        Aviso(aviso, vm::limparAviso, Modifier.align(Alignment.TopCenter))
    }
}

