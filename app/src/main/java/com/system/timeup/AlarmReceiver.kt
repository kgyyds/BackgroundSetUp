package com.system.timeup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reason = intent.getStringExtra("reason") ?: "闹钟触发"

        FileLog.i(context, "闹钟触发：投递Work→由Work启动前台定位服务 + 续排3分钟")

        // ✅ 关键：由Work去启动FGS（更稳）
        WorkKick.kickFgsNow(context, reason = "闹钟:$reason")

        // ✅ 续排
        AlarmKick.ensure(context, seconds = 180, reason = "闹钟续排")
    }
}