package com.rivaisdolar.app.data.local

import android.content.Context
import androidx.core.content.edit
import com.rivaisdolar.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class ConfigServidor(val url: String, val chaveAnon: String)

/** Preferências pequenas do aparelho (não sincronizadas). */
@Singleton
class Preferencias @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("rivais", Context.MODE_PRIVATE)

    /**
     * BuildConfig vem de local.properties / secrets do CI. Se o APK foi gerado sem eles, a pessoa
     * informa o servidor na primeira abertura.
     */
    fun servidor(): ConfigServidor? {
        val url = prefs.getString("servidor_url", null) ?: BuildConfig.SUPABASE_URL
        val chave = prefs.getString("servidor_chave", null) ?: BuildConfig.SUPABASE_ANON_KEY
        return if (url.isBlank() || chave.isBlank()) null else ConfigServidor(url.trim(), chave.trim())
    }

    fun salvarServidor(config: ConfigServidor?) = prefs.edit {
        if (config == null) {
            remove("servidor_url")
            remove("servidor_chave")
        } else {
            putString("servidor_url", config.url)
            putString("servidor_chave", config.chaveAnon)
        }
    }

    val servidorEmbutido: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    /** Último usuário logado: permite abrir o app offline quando o refresh do token falha. */
    var ultimoUsuario: String?
        get() = prefs.getString("ultimo_usuario", null)
        set(value) = prefs.edit { putString("ultimo_usuario", value) }

    var temporadaVista: String?
        get() = prefs.getString("temporada_vista", null)
        set(value) = prefs.edit { putString("temporada_vista", value) }

    var atualizacaoIgnorada: String?
        get() = prefs.getString("atualizacao_ignorada", null)
        set(value) = prefs.edit { putString("atualizacao_ignorada", value) }
}
