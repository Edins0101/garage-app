package com.garaje.app

import android.app.Application
import com.garaje.app.di.AppContainer

class GarajeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
