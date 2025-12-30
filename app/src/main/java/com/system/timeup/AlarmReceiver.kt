package com.system.timeup

import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reason = intent.getStringExtra("reason") ?: "闹钟触发"
        FileLog.i(context, "闹钟触发：尝试启动前台定位服务 + 续排3分钟 reason=$reason")

        // ✅ 先续排：但也要防崩
        try {
            AlarmKick.ensure(context, seconds = 180, reason = "闹钟续排")
            FileLog.i(context, "闹钟：已续排下一次（3分钟）")
        } catch (t: Throwable) {
            FileLog.e(context, "闹钟：续排失败！${t.javaClass.simpleName}:${t.message}")
            // 续排失败也不要 return，至少本次还能试着拿定位
        }

        // ✅ 直接启动前台定位（成功率最高）
        try {
            LocationFgService.start(context, reason = "闹钟:$reason")
            FileLog.i(context, "闹钟：已请求启动前台定位服务")
        } catch (e: ForegroundServiceStartNotAllowedException) {
            FileLog.w(context, "闹钟：前台服务被系统拒绝（后台限制）：${e.message}")
            // 可选：这里你可以记录一次“本轮拿不到定位”
        } catch (t: Throwable) {
            FileLog.e(context, "闹钟：启动前台服务异常：${t.javaClass.simpleName}:${t.message}")
        }

        // ✅ 周期保险：也要防崩
        try {
            WorkKick.ensurePeriodic(context)
        } catch (t: Throwable) {
            FileLog.w(context, "闹钟：确保周期保险失败（不影响主链）：${t.javaClass.simpleName}:${t.message}")
        }
    }
}