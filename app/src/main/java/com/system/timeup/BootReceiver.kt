package com.system.timeup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: "null"
        FileLog.i(context, "BootReceiver action=$action -> ensure alarm + kick work")

        // 开机给 60s 缓冲更稳（系统忙）
        AlarmScheduler.ensureNext(context.applicationContext, 60_000L)

        // 同时 kick 一个 OneTimeWork（网络满足就跑）
        WorkKick.kickNow(context.applicationContext, reason = "boot:$action")

        // 冗余 periodic
        WorkKick.ensurePeriodic(context.applicationContext)
    }
}