package com.system.timeup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext
        val action = intent?.action ?: "未知"

        FileLog.i(app, "系统广播触发：$action，执行补排（闹钟+任务+保险）")

        // 开机/变更时给 30 秒缓冲更稳
        AlarmScheduler.ensureNext(app, 30_000L)

        // 补踢一次联网任务（有网才会跑）
        WorkKick.kickNow(app, reason = "系统广播:$action")

        // 确保 15分钟周期保险存在
        WorkKick.ensurePeriodic(app)
    }
}