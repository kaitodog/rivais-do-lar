package com.rivaisdolar.app.ui.cartas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivaisdolar.app.data.remote.CasaSnapshot
import com.rivaisdolar.app.domain.pt
import com.rivaisdolar.app.ui.components.CartaoSolido
import com.rivaisdolar.app.ui.components.Divisoria
import com.rivaisdolar.app.ui.components.Grupo
import com.rivaisdolar.app.ui.components.Linha
import com.rivaisdolar.app.ui.components.Secao
import com.rivaisdolar.app.ui.components.Simbolo
import com.rivaisdolar.app.ui.components.Texto
import com.rivaisdolar.app.ui.theme.Cores
import com.rivaisdolar.app.ui.theme.Tipo

private data class Carta(val nome: String, val raridade: String, val custo: Int, val icone: String, val efeito: String, val epica: Boolean = false)

private val baralho = listOf(
    Carta("Holofote", "Comum", 40, "e911_emergency", "Transforma uma tarefa em bomba agora."),
    Carta("Escudo", "Comum", 50, "shield", "Sua reserva não pode ser roubada por 24h."),
    Carta("Troca", "Rara", 60, "swap_horiz", "Troca uma tarefa sua por uma do outro."),
    Carta("Dobradinha", "Rara", 80, "library_add", "A próxima tarefa vale o dobro."),
    Carta("Hora Feliz", "Épica", 90, "hourglass_empty", "Por 1 hora, tudo vale x1,5.", epica = true),
    Carta("Veto", "Épica", 120, "block", "Você não pega a tarefa mais odiada da semana.", epica = true),
)

/** Fase 3. Por enquanto mostra o baralho e as moedas guardadas. */
@Composable
fun CartasTela(s: CasaSnapshot, lista: LazyListState) {
    LazyColumn(
        state = lista,
        modifier = Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 50.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Texto("Cartas", Tipo.tituloGrande) }
        item {
            CartaoSolido {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Simbolo("playing_cards", tamanho = 36.sp, cor = Cores.TextoSecundario)
                    Spacer(Modifier.height(10.dp))
                    Texto("Em breve", Tipo.tituloGrande.copy(fontSize = 22.sp))
                    Spacer(Modifier.height(4.dp))
                    Texto(
                        "A loja de cartas abre na próxima fase. Suas moedas já estão guardando.",
                        Tipo.pequenoSecundario,
                        alinhamento = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Simbolo("toll", tamanho = 18.sp, cor = Cores.Texto)
                        Texto("  ${(s.eu?.moedas ?: 0).pt()} moedas", Tipo.numero)
                    }
                }
            }
        }
        item { Secao("O baralho", Modifier.padding(top = 4.dp)) }
        item {
            Grupo {
                baralho.forEachIndexed { i, c ->
                    if (i > 0) Divisoria()
                    Linha(
                        titulo = c.nome,
                        subtitulo = "${c.raridade} · ${c.efeito}",
                        inicio = {
                            androidx.compose.foundation.layout.Box(
                                Modifier.size(32.dp).clip(RoundedCornerShape(9.dp))
                                    .background(if (c.epica) Cores.Ouro.copy(alpha = 0.16f) else Cores.Preenchimento),
                                contentAlignment = Alignment.Center,
                            ) { Simbolo(c.icone, tamanho = 19.sp, cor = if (c.epica) Cores.Ouro else Cores.Texto) }
                        },
                        fim = { Texto(c.custo.toString(), Tipo.numero) },
                    )
                }
            }
        }
    }
}
