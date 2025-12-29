package com.system.timeup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.i(TAG, "BootReceiver action=${intent?.action} -> schedule alarm")
        // 开机后给它 1 分钟缓冲，避免系统忙/ROM初始化阶段干扰
        AlarmScheduler.scheduleNext(context.applicationContext, 60_000L)
    }

    companion object {
        private const val TAG = "TimeUpBoot"
    }
}