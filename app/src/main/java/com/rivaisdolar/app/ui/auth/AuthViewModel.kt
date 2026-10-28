package com.rivaisdolar.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rivaisdolar.app.data.local.ConfigServidor
import com.rivaisdolar.app.data.local.Preferencias
import com.rivaisdolar.app.data.remote.AuthRepository
import com.rivaisdolar.app.data.remote.CasaRepository
import com.rivaisdolar.app.data.remote.Resultado
import com.rivaisdolar.app.data.remote.SupabaseProvider
import com.rivaisdolar.app.data.remote.mensagem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUi(val carregando: Boolean = false, val erro: String? = null, val info: String? = null)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val casa: CasaRepository,
    private val provider: SupabaseProvider,
    private val prefs: Preferencias,
) : ViewModel() {
    private val _ui = MutableStateFlow(AuthUi())
    val ui: StateFlow<AuthUi> = _ui.asStateFlow()

    val servidorEmbutido: Boolean get() = prefs.servidorEmbutido
    val servidorAtual: String? get() = prefs.servidor()?.url

    fun limpar() = _ui.update { AuthUi() }

    private fun rodar(info: String? = null, aoConcluir: () -> Unit = {}, bloco: suspend () -> Unit) {
        if (_ui.value.carregando) return
        viewModelScope.launch {
            _ui.value = AuthUi(carregando = true)
            try {
                bloco()
                _ui.value = AuthUi(info = info)
                aoConcluir()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _ui.value = AuthUi(erro = e.mensagem())
            }
        }
    }

    fun entrar(email: String, senha: String) = rodar { auth.entrar(email, senha) }

    fun cadastrar(nome: String, email: String, senha: String, precisaConfirmar: () -> Unit) {
        if (senha.length < 6) {
            _ui.value = AuthUi(erro = "A senha precisa ter pelo menos 6 caracteres.")
            return
        }
        var confirmar = false
        rodar(aoConcluir = { if (confirmar) precisaConfirmar() }) { confirmar = auth.cadastrar(nome, email, senha) }
    }

    fun confirmar(email: String, codigo: String) = rodar { auth.confirmarCadastro(email, codigo) }

    fun reenviar(email: String) = rodar("Enviamos outro código.") { auth.reenviarConfirmacao(email) }

    fun enviarRecuperacao(email: String, ok: () -> Unit) =
        rodar("Se o e-mail tiver conta, chega um código em instantes.", ok) { auth.enviarRecuperacao(email) }

    fun redefinir(email: String, codigo: String, nova: String) {
        if (nova.length < 6) {
            _ui.value = AuthUi(erro = "A senha precisa ter pelo menos 6 caracteres.")
            return
        }
        rodar { auth.redefinirSenha(email, codigo, nova) }
    }

    fun conectar(url: String, chave: String) {
        val limpa = url.trim().trimEnd('/')
        if (!limpa.startsWith("https://") && !limpa.startsWith("http://")) {
            _ui.value = AuthUi(erro = "O endereço começa com https:// (ex.: https://abcd.supabase.co).")
            return
        }
        if (chave.trim().length < 20) {
            _ui.value = AuthUi(erro = "Cole a chave anon (pública) completa do projeto.")
            return
        }
        provider.conectar(ConfigServidor(limpa, chave.trim()))
        _ui.value = AuthUi()
    }

    fun trocarServidor() = provider.desconectar()

    // Sem casa ----------------------------------------------------------------------------------

    private fun rodarCasa(acao: suspend () -> Resultado) {
        if (_ui.value.carregando) return
        viewModelScope.launch {
            _ui.value = AuthUi(carregando = true)
            _ui.value = when (val r = acao()) {
                Resultado.Ok, Resultado.NaFila -> AuthUi()
                is Resultado.Erro -> AuthUi(erro = r.mensagem)
            }
        }
    }

    fun criarCasa(nome: String) = rodarCasa { casa.criarCasa(nome) }

    fun entrarCasa(codigo: String) = rodarCasa { casa.entrarCasa(codigo) }

    fun sair() {
        viewModelScope.launch {
            casa.esquecerTudo()
            auth.sair()
        }
    }
}
