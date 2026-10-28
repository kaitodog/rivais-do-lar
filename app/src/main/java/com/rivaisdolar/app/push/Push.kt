package com.rivaisdolar.app.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.rivaisdolar.app.BuildConfig
import com.rivaisdolar.app.MainActivity
import com.rivaisdolar.app.R

/**
 * Firebase entra só para entregar push. As opções vêm do BuildConfig (sem google-services.json),
 * então um APK sem elas funciona igual, apenas sem notificação no celular.
 */
object Push {
    private const val CANAL = "rivais"

    val configurado: Boolean
        get() = listOf(BuildConfig.FIREBASE_APP_ID, BuildConfig.FIREBASE_API_KEY, BuildConfig.FIREBASE_PROJECT_ID, BuildConfig.FIREBASE_SENDER_ID)
            .all { it.isNotBlank() }

    fun iniciar(context: Context) {
        criarCanal(context)
        if (!configurado || FirebaseApp.getApps(context).isNotEmpty()) return
        FirebaseApp.initializeApp(
            context,
            FirebaseOptions.Builder()
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                .build(),
        )
    }

    /** Pede o registro no FCM; o token chega em [RivaisMensagens.onRegistered]. */
    fun registrar() {
        if (configurado) FirebaseMessaging.getInstance().register()
    }

    fun permitido(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun criarCanal(context: Context) {
        val canal = NotificationChannel(CANAL, "Placar e provocações", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Viradas, pedidos de validação, bombas e fim de temporada"
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(canal)
    }

    fun mostrar(context: Context, id: String, titulo: String, corpo: String) {
        if (!permitido(context)) return
        val abrir = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notificacao = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentTitle(titulo)
            .setContentText(corpo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(corpo))
            .setAutoCancel(true)
            .setContentIntent(abrir)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id.hashCode(), notificacao)
        } catch (_: SecurityException) {
            // Permissão revogada entre a checagem e o envio: não há o que mostrar.
        }
    }
}
