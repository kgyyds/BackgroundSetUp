package com.system.timeup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.net.HttpURLConnection
import java.net.URL

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val reason = inputData.getString("reason") ?: "未知原因"
        val now = System.currentTimeMillis()
        Persist.setLastWorkStart(applicationContext, now)

        FileLog.i(applicationContext, "联网任务开始：原因=$reason，尝试次数=$runAttemptCount")

        return try {
            // 你后面换成真实API上传即可
            val ok = ping("https://www.google.com/generate_204")
            if (ok) {
                FileLog.i(applicationContext, "联网任务成功：网络正常 / 请求成功")
                Result.success()
            } else {
                FileLog.w(applicationContext, "联网任务失败：网络/请求异常，准备重试")
                Result.retry()
            }
        } catch (t: Throwable) {
            FileLog.e(applicationContext, "联网任务异常：${t.javaClass.simpleName}:${t.message}，准备重试")
            Result.retry()
        }
    }

    private fun ping(urlStr: String): Boolean {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        return try {
            conn.connect()
            val code = conn.responseCode
            code in 200..399
        } finally {
            conn.disconnect()
        }
    }
}