package com.rivaisdolar.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rivaisdolar.app.data.local.Preferencias
import com.rivaisdolar.app.data.remote.Atualizacao
import com.rivaisdolar.app.data.remote.AtualizacaoRepository
import com.rivaisdolar.app.data.remote.AuthRepository
import com.rivaisdolar.app.data.remote.CasaRepository
import com.rivaisdolar.app.data.remote.EstadoAuth
import com.rivaisdolar.app.data.remote.EstadoCasa
import com.rivaisdolar.app.data.remote.mensagem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface Raiz {
    data object Carregando : Raiz
    data object SemServidor : Raiz
    data object Deslogado : Raiz
    data object SemCasa : Raiz
    data object Pronto : Raiz
    data class Falha(val mensagem: String) : Raiz
}

@HiltViewModel
class RaizViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val casa: CasaRepository,
    private val atualizacoes: AtualizacaoRepository,
    private val prefs: Preferencias,
) : ViewModel() {

    val raiz: StateFlow<Raiz> = combine(auth.estado, casa.estado) { a, c ->
        when (a) {
            EstadoAuth.SemServidor -> Raiz.SemServidor
            EstadoAuth.Carregando -> Raiz.Carregando
            EstadoAuth.Deslogado -> Raiz.Deslogado
            is EstadoAuth.Logado -> when (c) {
                EstadoCasa.Carregando -> Raiz.Carregando
                EstadoCasa.SemCasa -> Raiz.SemCasa
                is EstadoCasa.Pronta -> Raiz.Pronto
                is EstadoCasa.Falha -> Raiz.Falha(c.mensagem)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Raiz.Carregando)

    /** Cor do jogador logado, que tinge a ação principal do app todo. */
    val cor: StateFlow<String?> = casa.estado
        .map { (it as? EstadoCasa.Pronta)?.snapshot?.eu?.cor }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _oferta = MutableStateFlow<Atualizacao?>(null)
    val oferta: StateFlow<Atualizacao?> = _oferta.asStateFlow()

    private val _progresso = MutableStateFlow<Float?>(null)
    val progresso: StateFlow<Float?> = _progresso.asStateFlow()

    private val _aviso = MutableStateFlow<String?>(null)
    val aviso: StateFlow<String?> = _aviso.asStateFlow()

    init {
        viewModelScope.launch {
            auth.estado.collect { a ->
                if (a is EstadoAuth.Logado) casa.iniciar(a.userId) else casa.parar()
            }
        }
        procurarAtualizacao(manual = false)
    }

    fun tentarDeNovo() = casa.atualizar()

    fun sair() {
        viewModelScope.launch {
            casa.esquecerTudo()
            auth.sair()
        }
    }

    fun limparAviso() {
        _aviso.value = null
    }

    fun procurarAtualizacao(manual: Boolean) {
        viewModelScope.launch {
            try {
                val nova = atualizacoes.verificar()
                when {
                    nova == null -> if (manual) _aviso.value = "Você já está na versão mais nova."
                    !manual && nova.versao == prefs.atualizacaoIgnorada -> Unit
                    else -> _oferta.value = nova
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (manual) _aviso.value = "Não deu pra procurar atualização: ${e.mensagem()}"
            }
        }
    }

    fun ignorarAtualizacao() {
        _oferta.value?.let { prefs.atualizacaoIgnorada = it.versao }
        _oferta.value = null
    }

    fun instalarAtualizacao() {
        val nova = _oferta.value ?: return
        if (_progresso.value != null) return
        if (!atualizacoes.podeInstalar()) {
            _aviso.value = "Permita instalar apps do Rivais do Lar e toque de novo."
            atualizacoes.abrirPermissaoDeInstalar()
            return
        }
        viewModelScope.launch {
            _progresso.value = 0f
            try {
                val apk = atualizacoes.baixar(nova) { _progresso.value = it }
                atualizacoes.instalar(apk)
                _oferta.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _aviso.value = "O download falhou: ${e.mensagem()}"
            } finally {
                _progresso.value = null
            }
        }
    }
}
