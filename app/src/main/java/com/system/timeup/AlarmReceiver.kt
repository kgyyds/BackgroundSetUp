package com.system.timeup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        FileLog.i(context, "闹钟触发：启动前台定位服务 + 续排3分钟")

        LocationFgService.start(context, reason = "闹钟触发")
        AlarmKick.ensure(context, seconds = 180, reason = "闹钟续排")
    }
}