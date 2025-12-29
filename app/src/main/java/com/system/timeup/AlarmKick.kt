package com.system.timeup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object AlarmKick {
    fun ensure(context: Context, seconds: Int, reason: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAt = System.currentTimeMillis() + seconds * 1000L

        // Android 12+ 可能不允许精确闹钟
        val canExact = if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true

        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            FileLog.i(context, "已安排精确闹钟：${seconds}秒后触发（$reason）")
        } else {
            // 降级：用 setAndAllowWhileIdle（不精确，但尽量）
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            FileLog.w(context, "系统不允许精确闹钟，已降级安排：${seconds}秒后触发（可能延迟）（$reason）")
        }
    }
}