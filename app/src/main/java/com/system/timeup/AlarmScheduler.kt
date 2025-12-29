package com.system.timeup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.concurrent.TimeUnit

object AlarmScheduler {
    private const val REQ_CODE = 1001

    // ✅ 3分钟一次
    val INTERVAL_MS: Long = TimeUnit.MINUTES.toMillis(3)

    fun ensureNext(context: Context, delayMs: Long = INTERVAL_MS) {
        val app = context.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(app)

        val triggerAt = System.currentTimeMillis() + delayMs

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    setExactAllowIdle(am, triggerAt, pi)
                    FileLog.i(app, "已安排闹钟（精确）: ${delayMs / 1000} 秒后触发")
                } else {
                    setInexactAllowIdle(am, triggerAt, pi)
                    FileLog.w(app, "系统不允许精确闹钟，已降级安排: ${delayMs / 1000} 秒后触发（可能延迟）")
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                setExactAllowIdle(am, triggerAt, pi)
                FileLog.i(app, "已安排闹钟（允许待机）: ${delayMs / 1000} 秒后触发")
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                FileLog.i(app, "已安排闹钟: ${delayMs / 1000} 秒后触发")
            }
        } catch (t: Throwable) {
            FileLog.e(app, "安排闹钟失败，使用窗口兜底: ${t.javaClass.simpleName}:${t.message}")
            val window = TimeUnit.MINUTES.toMillis(1)
            am.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, window, pi)
        }
    }

    private fun setExactAllowIdle(am: AlarmManager, triggerAt: Long, pi: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun setInexactAllowIdle(am: AlarmManager, triggerAt: Long, pi: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val i = Intent(context, AlarmReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getBroadcast(context, REQ_CODE, i, flags)
    }
}