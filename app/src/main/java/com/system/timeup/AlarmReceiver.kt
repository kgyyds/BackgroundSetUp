package com.system.timeup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()

        thread(name = "TimeUpAlarmThread") {
            try {
                val ts = sdf.format(Date())
                Log.i(TAG, "Tick! time=$ts")

                // TODO：这里以后换成“联网上传/访问API”
                // 建议：网络请求必须设置超时（比如 8s），失败就打日志，下次再试

            } catch (t: Throwable) {
                Log.e(TAG, "Tick failed: ${t.javaClass.simpleName}:${t.message}")
            } finally {
                // ✅ 自续命：固定 15 分钟后再来一次
                AlarmScheduler.scheduleNext(context.applicationContext, AlarmScheduler.DEFAULT_DELAY_MS)
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "TimeUpTick"
        private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    }
}