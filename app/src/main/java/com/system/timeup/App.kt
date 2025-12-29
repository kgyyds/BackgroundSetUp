package com.system.timeup

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        FileLog.i(this, "App.onCreate -> ensure schedules (soft)")

        // 软补排：不强依赖，只要进程起来就续上
        AlarmScheduler.ensureNext(this, AlarmScheduler.DEFAULT_DELAY_MS)
        WorkKick.ensurePeriodic(this) // 冗余：能跑就跑
    }
}