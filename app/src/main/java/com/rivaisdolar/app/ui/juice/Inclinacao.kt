package com.rivaisdolar.app.ui.juice

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext

/**
 * Inclinação do aparelho normalizada em -1..1. O valor é lido no desenho (não na composição),
 * então o sensor inclina o reflexo do vidro sem recompor a tela.
 */
class InclinacaoState {
    var valor by mutableStateOf(Offset.Zero)
}

val LocalInclinacao = staticCompositionLocalOf { InclinacaoState() }

/**
 * Liga o acelerômetro filtrado enquanto [ativo]. Em economia de bateria ou com movimento
 * reduzido, o valor fica parado em zero.
 */
@Composable
fun InclinacaoProvider(ativo: Boolean, conteudo: @Composable () -> Unit) {
    val context = LocalContext.current
    val estado = remember { InclinacaoState() }
    val gerenciador = remember { context.getSystemService(SensorManager::class.java) }
    val sensor = remember { gerenciador?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }

    DisposableEffect(ativo, sensor) {
        if (!ativo || sensor == null || gerenciador == null) {
            estado.valor = Offset.Zero
            return@DisposableEffect onDispose { }
        }
        val ouvinte = object : SensorEventListener {
            override fun onSensorChanged(evento: SensorEvent) {
                val x = (-evento.values[0] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
                val y = (evento.values[1] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
                // Passa-baixa: some com o tremor da mão e mantém a luz estável.
                estado.valor = Offset(
                    estado.valor.x * 0.92f + x * 0.08f,
                    estado.valor.y * 0.92f + y * 0.08f,
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, precisao: Int) = Unit
        }
        gerenciador.registerListener(ouvinte, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose {
            gerenciador.unregisterListener(ouvinte)
            estado.valor = Offset.Zero
        }
    }

    CompositionLocalProvider(LocalInclinacao provides estado, content = conteudo)
}
