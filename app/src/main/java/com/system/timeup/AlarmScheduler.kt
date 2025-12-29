package com.system.timeup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.concurrent.TimeUnit

object AlarmScheduler {
    private const val REQ_CODE = 1001
    val DEFAULT_DELAY_MS: Long = TimeUnit.MINUTES.toMillis(15)

    fun ensureNext(context: Context, delayMs: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context)

        val triggerAt = System.currentTimeMillis() + delayMs

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    setExactAllowIdle(am, triggerAt, pi)
                    FileLog.i(context, "Alarm scheduled EXACT(+idle) in ${delayMs / 1000}s")
                } else {
                    setInexactAllowIdle(am, triggerAt, pi)
                    FileLog.w(context, "Exact alarm not allowed -> fallback in ${delayMs / 1000}s")
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                setExactAllowIdle(am, triggerAt, pi)
                FileLog.i(context, "Alarm scheduled EXACT(+idle) in ${delayMs / 1000}s")
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                FileLog.i(context, "Alarm scheduled EXACT in ${delayMs / 1000}s")
            }
        } catch (t: Throwable) {
            FileLog.e(context, "Alarm schedule failed -> fallback setWindow. err=${t.javaClass.simpleName}:${t.message}")
            val window = TimeUnit.MINUTES.toMillis(2)
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
        val intent = Intent(context, AlarmReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getBroadcast(context, REQ_CODE, intent, flags)
    }
}