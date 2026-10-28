package com.rivaisdolar.app.ui.principal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import com.rivaisdolar.app.data.model.Tarefa
import com.rivaisdolar.app.data.model.Temporada
import com.rivaisdolar.app.data.remote.CasaSnapshot
import com.rivaisdolar.app.domain.Pontuacao
import com.rivaisdolar.app.domain.curta
import com.rivaisdolar.app.domain.instanteOuNull
import com.rivaisdolar.app.domain.plural
import com.rivaisdolar.app.ui.theme.Cores
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Relógio que avança a cada 30s, para cronômetros de roubo e prazos. */
@Composable
fun rememberAgora(): State<Instant> = produceState(Instant.now()) {
    while (true) {
        delay(30_000)
        value = Instant.now()
    }
}

fun Tarefa.pontosVale(): Int = Pontuacao.pontos(minutos, esforco, chatice, diasParada)

fun Tarefa.prazoInstante(): Instant? = prazo?.instanteOuNull()

fun Tarefa.bomba(agora: Instant): Boolean = diasParada > 0 || prazoInstante()?.isBefore(agora) == true

/** Reserva vale 24h; depois disso o servidor devolve a tarefa para a fila. */
fun Tarefa.roubavelEm(agora: Instant): Duration? {
    if (status != "reservada") return null
    val desde = reservadaEm?.instanteOuNull() ?: return null
    return Duration.between(agora, desde.plus(Duration.ofHours(24))).takeIf { !it.isNegative }
}

fun Tarefa.venceHoje(agora: Instant, fuso: String): Boolean {
    val p = prazoInstante() ?: return false
    val zona = runCatching { ZoneId.of(fuso) }.getOrDefault(ZoneId.systemDefault())
    return !p.isBefore(agora) && p.atZone(zona).toLocalDate() == agora.atZone(zona).toLocalDate()
}

data class Legenda(val texto: String, val cor: Color = Cores.TextoSecundario)

/** Subtítulo de uma tarefa na lista. [doPlacar] usa o formato curto da seção "Hoje". */
fun legenda(t: Tarefa, s: CasaSnapshot, agora: Instant, doPlacar: Boolean): Legenda {
    val outroNome = s.outro?.nome ?: "a outra pessoa"
    return when {
        t.status == "aguardando" -> {
            val reg = s.registroPendenteDa(t.id)
            if (reg?.userId == s.meuId) Legenda("Aguardando $outroNome validar")
            else Legenda("${s.jogador(reg?.userId)?.nome ?: outroNome} concluiu · toque pra validar")
        }
        t.bomba(agora) -> Legenda(
            if (t.diasParada > 0) "Bomba · parada há ${plural(t.diasParada, "dia", "dias")}" else "Atrasada · vira bomba amanhã",
            Cores.Alerta,
        )
        t.status == "reservada" && t.reservadaPor == s.meuId -> {
            val resta = t.roubavelEm(agora)
            Legenda(if (resta != null && resta < Duration.ofHours(6)) "Reservada por você · libera em ${resta.curta()}" else "Reservada por você")
        }
        t.status == "reservada" -> {
            val resta = t.roubavelEm(agora)
            if (doPlacar || resta == null) Legenda("Reservada por ${s.jogador(t.reservadaPor)?.nome ?: outroNome}")
            else Legenda("Roubável em ${resta.curta()}", Cores.Alerta)
        }
        else -> {
            val prefixo = if (doPlacar) "Livre" else t.categoria
            val vence = if (t.venceHoje(agora, s.casa.fuso)) " · vence hoje" else ""
            Legenda("$prefixo · ${t.minutos} min$vence")
        }
    }
}

/** Tarefas da seção "Hoje" do placar: bombas, reservas e o que vence até o fim do dia. */
fun tarefasDeHoje(s: CasaSnapshot, agora: Instant): List<Tarefa> {
    val zona = runCatching { ZoneId.of(s.casa.fuso) }.getOrDefault(ZoneId.systemDefault())
    val fimDoDia = agora.atZone(zona).toLocalDate().plusDays(1).atStartOfDay(zona).toInstant()
    return s.tarefas
        .filter { t ->
            t.status != "livre" || t.prazoInstante()?.isBefore(fimDoDia) == true || t.recorrencia == "diaria"
        }
        .sortedWith(
            compareByDescending<Tarefa> { it.bomba(agora) }
                .thenByDescending { it.diasParada }
                .thenByDescending { it.reservadaPor == s.meuId }
                .thenByDescending { it.status != "livre" }
                .thenByDescending { it.pontosVale() },
        )
        .take(8)
}

fun subtituloTemporada(t: Temporada?, agora: Instant): String {
    if (t == null) return "Primeira temporada começando"
    val fim = t.fim.instanteOuNull() ?: return "Temporada ${t.numero}"
    val falta = Duration.between(agora, fim)
    val quando = when {
        falta.isNegative -> "fechando agora"
        falta < Duration.ofHours(24) -> "fecha hoje às 22h"
        else -> "fecha domingo às 22h"
    }
    return "Temporada ${t.numero} · $quando"
}
