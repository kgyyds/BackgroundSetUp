package com.system.timeup

import android.app.Application
import android.util.Log

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "App.onCreate -> ensure scheduled")
        TickScheduler.schedule(this)
    }

    companion object {
        private const val TAG = "TimeUp"
    }
}