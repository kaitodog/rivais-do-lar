package com.rivaisdolar.app.ui.theme

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.rivaisdolar.app.R

/** Tema preto único (seção 4 do plano). Não usamos Material: todos os componentes são próprios. */
object Cores {
    val Fundo = Color(0xFF000000)
    val Superficie = Color(0xFF1C1C1E)
    val SuperficieAlta = Color(0xFF2C2C2E)
    val Preenchimento = Color(0x3D787880) // rgba(120,120,128,.24)
    val Texto = Color(0xFFFFFFFF)
    val TextoSecundario = Color(0x99EBEBF5) // rgba(235,235,245,.6)
    val TextoTerciario = Color(0x4DEBEBF5) // rgba(235,235,245,.3)
    val Separador = Color(0x99545458) // rgba(84,84,88,.6)
    val Azul = Color(0xFF0A84FF)
    val Laranja = Color(0xFFFF9F0A)
    val Alerta = Color(0xFFFF453A) // só bomba / atraso
    val Ouro = Color(0xFFE6B422) // só recompensa
    val OuroBotao = Color(0xFFD6A628)

    val Vidro = Color(0x6B2C2C30) // rgba(44,44,48,.42)
    val VidroFallback = Color(0xEB2C2C2E) // #2C2C2E a 92%
    val VidroFolha = Color(0xB81E1E22) // rgba(30,30,34,.72)

    fun jogador(cor: String?): Color = if (cor == "laranja") Laranja else Azul
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold),
)

val Simbolos = FontFamily(Font(R.font.material_symbols_rounded))

object Tipo {
    private val base = TextStyle(fontFamily = Inter, color = Cores.Texto, letterSpacing = (-0.01).em)
    private const val TABULAR = "tnum"

    val tituloGrande = base.copy(fontSize = 31.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.035).em, lineHeight = 34.sp)
    val placar = base.copy(fontSize = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.05).em, lineHeight = 38.sp, fontFeatureSettings = TABULAR)
    val total = base.copy(fontSize = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.05).em, lineHeight = 42.sp, fontFeatureSettings = TABULAR)
    val vitoria = base.copy(fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.045).em, lineHeight = 36.sp)
    val linha = base.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.02).em, lineHeight = 19.sp)
    val subtitulo = base.copy(fontSize = 12.sp, color = Cores.TextoSecundario, lineHeight = 15.sp)
    val secao = base.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Cores.TextoSecundario, letterSpacing = 0.02.em)
    val corpo = base.copy(fontSize = 15.sp, lineHeight = 20.sp)
    val corpoSecundario = corpo.copy(color = Cores.TextoSecundario)
    val pequeno = base.copy(fontSize = 13.sp, lineHeight = 17.sp)
    val pequenoSecundario = pequeno.copy(color = Cores.TextoSecundario)
    val rotulo = base.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    val aba = base.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.em)
    val selo = base.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.em)
    val botao = base.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    val numero = base.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR)
    val numeroPequeno = base.copy(fontSize = 12.sp, color = Cores.TextoSecundario, fontFeatureSettings = TABULAR)
    val tituloFolha = base.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    val tituloCartao = base.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
}

/** Cor do jogador logado: botão "+", aba ativa e ação principal. */
val LocalDestaque = staticCompositionLocalOf { Cores.Azul }

/** Blur real só no Android 12+ e fora da economia de bateria. */
val LocalVidroReal = staticCompositionLocalOf { true }

/** "Remover animações" do sistema. */
val LocalReduzirMovimento = staticCompositionLocalOf { false }

private fun vidroDisponivel(context: Context): Boolean {
    val energia = context.getSystemService(PowerManager::class.java)
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && energia?.isPowerSaveMode != true
}

private fun movimentoReduzido(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

@Composable
fun RivaisTema(destaque: Color = Cores.Azul, conteudo: @Composable () -> Unit) {
    val context = LocalContext.current
    val vidro = remember { vidroDisponivel(context) }
    val reduzir = remember { movimentoReduzido(context) }
    CompositionLocalProvider(
        LocalDestaque provides destaque,
        LocalVidroReal provides vidro,
        LocalReduzirMovimento provides reduzir,
        content = conteudo,
    )
}
