package com.system.timeup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()

        thread(name = "TimeUpAlarm") {
            try {
                FileLog.i(context, "AlarmReceiver fired -> kick work + reschedule")

                // 1) kick 一个联网工作（网络满足才会执行）
                WorkKick.kickNow(context.applicationContext, reason = "alarm")

                // 2) 续命：下一次 15 分钟
                AlarmScheduler.ensureNext(context.applicationContext, AlarmScheduler.DEFAULT_DELAY_MS)

            } catch (t: Throwable) {
                FileLog.e(context, "AlarmReceiver error: ${t.javaClass.simpleName}:${t.message}")
                // 即使异常也要续命
                AlarmScheduler.ensureNext(context.applicationContext, AlarmScheduler.DEFAULT_DELAY_MS)
            } finally {
                pending.finish()
            }
        }
    }
}