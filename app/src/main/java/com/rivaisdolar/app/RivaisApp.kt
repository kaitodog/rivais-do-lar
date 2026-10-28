package com.rivaisdolar.app

import android.app.Application
import com.rivaisdolar.app.push.Push
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RivaisApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Push.iniciar(this)
    }
}
