package com.rivaisdolar.app.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.Modifier
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

fun Modifier.lenteDeVidro(raioPx: (larguraPx: Float, alturaPx: Float) -> Float, bordaPx: Float): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
    return this.graphicsLayer {
        val shader = RuntimeShader(LENTE)
        shader.setFloatUniform("tamanho", size.width, size.height)
        shader.setFloatUniform("raio", raioPx(size.width, size.height))
        shader.setFloatUniform("borda", bordaPx)
        renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "conteudo").asComposeRenderEffect()
        clip = true
    }
}
