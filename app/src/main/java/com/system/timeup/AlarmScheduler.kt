package com.system.timeup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.concurrent.TimeUnit

object AlarmScheduler {
    private const val TAG = "TimeUpAlarm"
    private const val REQ_CODE = 1001

    // 默认 15 分钟
    val DEFAULT_DELAY_MS: Long = TimeUnit.MINUTES.toMillis(15)

    fun scheduleNext(context: Context, delayMs: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context)

        val triggerAt = System.currentTimeMillis() + delayMs

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+：如果不允许精确闹钟，自动降级
                if (am.canScheduleExactAlarms()) {
                    setExactAllowIdle(am, triggerAt, pi)
                    Log.i(TAG, "Scheduled EXACT(+idle) in ${delayMs / 1000}s")
                } else {
                    setInexactAllowIdle(am, triggerAt, pi)
                    Log.w(TAG, "Exact alarm not allowed -> fallback in ${delayMs / 1000}s")
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                setExactAllowIdle(am, triggerAt, pi)
                Log.i(TAG, "Scheduled EXACT(+idle) in ${delayMs / 1000}s")
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                Log.i(TAG, "Scheduled EXACT in ${delayMs / 1000}s")
            }
        } catch (t: Throwable) {
            // 兜底：任何异常都别让它断链
            Log.e(TAG, "scheduleNext failed -> fallback setWindow. err=${t.javaClass.simpleName}:${t.message}")
            setWindowFallback(am, triggerAt, pi)
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context))
        Log.i(TAG, "Alarm cancelled")
    }

    private fun setExactAllowIdle(am: AlarmManager, triggerAt: Long, pi: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun setInexactAllowIdle(am: AlarmManager, triggerAt: Long, pi: PendingIntent) {
        // 非精确兜底：尽量允许 idle
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun setWindowFallback(am: AlarmManager, triggerAt: Long, pi: PendingIntent) {
        // 给一个 2 分钟窗口（你要求 1~2 分钟抖动可接受）
        val window = TimeUnit.MINUTES.toMillis(2)
        am.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, window, pi)
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getBroadcast(context, REQ_CODE, intent, flags)
    }
}