package com.robotta

import android.app.Application
import android.util.Log
import com.robotta.util.NotificationHelper

class RobottaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        Log.d("RobottaApp", "Aplikasi dimulai")
    }
}
