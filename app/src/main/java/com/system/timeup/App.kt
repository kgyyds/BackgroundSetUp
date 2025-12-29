package com.system.timeup

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        FileLog.i(this, "应用进程启动：执行软补排（闹钟3分钟 + 周期保险）")
        AlarmKick.ensure(this, seconds = 180, reason = "进程启动软补排")
        WorkKick.ensurePeriodic(this)
    }
}