package com.rivaisdolar.app.ui.juice

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings

enum class Toque { Clique, Tick, Pop, Sucesso, Erro }

/** Feedback tátil com fallback. Não depende de composable para poder ser usado em callbacks. */
interface Haptico {
    fun tocar(toque: Toque)
    fun vitoria()
}

object HapticoNulo : Haptico {
    override fun tocar(toque: Toque) = Unit
    override fun vitoria() = Unit
}

class HapticoAndroid(context: Context) : Haptico {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    private val ligado: Boolean = runCatching {
        Settings.System.getInt(
            context.contentResolver,
            Settings.System.HAPTIC_FEEDBACK_ENABLED,
            1,
        ) == 1
    }.getOrDefault(true)

    override fun tocar(toque: Toque) {
        if (!ligado) return
        val aparelho = vibrator ?: return
        if (!aparelho.hasVibrator()) return
        val efeito = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            VibrationEffect.createPredefined(
                when (toque) {
                    Toque.Clique -> VibrationEffect.EFFECT_CLICK
                    Toque.Tick -> VibrationEffect.EFFECT_TICK
                    Toque.Pop -> VibrationEffect.EFFECT_CLICK
                    Toque.Sucesso -> VibrationEffect.EFFECT_DOUBLE_CLICK
                    Toque.Erro -> VibrationEffect.EFFECT_CLICK
                },
            )
        } else {
            VibrationEffect.createOneShot(
                when (toque) {
                    Toque.Clique -> 12L
                    Toque.Tick -> 8L
                    Toque.Pop -> 22L
                    Toque.Sucesso -> 36L
                    Toque.Erro -> 46L
                },
                when (toque) {
                    Toque.Clique -> 90
                    Toque.Tick -> 50
                    Toque.Pop -> 140
                    Toque.Sucesso -> 180
                    Toque.Erro -> 255
                },
            )
        }
        runCatching { aparelho.vibrate(efeito) }
    }

    override fun vitoria() {
        if (!ligado) return
        val aparelho = vibrator ?: return
        if (!aparelho.hasVibrator()) return
        val efeito = VibrationEffect.createWaveform(
            longArrayOf(0, 40, 50, 30, 70, 50, 120),
            intArrayOf(0, 180, 0, 255, 0, 120, 0),
            -1,
        )
        runCatching { aparelho.vibrate(efeito) }
    }
}
