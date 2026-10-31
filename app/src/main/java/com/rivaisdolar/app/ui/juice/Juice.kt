package com.rivaisdolar.app.ui.juice

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import com.rivaisdolar.app.ui.theme.Cores

/** Momentos do jogo que ganham partícula e/ou haptic. */
enum class EventoJuice {
    Pontos,
    Virada,
    Validado,
    Carta,
    Compra,
    Enviado,
    Meta,
    Vitoria,
    Derrota,
    Sequencia,
    Vistoria,
}

/**
 * Dispara o "suco" de um evento. O haptic vale mesmo com movimento reduzido; a partícula não.
 * As coordenadas são frações da tela; [origem] permite ancorar no placar ou no cartão.
 */
class JuiceState(
    val sistema: SistemaParticulas,
    private val haptico: Haptico,
) {
    var reduzido: Boolean = false

    fun disparar(evento: EventoJuice, origem: Offset? = null) {
        when (evento) {
            EventoJuice.Pontos, EventoJuice.Enviado -> haptico.tocar(Toque.Pop)
            EventoJuice.Virada, EventoJuice.Validado, EventoJuice.Carta -> haptico.tocar(Toque.Sucesso)
            EventoJuice.Compra -> haptico.tocar(Toque.Clique)
            EventoJuice.Sequencia -> haptico.tocar(Toque.Tick)
            EventoJuice.Vistoria -> haptico.tocar(Toque.Sucesso)
            EventoJuice.Meta -> haptico.tocar(Toque.Sucesso)
            EventoJuice.Vitoria -> haptico.vitoria()
            EventoJuice.Derrota -> Unit
        }
        if (reduzido) return

        val largura = sistema.largura
        val altura = sistema.altura
        if (largura <= 0f || altura <= 0f) return

        fun ponto(x: Float, y: Float) = Offset(largura * x, altura * y)

        when (evento) {
            EventoJuice.Pontos -> {
                val centro = origem ?: ponto(0.24f, 0.31f)
                Emissores.fagulha(sistema, centro, Cores.Azul, 18, 1f)
                Emissores.moedas(sistema, centro, ponto(0.16f, 0.075f), 6)
            }

            EventoJuice.Virada -> {
                val centro = origem ?: ponto(0.5f, 0.34f)
                Emissores.fagulha(sistema, centro, Cores.Ouro, 22, 1.05f)
                Emissores.anel(sistema, centro, Cores.Ouro)
            }

            EventoJuice.Validado -> {
                val centro = origem ?: ponto(0.5f, 0.46f)
                Emissores.fagulha(sistema, centro, Cores.Ouro, 20, 1f)
                Emissores.anel(sistema, centro, Cores.Ouro)
            }

            EventoJuice.Carta -> {
                val centro = origem ?: ponto(0.5f, 0.55f)
                Emissores.fagulha(sistema, centro, Cores.Ouro, 30, 1.2f)
                Emissores.anel(sistema, centro, Cores.Ouro)
            }

            EventoJuice.Compra -> {
                val centro = origem ?: ponto(0.5f, 0.55f)
                Emissores.moedas(sistema, centro, ponto(0.16f, 0.075f), 7)
                Emissores.fagulha(sistema, centro, Cores.Ouro, 10, 0.8f)
            }

            EventoJuice.Enviado -> {
                val centro = origem ?: ponto(0.5f, 0.62f)
                Emissores.fagulha(sistema, centro, Cores.Ouro, 14, 0.9f)
            }

            EventoJuice.Meta -> {
                Emissores.confete(sistema, 60)
                Emissores.fagulha(sistema, ponto(0.5f, 0.3f), Cores.Ouro, 20, 1f)
            }

            EventoJuice.Vitoria -> {
                Emissores.confete(sistema, 90)
                Emissores.fagulha(sistema, ponto(0.5f, 0.22f), Cores.Ouro, 26, 1.1f)
            }

            EventoJuice.Derrota -> Emissores.cinza(sistema, 16)

            EventoJuice.Sequencia -> Emissores.brasa(sistema, ponto(0.11f, 0.32f), 7)

            EventoJuice.Vistoria -> {
                val centro = origem ?: ponto(0.5f, 0.46f)
                Emissores.fagulha(sistema, centro, Cores.Alerta, 18, 1f)
                Emissores.anel(sistema, centro, Cores.Alerta)
            }
        }
    }
}

val LocalJuice = staticCompositionLocalOf { JuiceState(SistemaParticulas(), HapticoNulo) }
