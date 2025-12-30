package com.system.timeup

import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reason = intent.getStringExtra("reason") ?: "闹钟触发"
        FileLog.i(context, "闹钟触发：尝试启动前台定位服务 + 续排3分钟 reason=$reason")

        // ✅ 先续排，避免后面任何异常导致断链
        AlarmKick.ensure(context, seconds = 180, reason = "闹钟续排")

        // ✅ 直接尝试启动前台定位（成功率最高）
        try {
            LocationFgService.start(context, reason = "闹钟:$reason")
            FileLog.i(context, "闹钟：已请求启动前台定位服务")
        } catch (e: ForegroundServiceStartNotAllowedException) {
            // ✅ 不崩！记录即可
            FileLog.w(context, "闹钟：前台服务被系统拒绝（后台限制）：${e.message}")
        } catch (t: Throwable) {
            FileLog.e(context, "闹钟：启动前台服务异常：${t.javaClass.simpleName}:${t.message}")
        }

        // ✅ 可选：顺便投递一个 Work 做上报/清理（但不让它负责启动FGS）
        WorkKick.ensurePeriodic(context)
    }
}