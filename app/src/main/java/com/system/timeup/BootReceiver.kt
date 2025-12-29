package com.system.timeup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        FileLog.i(context, "收到系统广播：${intent.action}，重排闹钟 + 周期保险")
        AlarmKick.ensure(context, seconds = 180, reason = "开机/更新后补排")
        WorkKick.ensurePeriodic(context)
    }
}