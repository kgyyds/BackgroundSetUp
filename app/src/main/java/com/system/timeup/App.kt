package com.system.timeup

import android.app.Application
import android.util.Log

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "App.onCreate -> schedule alarm (soft)")
        // 不强依赖这里，主要靠 SetupActivity / BootReceiver 补排
        AlarmScheduler.scheduleNext(this, AlarmScheduler.DEFAULT_DELAY_MS)
    }

    companion object {
        private const val TAG = "TimeUp"
    }
}