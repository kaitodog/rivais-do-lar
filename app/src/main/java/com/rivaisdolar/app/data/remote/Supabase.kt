package com.rivaisdolar.app.data.remote

import com.rivaisdolar.app.data.local.ConfigServidor
import com.rivaisdolar.app.data.local.Preferencias
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

val JsonRivais = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

/** O cliente só existe depois que há um servidor configurado (BuildConfig ou tela de servidor). */
@Singleton
class SupabaseProvider @Inject constructor(private val prefs: Preferencias) {
    private val _cliente = MutableStateFlow(prefs.servidor()?.let(::criar))
    val cliente: StateFlow<SupabaseClient?> = _cliente.asStateFlow()

    fun exigir(): SupabaseClient = _cliente.value ?: throw IllegalStateException("Servidor não configurado.")

    fun conectar(config: ConfigServidor) {
        prefs.salvarServidor(config)
        _cliente.value = criar(config)
    }

    fun desconectar() {
        prefs.salvarServidor(null)
        _cliente.value = null
    }

    private fun criar(config: ConfigServidor): SupabaseClient =
        createSupabaseClient(config.url, config.chaveAnon) {
            defaultSerializer = KotlinXSerializer(JsonRivais)
            install(Auth)
            install(Postgrest)
            install(Realtime)
            install(Storage)
            install(Functions)
        }
}

fun Throwable.semConexao(): Boolean =
    this is HttpRequestException || this is HttpRequestTimeoutException ||
        this is IOException || cause?.let { it !== this && it.semConexao() } == true

/** Mensagem pronta para a interface, em português. */
fun Throwable.mensagem(): String = when {
    this is AuthRestException -> when (error) {
        "invalid_credentials" -> "E-mail ou senha incorretos."
        "user_already_exists", "email_exists" -> "Já existe uma conta com esse e-mail."
        "weak_password" -> "Senha fraca: use pelo menos 6 caracteres."
        "email_not_confirmed" -> "Confirme seu e-mail antes de entrar."
        "otp_expired" -> "Código inválido ou expirado."
        "same_password" -> "A nova senha precisa ser diferente da anterior."
        "over_email_send_rate_limit", "over_request_rate_limit" -> "Muitas tentativas. Espere um pouco e tente de novo."
        "validation_failed", "email_address_invalid" -> "Confira o e-mail digitado."
        else -> errorDescription.ifBlank { "Não deu certo ($error)." }
    }
    // Erros das RPCs (RAISE ... P0001) já vêm em português do servidor.
    this is PostgrestRestException && code == "P0001" -> error
    this is PostgrestRestException && code == "42501" -> "Sem permissão para essa ação."
    this is RestException -> "O servidor recusou: $error"
    semConexao() -> "Sem conexão com o servidor."
    else -> message ?: "Algo deu errado."
}
