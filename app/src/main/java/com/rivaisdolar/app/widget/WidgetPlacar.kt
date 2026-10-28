package com.rivaisdolar.app.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.rivaisdolar.app.MainActivity
import com.rivaisdolar.app.data.remote.CasaSnapshot
import com.rivaisdolar.app.data.remote.JsonRivais
import com.rivaisdolar.app.domain.pt
import kotlinx.serialization.Serializable

@Serializable
data class LadoWidget(val nome: String, val cor: String, val pontos: Int)

@Serializable
data class DadosWidget(val temporada: Int, val lados: List<LadoWidget>, val tregua: Boolean)

/** Widget do placar na tela inicial. Lê a última fotografia que o app gravou. */
object WidgetPlacar {
    private const val PREFS = "widget_placar"

    fun dados(s: CasaSnapshot) = DadosWidget(
        temporada = s.temporada?.numero ?: 1,
        lados = s.jogadores.map { LadoWidget(it.nome, it.cor, s.pontos(it.userId)) },
        tregua = s.casa.modoTregua,
    )

    suspend fun atualizar(context: Context, s: CasaSnapshot) {
        val json = JsonRivais.encodeToString(DadosWidget.serializer(), dados(s))
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString("dados", null) == json) return
        prefs.edit { putString("dados", json) }
        runCatching { PlacarWidget().updateAll(context) }
    }

    suspend fun limpar(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { clear() }
        runCatching { PlacarWidget().updateAll(context) }
    }

    fun ler(context: Context): DadosWidget? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("dados", null)
            ?.let { runCatching { JsonRivais.decodeFromString(DadosWidget.serializer(), it) }.getOrNull() }
}

private val Branco = ColorProvider(Color.White)
private val Secundario = ColorProvider(Color(0x99EBEBF5))
private fun corJogador(cor: String) = Color(if (cor == "laranja") 0xFFFF9F0A else 0xFF0A84FF)

class PlacarWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dados = WidgetPlacar.ler(context)
        provideContent { Conteudo(dados) }
    }

    @androidx.compose.runtime.Composable
    private fun Conteudo(dados: DadosWidget?) {
        Column(
            GlanceModifier.fillMaxSize().cornerRadius(22.dp).background(Color(0xFF1C1C1E)).padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            if (dados == null || dados.lados.isEmpty()) {
                Text("Rivais do Lar", style = TextStyle(color = Branco, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                Text("Abra o app para ver o placar.", style = TextStyle(color = Secundario, fontSize = 12.sp))
                return@Column
            }
            Text(
                if (dados.tregua) "Temporada ${dados.temporada} · trégua" else "Temporada ${dados.temporada}",
                style = TextStyle(color = Secundario, fontSize = 12.sp),
            )
            Spacer(GlanceModifier.height(8.dp))
            val lider = dados.lados.maxByOrNull { it.pontos }?.takeIf { l -> dados.lados.count { it.pontos == l.pontos } == 1 }
            dados.lados.forEach { lado ->
                Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(GlanceModifier.size(10.dp).cornerRadius(5.dp).background(corJogador(lado.cor))) {}
                    Spacer(GlanceModifier.width(8.dp))
                    Text(
                        lado.nome + if (lado == lider) "  na frente" else "",
                        style = TextStyle(color = Branco, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    Text(lado.pontos.pt(), style = TextStyle(color = Branco, fontSize = 22.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

class PlacarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PlacarWidget()
}
