package com.rivaisdolar.app.data.model

/** Cartas de poder (custos e raridades iguais às funções carta_custo/carta_raridade do servidor). */
enum class TipoCarta(
    val codigo: String,
    val nome: String,
    val raridade: String,
    val custo: Int,
    val icone: String,
    val efeito: String,
) {
    Holofote("holofote", "Holofote", "Comum", 15, "e911_emergency", "Transforma uma tarefa em bomba agora."),
    Escudo("escudo", "Escudo", "Comum", 20, "shield", "Sua reserva não pode ser roubada por 24h."),
    Troca("troca", "Troca", "Rara", 25, "swap_horiz", "Troca uma reserva sua por uma da outra pessoa."),
    Dobradinha("dobradinha", "Dobradinha", "Rara", 40, "library_add", "A próxima tarefa vale o dobro."),
    HoraFeliz("hora_feliz", "Hora Feliz", "Épica", 45, "hourglass_empty", "Por 1 hora, tudo o que você fizer vale x1,5."),
    Veto("veto", "Veto", "Épica", 30, "block", "Você não pega a tarefa mais odiada da semana."),
    CafePassado("cafe_passado", "Café Passado", "Comum", 15, "coffee", "+25% na próxima tarefa de até 30 min (máx. +25)."),
    Seguro("seguro", "Seguro Antissacanagem", "Comum", 15, "shield", "Bloqueia o próximo ataque por 24h."),
    Balde("balde", "Balde de Água Fria", "Comum", 20, "water_drop", "−25% na próxima tarefa do rival (máx. −20)."),
    Cobranca("cobranca", "Cobrança de Aluguel", "Rara", 30, "request_quote", "Rouba 15 pontos; o rival pode cobrar de volta em 24h."),
    Castigo("castigo", "Castigo da Casa", "Comum", 20, "cleaning_services", "O rival tem 24h para cumprir ou perde 15 pontos."),
    Vale("vale", "Vale-favor", "Comum", 10, "redeem", "Deixa uma dívida de favor. Sem pontos, só a honra."),
    Pedagio("pedagio", "Pedágio Doméstico", "Rara", 25, "toll", "Rouba 10 pontos do rival agora."),
    MaoAtada("mao_atada", "Mão Atada", "Rara", 25, "pan_tool", "O rival fica 12h sem usar cartas ofensivas."),
    RouboCena("roubo_cena", "Roubo de Cena", "Rara", 20, "theater_comedy", "Marca uma tarefa livre: quem concluir em 24h leva +30."),
    Combo("combo", "Combo da Casa", "Comum", 20, "category", "+30 ao completar 3 categorias distintas em 24h."),
    Dobrou("dobrou", "Dobrou ou Danou", "Rara", 25, "casino", "Tarefa sua de 40+ pontos: +40 se concluir em 12h, −20 se falhar."),
    Arrastao("arrastao", "Arrastão do Sofá", "Decisiva", 0, "weekend", "Rouba até 30 pontos. Uma vez por temporada.");

    val epica: Boolean get() = raridade == "Épica"

    companion object {
        const val MAO_MAXIMA = 3
        fun de(codigo: String): TipoCarta? = entries.firstOrNull { it.codigo == codigo }
    }
}

data class InfoConquista(val codigo: String, val nome: String, val descricao: String, val icone: String)

/** Mesmos códigos de `_avaliar_conquistas` no servidor. */
object Conquistas {
    val todas = listOf(
        InfoConquista("primeira_faxina", "Primeira faxina", "Primeira tarefa validada.", "cleaning_services"),
        InfoConquista("rei_do_rejunte", "Rei do Rejunte", "10 banheiros limpos.", "bathtub"),
        InfoConquista("madrugador", "Madrugador", "Uma tarefa antes das 7h.", "wb_twilight"),
        InfoConquista("coruja", "Coruja", "Uma tarefa depois das 23h.", "bedtime"),
        InfoConquista("desarma_bombas", "Desarma-bombas", "5 tarefas bomba resolvidas.", "bomb"),
        InfoConquista("ladrao", "Ladrão de casaca", "3 tarefas roubadas.", "front_hand"),
        InfoConquista("em_chamas", "Em chamas", "7 dias seguidos de sequência.", "local_fire_department"),
        InfoConquista("mestre_cuca", "Mestre-cuca", "20 tarefas de cozinha.", "cooking"),
        InfoConquista("maratonista", "Maratonista", "Bateu o teto de 400 pontos num dia.", "directions_run"),
        InfoConquista("centuriao", "Centurião", "100 tarefas validadas.", "military_tech"),
        InfoConquista("campeao", "Campeão da semana", "Venceu uma temporada.", "emoji_events"),
        InfoConquista("tricampeao", "Tricampeão", "Venceu 3 temporadas.", "workspace_premium"),
        InfoConquista("veterano", "Veterano", "Chegou ao nível 5.", "star"),
    )

    fun info(codigo: String) = todas.firstOrNull { it.codigo == codigo }
}
