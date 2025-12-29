package com.system.timeup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val app = context.applicationContext

        thread(name = "TimeUpAlarmThread") {
            try {
                val now = System.currentTimeMillis()
                Persist.setLastAlarmFire(app, now)
                FileLog.i(app, "闹钟已触发：准备投递联网任务，并续排下一次（3分钟）")

                // 1) 主执行：投递一次性联网任务
                WorkKick.kickNow(app, reason = "闹钟触发")

                // 2) 看门狗补救：若长时间未启动任务，则额外补踢一次（保险）
                val lastWork = Persist.getLastWorkStart(app)
                if (lastWork > 0L && now - lastWork > AlarmScheduler.INTERVAL_MS * 3) {
                    FileLog.w(app, "检测到任务可能长时间未启动，触发补救投递（额外保险）")
                    WorkKick.kickNow(app, reason = "看门狗补救")
                }

                // 3) 链式续命：下一次 3 分钟
                AlarmScheduler.ensureNext(app, AlarmScheduler.INTERVAL_MS)

            } catch (t: Throwable) {
                FileLog.e(app, "闹钟处理异常：${t.javaClass.simpleName}:${t.message}（仍将续排）")
                AlarmScheduler.ensureNext(app, AlarmScheduler.INTERVAL_MS)
            } finally {
                pending.finish()
            }
        }
    }
}