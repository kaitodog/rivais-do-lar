package com.rivaisdolar.app.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.rivaisdolar.app.data.local.Preferencias
import com.rivaisdolar.app.data.remote.CasaRepository
import com.rivaisdolar.app.di.EscopoApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Recebe as mensagens da Edge Function enviar-push (só dados; o app monta a notificação). */
@AndroidEntryPoint
class RivaisMensagens : FirebaseMessagingService() {
    @Inject lateinit var casa: CasaRepository
    @Inject @EscopoApp lateinit var escopo: CoroutineScope
    @Inject lateinit var prefs: Preferencias

    // O token pode chegar antes do login: fica guardado e o app envia quando a casa carregar.
    override fun onRegistered(token: String) {
        prefs.tokenPush = token
        escopo.launch { casa.salvarTokenPush(token) }
    }

    override fun onUnregistered(token: String) {
        prefs.tokenPush = null
    }

    override fun onMessageReceived(mensagem: RemoteMessage) {
        val dados = mensagem.data
        val titulo = dados["titulo"] ?: mensagem.notification?.title ?: return
        val corpo = dados["corpo"] ?: mensagem.notification?.body.orEmpty()
        Push.mostrar(this, dados["id"] ?: titulo, titulo, corpo)
        casa.atualizar()
    }
}
