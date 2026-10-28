package com.rivaisdolar.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Espelham as tabelas do Supabase (supabase/migrations). Só leitura: escrita de jogo é por RPC.

@Serializable
data class Profile(
    val id: String,
    val nome: String,
)

@Serializable
data class Casa(
    val id: String,
    val nome: String,
    @SerialName("codigo_convite") val codigoConvite: String,
    val fuso: String = "America/Sao_Paulo",
    @SerialName("modo_tregua") val modoTregua: Boolean = false,
)

@Serializable
data class Membro(
    @SerialName("casa_id") val casaId: String,
    @SerialName("user_id") val userId: String,
    val cor: String,
    val nivel: Int = 1,
    val xp: Int = 0,
    val moedas: Int = 0,
    @SerialName("sequencia_dias") val sequenciaDias: Int = 0,
    @SerialName("nivel_provocacao") val nivelProvocacao: String = "moderada",
)

@Serializable
data class Tarefa(
    val id: String,
    @SerialName("casa_id") val casaId: String,
    val titulo: String,
    val categoria: String,
    val icone: String,
    val minutos: Int,
    val esforco: Int,
    val chatice: Double,
    val recorrencia: String,
    val prazo: String? = null,
    val status: String = "livre",
    @SerialName("reservada_por") val reservadaPor: String? = null,
    @SerialName("reservada_em") val reservadaEm: String? = null,
    @SerialName("dias_parada") val diasParada: Int = 0,
    val ativa: Boolean = true,
)

@Serializable
data class Temporada(
    val id: String,
    @SerialName("casa_id") val casaId: String,
    val numero: Int,
    val inicio: String,
    val fim: String,
    val placar: Map<String, Int> = emptyMap(),
    val aposta: String? = null,
    @SerialName("meta_alvo") val metaAlvo: Int = 2000,
    @SerialName("meta_atual") val metaAtual: Int = 0,
    @SerialName("meta_premio") val metaPremio: String? = null,
    val vencedor: String? = null,
    @SerialName("premio_resgatado") val premioResgatado: Boolean = false,
    val encerrada: Boolean = false,
)

@Serializable
data class Multiplicador(
    val tipo: String,
    val rotulo: String,
    val valor: Double,
)

@Serializable
data class Registro(
    val id: String,
    @SerialName("casa_id") val casaId: String,
    @SerialName("tarefa_id") val tarefaId: String,
    @SerialName("user_id") val userId: String,
    val titulo: String,
    @SerialName("pontos_base") val pontosBase: Int,
    val multiplicadores: List<Multiplicador> = emptyList(),
    @SerialName("pontos_finais") val pontosFinais: Int,
    val moedas: Int,
    val status: String,
    val comentario: String? = null,
    @SerialName("contestado_por") val contestadoPor: String? = null,
    @SerialName("contestado_em") val contestadoEm: String? = null,
    @SerialName("validado_por") val validadoPor: String? = null,
    @SerialName("criado_em") val criadoEm: String,
)

/** Campos que o app pode gravar direto em `tarefas` (os grants do servidor só liberam estes). */
@Serializable
data class TarefaCadastro(
    val titulo: String,
    val categoria: String,
    val icone: String,
    val minutos: Int,
    val esforco: Int,
    val chatice: Double,
    val recorrencia: String,
    val prazo: String?,
)

@Serializable
data class TarefaNova(
    @SerialName("casa_id") val casaId: String,
    val titulo: String,
    val categoria: String,
    val icone: String,
    val minutos: Int,
    val esforco: Int,
    val chatice: Double,
    val recorrencia: String,
    val prazo: String?,
)

object Recorrencias {
    val todas = listOf(
        "avulsa" to "Uma vez",
        "diaria" to "Diária",
        "3x_semana" to "3x semana",
        "2x_semana" to "2x semana",
        "semanal" to "Semanal",
        "quinzenal" to "Quinzenal",
        "mensal" to "Mensal",
    )

    fun rotulo(chave: String) = todas.firstOrNull { it.first == chave }?.second ?: chave
}

object Esforcos {
    val rotulos = listOf("Leve", "Médio", "Pesado")
    fun rotulo(e: Int) = rotulos.getOrElse(e) { "Médio" }
}
