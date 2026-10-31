package com.rivaisdolar.app.ui.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Refração na borda do vidro (AGSL, Android 13+). Perto da borda, cada pixel amostra o conteúdo
 * um pouco para dentro, como uma lente grossa: o fundo borrado "dobra" na beirada. No miolo fica
 * igual. A forma é um retângulo arredondado de raio [raioPx] (cápsulas usam metade da altura).
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private const val LENTE = """
uniform shader conteudo;
uniform float2 tamanho;
uniform float raio;
uniform float borda;

half4 main(float2 p) {
    float2 meio = tamanho * 0.5;
    float r = min(raio, min(meio.x, meio.y));
    float2 q = abs(p - meio) - (meio - r);
    float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
    float t = clamp(1.0 + d / borda, 0.0, 1.0);
    float2 normal = normalize(p - meio + float2(0.0001, 0.0001));
    float2 amostra = p - normal * (t * t * t) * borda * 0.55;
    half4 cor = conteudo.eval(amostra);
    return half4(cor.rgb * (1.0 + 0.10 * t), cor.a);
}
"""

private class CacheLente {
    private var shader: RuntimeShader? = null
    private var efeito: RenderEffect? = null
    private var largura = -1f
    private var altura = -1f
    private var raio = -1f

    fun efeito(largura: Float, altura: Float, raio: Float, borda: Float): RenderEffect {
        val atual = shader
        if (atual == null || this.largura != largura || this.altura != altura || this.raio != raio) {
            val novo = RuntimeShader(LENTE).apply {
                setFloatUniform("tamanho", largura, altura)
                setFloatUniform("raio", raio)
            }
            shader = novo
            this.largura = largura
            this.altura = altura
            this.raio = raio
            efeito = android.graphics.RenderEffect.createRuntimeShaderEffect(novo, "conteudo").asComposeRenderEffect()
        }
        // O pulso muda só o uniform: o shader e o RenderEffect são reaproveitados.
        shader?.setFloatUniform("borda", borda)
        return efeito!!
    }
}

/**
 * Aplica a lente. [bordaPx] é lida a cada quadro para o pulso de refração animar sem recompor.
 * O shader é cacheado por tamanho/raio; antes ele era recriado a cada frame.
 */
@Composable
fun Modifier.lenteDeVidro(
    raioPx: (larguraPx: Float, alturaPx: Float) -> Float,
    bordaPx: () -> Float,
): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
    val cache = remember { CacheLente() }
    return this.graphicsLayer {
        val raio = raioPx(size.width, size.height)
        renderEffect = cache.efeito(size.width, size.height, raio, bordaPx())
        clip = true
    }
}
