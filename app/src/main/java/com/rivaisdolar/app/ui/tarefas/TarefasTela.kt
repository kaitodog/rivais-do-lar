package com.rivaisdolar.app.ui.tarefas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rivaisdolar.app.data.model.Tarefa
import com.rivaisdolar.app.data.remote.CasaSnapshot
import com.rivaisdolar.app.ui.components.BotaoCirculo
import com.rivaisdolar.app.ui.components.CampoTexto
import com.rivaisdolar.app.ui.components.CartaoSolido
import com.rivaisdolar.app.ui.components.Divisoria
import com.rivaisdolar.app.ui.components.Grupo
import com.rivaisdolar.app.ui.components.Secao
import com.rivaisdolar.app.ui.components.Segmentado
import com.rivaisdolar.app.ui.components.Texto
import com.rivaisdolar.app.ui.components.Vazio
import com.rivaisdolar.app.ui.placar.LinhaTarefa
import com.rivaisdolar.app.ui.principal.bomba
import com.rivaisdolar.app.ui.principal.pontosVale
import com.rivaisdolar.app.ui.theme.Cores
import com.rivaisdolar.app.ui.theme.Tipo
import java.text.Normalizer
import java.time.Instant

private fun String.semAcento(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

@Composable
fun TarefasTela(
    s: CasaSnapshot,
    agora: Instant,
    lista: LazyListState,
    categoria: String?,
    aoFiltrar: () -> Unit,
    aoAbrirTarefa: (Tarefa) -> Unit,
) {
    var segmento by rememberSaveable { mutableIntStateOf(0) }
    var buscando by rememberSaveable { mutableStateOf(false) }
    var busca by rememberSaveable { mutableStateOf("") }
    val outro = s.outro

    val filtradas = s.tarefas.filter { t ->
        (categoria == null || t.categoria == categoria) &&
            (busca.isBlank() || t.titulo.semAcento().contains(busca.semAcento()) || t.categoria.semAcento().contains(busca.semAcento()))
    }
    val ordem = compareByDescending<Tarefa> { it.bomba(agora) }.thenByDescending { it.pontosVale() }

    // Livres: o que dá pra pegar (inclui reservas do outro, que podem ser roubadas).
    val secoes: List<Pair<String, List<Tarefa>>> = when (segmento) {
        0 -> listOf(
            "Reservadas" to filtradas.filter { it.status == "reservada" && it.reservadaPor != s.meuId }.sortedWith(ordem),
            "Livres" to filtradas.filter { it.status == "livre" }.sortedWith(ordem),
        )
        1 -> listOf(
            "Reservadas por você" to filtradas.filter { it.status == "reservada" && it.reservadaPor == s.meuId }.sortedWith(ordem),
            "Aguardando validação" to filtradas.filter { it.status == "aguardando" && it.reservadaPor == s.meuId }.sortedWith(ordem),
        )
        else -> listOf(
            "Reservadas" to filtradas.filter { it.status == "reservada" && it.reservadaPor == outro?.userId }.sortedWith(ordem),
            "Aguardando sua validação" to filtradas.filter { it.status == "aguardando" && it.reservadaPor == outro?.userId }.sortedWith(ordem),
        )
    }.filter { it.second.isNotEmpty() }

    LazyColumn(
        state = lista,
        modifier = Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().height(38.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                BotaoCirculo("search", "Buscar", {
                    buscando = !buscando
                    if (!buscando) busca = ""
                })
                BotaoCirculo("tune", "Filtrar por categoria", aoFiltrar, marcador = if (categoria != null) Cores.Texto else null)
            }
        }
        item { Texto("Tarefas", Tipo.tituloGrande) }
        item {
            AnimatedVisibility(buscando) {
                CampoTexto(busca, { busca = it }, "Buscar tarefa ou categoria")
            }
        }
        item {
            Segmentado(listOf("Livres", "Minhas", outro?.nome ?: "Outro"), segmento, { segmento = it })
        }
        if (categoria != null) {
            item { Texto("Mostrando só $categoria", Tipo.subtitulo, Modifier.padding(horizontal = 16.dp)) }
        }
        if (secoes.isEmpty()) {
            item {
                CartaoSolido {
                    when (segmento) {
                        0 -> Vazio("celebration", "Nada livre por aqui", "Tudo reservado ou feito. Toque em + para criar uma tarefa.")
                        1 -> Vazio("back_hand", "Você não pegou nada", "Reserve uma tarefa na aba Livres para ela ficar sua.")
                        else -> Vazio("hourglass_empty", "${outro?.nome ?: "A outra pessoa"} não pegou nada", "Quando reservar algo, aparece aqui.")
                    }
                }
            }
        }
        secoes.forEach { (titulo, tarefas) ->
            item(key = "s-$titulo") { Secao(titulo, Modifier.padding(top = 4.dp)) }
            item(key = "g-$titulo") {
                Grupo {
                    tarefas.forEachIndexed { i, t ->
                        if (i > 0) Divisoria()
                        LinhaTarefa(t, s, agora, doPlacar = false, aoTocar = { aoAbrirTarefa(t) })
                    }
                }
            }
        }
    }
}
