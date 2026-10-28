package com.rivaisdolar.app.data.remote

import com.rivaisdolar.app.data.local.Preferencias
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

sealed interface EstadoAuth {
    data object SemServidor : EstadoAuth
    data object Carregando : EstadoAuth
    data object Deslogado : EstadoAuth
    data class Logado(val userId: String, val email: String?) : EstadoAuth
}

@Singleton
class AuthRepository @Inject constructor(
    private val provider: SupabaseProvider,
    private val prefs: Preferencias,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val estado: Flow<EstadoAuth> = provider.cliente.flatMapLatest { cliente ->
        if (cliente == null) {
            flowOf(EstadoAuth.SemServidor)
        } else {
            cliente.auth.sessionStatus.map { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        val user = status.session.user
                        val id = user?.id ?: prefs.ultimoUsuario
                        if (id == null) {
                            EstadoAuth.Deslogado
                        } else {
                            prefs.ultimoUsuario = id
                            EstadoAuth.Logado(id, user?.email)
                        }
                    }
                    SessionStatus.Initializing -> EstadoAuth.Carregando
                    // Sem rede o token não renova; seguimos com a sessão salva e o cache local.
                    is SessionStatus.RefreshFailure ->
                        prefs.ultimoUsuario?.let { EstadoAuth.Logado(it, null) } ?: EstadoAuth.Deslogado
                    is SessionStatus.NotAuthenticated -> EstadoAuth.Deslogado
                }
            }
        }
    }.distinctUntilChanged()

    private val auth get() = provider.exigir().auth

    fun emailAtual(): String? = provider.cliente.value?.auth?.currentUserOrNull()?.email

    suspend fun entrar(email: String, senha: String) {
        auth.signInWith(Email) {
            this.email = email.trim()
            password = senha
        }
    }

    /** @return true quando o projeto exige confirmar o e-mail antes da primeira sessão. */
    suspend fun cadastrar(nome: String, email: String, senha: String): Boolean {
        auth.signUpWith(Email) {
            this.email = email.trim()
            password = senha
            data = buildJsonObject { put("nome", nome.trim()) }
        }
        return auth.currentSessionOrNull() == null
    }

    suspend fun confirmarCadastro(email: String, codigo: String) {
        auth.verifyEmailOtp(OtpType.Email.EMAIL, email.trim(), codigo.trim())
    }

    suspend fun reenviarConfirmacao(email: String) {
        auth.resendEmail(OtpType.Email.SIGNUP, email.trim())
    }

    suspend fun enviarRecuperacao(email: String) {
        auth.resetPasswordForEmail(email.trim())
    }

    suspend fun redefinirSenha(email: String, codigo: String, novaSenha: String) {
        auth.verifyEmailOtp(OtpType.Email.RECOVERY, email.trim(), codigo.trim())
        auth.updateUser { password = novaSenha }
    }

    suspend fun sair() {
        runCatching { auth.signOut() }
        prefs.ultimoUsuario = null
    }
}
