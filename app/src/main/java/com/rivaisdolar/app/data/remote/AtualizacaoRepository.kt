package com.rivaisdolar.app.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.rivaisdolar.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.contentLength
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class ReleaseGitHub(
    @SerialName("tag_name") val tag: String,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<AssetGitHub> = emptyList(),
)

@Serializable
private data class AssetGitHub(
    val name: String,
    @SerialName("browser_download_url") val url: String,
    val size: Long = 0,
)

data class Atualizacao(val versao: String, val notas: String, val urlApk: String, val tamanho: Long)

/** O "bot" de distribuição: lê o último GitHub Release e instala o APK novo. */
@Singleton
class AtualizacaoRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val http = HttpClient(OkHttp) {
        expectSuccess = true
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 60_000
        }
    }

    suspend fun verificar(): Atualizacao? {
        val repo = BuildConfig.UPDATE_REPO.ifBlank { return null }
        val resposta = http.get("https://api.github.com/repos/$repo/releases/latest") {
            header("Accept", "application/vnd.github+json")
        }.bodyAsText()
        val release = JsonRivais.decodeFromString(ReleaseGitHub.serializer(), resposta)
        if (release.draft || release.prerelease) return null
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk") } ?: return null
        val versao = release.tag.removePrefix("v")
        if (!versaoMaior(versao, BuildConfig.VERSION_NAME)) return null
        return Atualizacao(versao, release.body.orEmpty().trim(), apk.url, apk.size)
    }

    suspend fun baixar(atualizacao: Atualizacao, progresso: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val pasta = File(context.cacheDir, "atualizacoes").apply { mkdirs() }
        pasta.listFiles()?.forEach { it.delete() }
        val destino = File(pasta, "rivais-do-lar-${atualizacao.versao}.apk")
        http.prepareGet(atualizacao.urlApk).execute { resposta ->
            val total = resposta.contentLength() ?: atualizacao.tamanho
            resposta.bodyAsChannel().toInputStream().use { entrada ->
                destino.outputStream().use { saida ->
                    val buffer = ByteArray(64 * 1024)
                    var lidos = 0L
                    while (true) {
                        val n = entrada.read(buffer)
                        if (n < 0) break
                        saida.write(buffer, 0, n)
                        lidos += n
                        if (total > 0) progresso((lidos.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        destino
    }

    fun podeInstalar(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun abrirPermissaoDeInstalar() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun instalar(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.arquivos", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    companion object {
        /** "0.10.0" > "0.9.3"; partes não numéricas (ex.: "-beta") são ignoradas. */
        fun versaoMaior(nova: String, atual: String): Boolean {
            fun partes(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val a = partes(nova)
            val b = partes(atual)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
