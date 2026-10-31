package com.rivaisdolar.app.ui.juice

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.rivaisdolar.app.ui.theme.Cores
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sistema único de partículas do app. Um canvas por tela, sem um composable por partícula.
 * As coordenadas são em pixels da tela; o tamanho é informado pelo [CanvasParticulas].
 */
enum class TipoParticula { Fagulha, Confete, Moeda, Brasa, Cinza, Anel }

class Particula(
    val tipo: TipoParticula,
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var vida: Float = 0.6f,
    val vidaMax: Float = 0.6f,
    var tamanho: Float = 2f,
    var cor: Color = Color.White,
    var gravidade: Float = 0f,
    var arrasto: Float = 1f,
    var rotacao: Float = 0f,
    var giro: Float = 0f,
    var brilho: Boolean = false,
    // Moedas: caminho quadrático de p0 até p2 com controle p1.
    val p0: Offset = Offset.Zero,
    val p1: Offset = Offset.Zero,
    val p2: Offset = Offset.Zero,
    val duracao: Float = 0.7f,
    val atraso: Float = 0f,
) {
    var t: Float = -atraso
}

class SistemaParticulas {
    private val lista = ArrayList<Particula>(LIMITE)

    var largura: Float = 0f
        private set
    var altura: Float = 0f
        private set
    var densidade: Float = 1f
        private set

    /** Estado observável: liga/desliga o loop de frames. */
    var ativo by mutableStateOf(false)
        private set

    val quantidade: Int get() = lista.size

    fun definirTamanho(largura: Float, altura: Float, densidade: Float) {
        this.largura = largura
        this.altura = altura
        this.densidade = densidade
    }

    fun emitir(particula: Particula) {
        if (lista.size >= LIMITE) lista.removeAt(0)
        lista.add(particula)
        ativo = true
    }

    fun limpar() {
        lista.clear()
        ativo = false
    }

    /** Avança um quadro. Devolve false quando não sobrou nada para desenhar. */
    fun atualizar(dt: Float): Boolean {
        var i = lista.size - 1
        while (i >= 0) {
            val p = lista[i]
            if (p.tipo == TipoParticula.Moeda) {
                if (p.t < 0f) {
                    p.t += dt
                } else {
                    p.t += dt / p.duracao
                    if (p.t >= 1f) {
                        lista.removeAt(i)
                    } else {
                        val u = 1f - p.t
                        p.x = u * u * p.p0.x + 2f * u * p.t * p.p1.x + p.t * p.t * p.p2.x
                        p.y = u * u * p.p0.y + 2f * u * p.t * p.p1.y + p.t * p.t * p.p2.y
                    }
                }
            } else {
                p.vida -= dt
                if (p.vida <= 0f) {
                    lista.removeAt(i)
                } else {
                    p.vy += p.gravidade * dt
                    p.vx *= p.arrasto
                    p.vy *= p.arrasto
                    p.x += p.vx * dt
                    p.y += p.vy * dt
                    p.rotacao += p.giro * dt
                }
            }
            i--
        }
        ativo = lista.isNotEmpty()
        return ativo
    }

    fun desenhar(escopo: DrawScope) = with(escopo) {
        lista.forEach { p ->
            when (p.tipo) {
                TipoParticula.Moeda -> {
                    val alfa = ((p.t + 0.15f) / 0.15f).coerceIn(0f, 1f)
                    val raio = p.tamanho * (1f - 0.22f * p.t.coerceIn(0f, 1f))
                    drawCircle(Cores.Ouro, radius = raio, center = Offset(p.x, p.y), alpha = alfa)
                    drawCircle(Color(0xFFB98A23), radius = raio * 0.62f, center = Offset(p.x, p.y), alpha = alfa)
                    drawCircle(
                        Color.White.copy(alpha = 0.75f * alfa),
                        radius = raio * 0.2f,
                        center = Offset(p.x - raio * 0.28f, p.y - raio * 0.28f),
                    )
                }

                TipoParticula.Confete -> {
                    val alfa = (p.vida / 0.45f).coerceIn(0f, 1f)
                    rotate(p.rotacao, pivot = Offset(p.x, p.y)) {
                        drawRoundRect(
                            color = p.cor.copy(alpha = alfa),
                            topLeft = Offset(p.x - p.tamanho * 0.5f, p.y - p.tamanho),
                            size = androidx.compose.ui.geometry.Size(p.tamanho, p.tamanho * 1.9f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(p.tamanho * 0.35f),
                        )
                    }
                }

                TipoParticula.Anel -> {
                    val alfa = (p.vida / p.vidaMax).coerceIn(0f, 1f)
                    val progresso = 1f - p.vida / p.vidaMax
                    drawCircle(
                        color = p.cor.copy(alpha = alfa * 0.85f),
                        radius = p.tamanho + progresso * 44f * densidade,
                        center = Offset(p.x, p.y),
                        style = Stroke(width = 1.6f * densidade),
                    )
                }

                else -> {
                    val alfa = (p.vida / p.vidaMax).coerceIn(0f, 1f)
                    if (p.brilho) {
                        drawCircle(
                            color = p.cor.copy(alpha = alfa * 0.20f),
                            radius = p.tamanho * 2.4f,
                            center = Offset(p.x, p.y),
                        )
                    }
                    drawCircle(
                        color = p.cor.copy(alpha = alfa),
                        radius = p.tamanho,
                        center = Offset(p.x, p.y),
                    )
                }
            }
        }
    }

    companion object {
        const val LIMITE = 160
    }
}

/**
 * Overlay de partículas. Fica no topo da tela, não intercepta toque e respeita a densidade.
 * O loop só roda enquanto existe partícula viva.
 */
@Composable
fun CanvasParticulas(sistema: SistemaParticulas, modifier: Modifier = Modifier) {
    val densidade = LocalDensity.current.density
    var tick by remember { mutableIntStateOf(0) }
    val ativo = sistema.ativo

    LaunchedEffect(ativo) {
        if (!ativo) return@LaunchedEffect
        var anterior = 0L
        while (true) {
            var continuar = true
            androidx.compose.runtime.withFrameNanos { agora ->
                val dt = if (anterior == 0L) 0f else ((agora - anterior) / 1_000_000_000f).coerceIn(0f, 0.034f)
                anterior = agora
                continuar = sistema.atualizar(dt)
                tick++
            }
            if (!continuar) break
        }
    }

    Canvas(
        modifier = modifier.onSizeChanged {
            sistema.definirTamanho(it.width.toFloat(), it.height.toFloat(), densidade)
        },
    ) {
        tick // leitura de estado: redesenha a cada quadro enquanto houver partícula
        sistema.desenhar(this)
    }
}

/** Atalhos de emissão usados pelo [JuiceState]. Ficam aqui para o sistema não conhecer regras de jogo. */
object Emissores {
    private val aleatorio = java.util.Random()

    fun fagulha(s: SistemaParticulas, centro: Offset, cor: Color, n: Int = 16, forca: Float = 1f) {
        val d = s.densidade
        repeat(n) {
            val angulo = aleatorio.nextFloat() * 2f * PI.toFloat()
            val velocidade = (70f + aleatorio.nextFloat() * 260f * forca) * d
            val vida = 0.45f + aleatorio.nextFloat() * 0.45f
            s.emitir(
                Particula(
                    tipo = TipoParticula.Fagulha,
                    x = centro.x,
                    y = centro.y,
                    vx = cos(angulo) * velocidade,
                    vy = sin(angulo) * velocidade - 34f * d,
                    vida = vida,
                    vidaMax = vida,
                    tamanho = (1.2f + aleatorio.nextFloat() * 1.4f) * d,
                    cor = cor,
                    gravidade = 300f * d,
                    arrasto = 0.985f,
                    brilho = true,
                ),
            )
        }
    }

    fun confete(s: SistemaParticulas, n: Int = 80) {
        val d = s.densidade
        val largura = s.largura.coerceAtLeast(1f)
        val altura = s.altura.coerceAtLeast(1f)
        val cores = listOf(Color.White, Cores.Ouro, Color(0xFFF6DE8D), Color(0xFFD9C9A3))
        repeat(n) {
            val vida = 2.6f + aleatorio.nextFloat() * 2f
            s.emitir(
                Particula(
                    tipo = TipoParticula.Confete,
                    x = aleatorio.nextFloat() * largura,
                    y = -aleatorio.nextFloat() * altura * 0.35f - 20f,
                    vx = (aleatorio.nextFloat() - 0.5f) * 110f * d,
                    vy = (60f + aleatorio.nextFloat() * 120f) * d,
                    vida = vida,
                    vidaMax = 4.6f,
                    tamanho = (3.5f + aleatorio.nextFloat() * 3f) * d,
                    cor = cores[aleatorio.nextInt(cores.size)],
                    gravidade = 70f * d,
                    arrasto = 0.995f,
                    rotacao = aleatorio.nextFloat() * 360f,
                    giro = (aleatorio.nextFloat() - 0.5f) * 720f,
                ),
            )
        }
    }

    /** Moedas em arco de [origem] até [destino] (já em pixels). */
    fun moedas(s: SistemaParticulas, origem: Offset, destino: Offset, n: Int = 8) {
        val d = s.densidade
        repeat(n) {
            val p0 = Offset(
                origem.x + (aleatorio.nextFloat() - 0.5f) * 56f * d,
                origem.y + (aleatorio.nextFloat() - 0.5f) * 22f * d,
            )
            val p2 = Offset(
                destino.x + (aleatorio.nextFloat() - 0.5f) * 18f * d,
                destino.y + (aleatorio.nextFloat() - 0.5f) * 12f * d,
            )
            val p1 = Offset(
                (p0.x + p2.x) / 2f + (aleatorio.nextFloat() - 0.5f) * 150f * d,
                minOf(p0.y, p2.y) - (140f + aleatorio.nextFloat() * 100f) * d,
            )
            s.emitir(
                Particula(
                    tipo = TipoParticula.Moeda,
                    x = p0.x,
                    y = p0.y,
                    tamanho = 6f * d,
                    cor = Cores.Ouro,
                    p0 = p0,
                    p1 = p1,
                    p2 = p2,
                    duracao = 0.6f + aleatorio.nextFloat() * 0.26f,
                    atraso = aleatorio.nextFloat() * 0.34f,
                ),
            )
        }
    }

    fun brasa(s: SistemaParticulas, centro: Offset, n: Int = 7) {
        val d = s.densidade
        repeat(n) {
            val vida = 0.9f + aleatorio.nextFloat() * 0.9f
            s.emitir(
                Particula(
                    tipo = TipoParticula.Brasa,
                    x = centro.x + (aleatorio.nextFloat() - 0.5f) * 14f * d,
                    y = centro.y + (aleatorio.nextFloat() - 0.5f) * 8f * d,
                    vx = (aleatorio.nextFloat() - 0.5f) * 26f * d,
                    vy = -(40f + aleatorio.nextFloat() * 70f) * d,
                    vida = vida,
                    vidaMax = vida,
                    tamanho = (1.2f + aleatorio.nextFloat() * 1.3f) * d,
                    cor = Cores.Laranja,
                    gravidade = -12f * d,
                    arrasto = 0.99f,
                    brilho = true,
                ),
            )
        }
    }

    fun cinza(s: SistemaParticulas, n: Int = 14) {
        val d = s.densidade
        val largura = s.largura.coerceAtLeast(1f)
        repeat(n) {
            val vida = 1.2f + aleatorio.nextFloat() * 0.9f
            s.emitir(
                Particula(
                    tipo = TipoParticula.Cinza,
                    x = aleatorio.nextFloat() * largura,
                    y = -aleatorio.nextFloat() * 40f * d,
                    vx = (aleatorio.nextFloat() - 0.5f) * 20f * d,
                    vy = (18f + aleatorio.nextFloat() * 28f) * d,
                    vida = vida,
                    vidaMax = vida,
                    tamanho = (1.6f + aleatorio.nextFloat() * 1.8f) * d,
                    cor = Color(0xFF8E8E93),
                    gravidade = 4f * d,
                    arrasto = 0.999f,
                ),
            )
        }
    }

    fun anel(s: SistemaParticulas, centro: Offset, cor: Color) {
        s.emitir(
            Particula(
                tipo = TipoParticula.Anel,
                x = centro.x,
                y = centro.y,
                vida = 0.42f,
                vidaMax = 0.42f,
                tamanho = 8f * s.densidade,
                cor = cor,
            ),
        )
    }
}
