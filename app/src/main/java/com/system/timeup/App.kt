package com.system.timeup

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        FileLog.i(this, "应用进程启动：执行软补排（闹钟3分钟 + 周期保险）")

        AlarmScheduler.ensureNext(this, AlarmScheduler.INTERVAL_MS)
        WorkKick.ensurePeriodic(this)
    }
}