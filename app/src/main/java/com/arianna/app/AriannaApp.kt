package com.arianna.app

import android.app.Application
import com.arianna.app.data.push.Notifications
import com.arianna.app.di.AppContainer

class AriannaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appearance.restore()
        Notifications.createChannels(this)
    }
}
