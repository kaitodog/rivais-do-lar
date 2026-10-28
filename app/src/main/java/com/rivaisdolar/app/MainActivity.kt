package com.rivaisdolar.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rivaisdolar.app.data.remote.CasaRepository
import com.rivaisdolar.app.ui.RivaisRaiz
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var casa: CasaRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent { RivaisRaiz() }
    }

    override fun onStart() {
        super.onStart()
        // Voltar ao app sempre puxa o estado mais recente (o Realtime pode ter caído em segundo plano).
        casa.atualizar()
    }
}
