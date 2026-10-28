package com.rivaisdolar.app.ui.principal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rivaisdolar.app.data.local.Preferencias
import com.rivaisdolar.app.data.model.Carta
import com.rivaisdolar.app.data.model.Previa
import com.rivaisdolar.app.data.model.Registro
import com.rivaisdolar.app.data.model.TipoCarta
import com.rivaisdolar.app.data.model.Troca
import com.rivaisdolar.app.push.Push
import com.rivaisdolar.app.data.model.Tarefa
import com.rivaisdolar.app.data.model.TarefaCadastro
import com.rivaisdolar.app.data.model.Temporada
import com.rivaisdolar.app.data.remote.AuthRepository
import com.rivaisdolar.app.data.remote.CasaRepository
import com.rivaisdolar.app.data.remote.CasaSnapshot
import com.rivaisdolar.app.data.remote.EstadoCasa
import com.rivaisdolar.app.data.remote.Resultado
import com.rivaisdolar.app.domain.instanteOuNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Ações que os sheets disparam; separado do ViewModel para as telas poderem ser testadas sozinhas. */
interface AcoesJogo {
    fun reservar(t: Tarefa, ok: () -> Unit = {})
    fun liberar(t: Tarefa, ok: () -> Unit = {})
    fun concluir(t: Tarefa, foto: ByteArray? = null, ok: () -> Unit = {})
    suspend fun previa(t: Tarefa): Previa?
    suspend fun foto(caminho: String): ByteArray?
    fun removerTarefa(id: String, ok: () -> Unit = {})
    fun comprar(tipo: TipoCarta, ok: () -> Unit = {})
    fun usarCarta(carta: Carta, alvo: String? = null, alvo2: String? = null, ok: () -> Unit = {})
    fun responderTroca(troca: Troca, aceitar: Boolean, ok: () -> Unit = {})
    fun recusasNaSemana(): Int
    fun marcarLidas()
    fun validar(r: Registro, ok: () -> Unit = {})
    fun contestar(r: Registro, comentario: String, ok: () -> Unit = {})
    fun definirMeta(alvo: Int, premio: String, ok: () -> Unit = {})
    fun contestacoesRestantes(): Int
}

/** Estado e ações do jogo, compartilhado pelas telas da casa (placar, tarefas, casa, editor). */
@HiltViewModel
class JogoViewModel @Inject constructor(
    private val repo: CasaRepository,
    private val auth: AuthRepository,
    private val prefs: Preferencias,
) : ViewModel(), AcoesJogo {

    val snapshot: StateFlow<CasaSnapshot?> = repo.estado
        .map { (it as? EstadoCasa.Pronta)?.snapshot }
        .stateIn(viewModelScope, SharingStarted.Eagerly, (repo.estado.value as? EstadoCasa.Pronta)?.snapshot)

    val offline: StateFlow<Boolean> = repo.estado
        .map { (it as? EstadoCasa.Pronta)?.offline == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val naFila: StateFlow<Int> = repo.acoesNaFila.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _aviso = MutableStateFlow<String?>(null)
    val aviso: StateFlow<String?> = _aviso.asStateFlow()

    private val _ocupado = MutableStateFlow(false)
    val ocupado: StateFlow<Boolean> = _ocupado.asStateFlow()

    init {
        viewModelScope.launch { repo.avisos.collect { _aviso.value = it } }
        // Registra este aparelho para receber push (se o APK veio com o Firebase configurado).
        prefs.tokenPush?.let { viewModelScope.launch { repo.salvarTokenPush(it) } }
        Push.registrar()
    }

    fun avisar(texto: String) {
        _aviso.value = texto
    }

    fun limparAviso() {
        _aviso.value = null
    }

    fun atualizar() = repo.atualizar()

    val email: String? get() = auth.emailAtual()

    private fun executar(sucesso: String?, aoConcluir: () -> Unit = {}, acao: suspend () -> Resultado) {
        if (_ocupado.value) return
        viewModelScope.launch {
            _ocupado.value = true
            when (val r = acao()) {
                Resultado.Ok -> {
                    sucesso?.let { _aviso.value = it }
                    aoConcluir()
                }
                Resultado.NaFila -> {
                    _aviso.value = "Sem conexão: vai ser enviado quando a internet voltar."
                    aoConcluir()
                }
                is Resultado.Erro -> _aviso.value = r.mensagem
            }
            _ocupado.value = false
        }
    }

    private val outroNome get() = snapshot.value?.outro?.nome ?: "A outra pessoa"

    override fun reservar(t: Tarefa, ok: () -> Unit) = executar("Reservada. É sua pelas próximas 24h.", ok) { repo.reservar(t) }
    override fun liberar(t: Tarefa, ok: () -> Unit) = executar("Tarefa de volta pra fila.", ok) { repo.liberar(t) }
    override fun concluir(t: Tarefa, foto: ByteArray?, ok: () -> Unit) =
        executar("Enviado. $outroNome confirma com um toque.", ok) { repo.concluir(t, foto) }

    override suspend fun previa(t: Tarefa): Previa? = repo.previa(t)
    override suspend fun foto(caminho: String): ByteArray? = repo.baixarFoto(caminho)

    override fun removerTarefa(id: String, ok: () -> Unit) = executar("Tarefa removida. O histórico de pontos continua.", ok) { repo.arquivarTarefa(id) }

    override fun comprar(tipo: TipoCarta, ok: () -> Unit) = executar("${tipo.nome} foi para a sua mão.", ok) { repo.comprarCarta(tipo.codigo) }

    override fun usarCarta(carta: Carta, alvo: String?, alvo2: String?, ok: () -> Unit) {
        val nome = TipoCarta.de(carta.tipo)?.nome ?: "Carta"
        val aviso = if (carta.tipo == "troca") "Troca proposta para $outroNome." else "$nome usada."
        executar(aviso, ok) { repo.usarCarta(carta, alvo, alvo2) }
    }

    override fun responderTroca(troca: Troca, aceitar: Boolean, ok: () -> Unit) =
        executar(if (aceitar) "Troca aceita." else "Troca recusada.", ok) { repo.responderTroca(troca, aceitar) }

    /** Recusas de troca que eu já usei nesta temporada (o limite é 1). */
    override fun recusasNaSemana(): Int {
        val s = snapshot.value ?: return 0
        val inicio = s.temporada?.inicio?.instanteOuNull() ?: return 0
        return s.trocas.count { it.para == s.meuId && it.status == "recusada" && it.respondidaEm?.instanteOuNull()?.isBefore(inicio) == false }
    }

    override fun marcarLidas() {
        if ((snapshot.value?.naoLidas ?: 0) == 0) return
        viewModelScope.launch { repo.marcarNotificacoesLidas() }
    }

    suspend fun historico() = repo.historico()
    override fun validar(r: Registro, ok: () -> Unit) = executar("Validado. Os pontos entraram no placar.", ok) { repo.validar(r) }
    override fun contestar(r: Registro, comentario: String, ok: () -> Unit) =
        executar("Contestado. A tarefa voltou pra fila.", ok) { repo.contestar(r, comentario) }

    fun definirAposta(texto: String, ok: () -> Unit = {}) = executar("Aposta definida.", ok) { repo.definirAposta(texto) }
    override fun definirMeta(alvo: Int, premio: String, ok: () -> Unit) = executar("Meta da casa atualizada.", ok) { repo.definirMeta(alvo, premio) }
    fun resgatar(t: Temporada, ok: () -> Unit = {}) = executar("Prêmio resgatado. Aproveite!", ok) { repo.resgatarPremio(t) }

    fun salvarTarefa(id: String?, cadastro: TarefaCadastro, ok: () -> Unit) =
        executar(if (id == null) "Tarefa criada." else "Tarefa salva.", ok) { repo.salvarTarefa(id, cadastro) }


    fun atualizarNome(nome: String, ok: () -> Unit = {}) = executar("Nome atualizado.", ok) { repo.atualizarNome(nome) }
    fun atualizarNomeCasa(nome: String, ok: () -> Unit = {}) = executar("Casa renomeada.", ok) { repo.atualizarCasa(nome = nome) }
    fun definirTregua(ligada: Boolean) =
        executar(if (ligada) "Modo trégua ligado." else "Modo trégua desligado.") { repo.atualizarCasa(tregua = ligada) }

    fun definirProvocacao(nivel: String) = executar(null) { repo.atualizarProvocacao(nivel) }
    fun definirCor(cor: String) = executar("Cor trocada.") { repo.definirCor(cor) }
    fun sairDaCasa(ok: () -> Unit = {}) = executar("Você saiu da casa.", ok) { repo.sairDaCasa() }

    fun sairDaConta() {
        viewModelScope.launch {
            repo.esquecerTudo()
            auth.sair()
        }
    }

    /** Temporada encerrada que este aparelho ainda não mostrou (abre a tela de fim de temporada). */
    fun temporadaNaoVista(): Temporada? {
        val t = snapshot.value?.ultimaEncerrada ?: return null
        if (t.id == prefs.temporadaVista) return null
        // Só comemora o que fechou nos últimos 7 dias; temporadas antigas não pulam na cara.
        val fim = t.fim.instanteOuNull() ?: return null
        if (fim.isBefore(java.time.Instant.now().minus(java.time.Duration.ofDays(7)))) return null
        return t
    }

    fun marcarVista(t: Temporada) {
        prefs.temporadaVista = t.id
    }

    /** Contestações que ainda posso usar na temporada atual (máximo 2). */
    override fun contestacoesRestantes(): Int {
        val s = snapshot.value ?: return 2
        val inicio = s.temporada?.inicio?.instanteOuNull() ?: return 2
        val usadas = s.registros.count {
            it.contestadoPor == s.meuId && it.contestadoEm?.instanteOuNull()?.isBefore(inicio) == false
        }
        return (2 - usadas).coerceAtLeast(0)
    }
}
