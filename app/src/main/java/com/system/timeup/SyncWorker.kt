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
        val reason = inputData.getString("reason") ?: "unknown"
        FileLog.i(applicationContext, "SyncWorker start reason=$reason attempt=$runAttemptCount")

        return try {
            // ✅ 先用一个最小联网探测（以后你换成真实 API 上传）
            // 选择一个非常轻的请求：你可以换成自己的 API
            val ok = ping("https://www.baidu.com/")
            FileLog.i(applicationContext, "SyncWorker network ping ok=$ok")

            if (ok) {
                FileLog.i(applicationContext, "SyncWorker success")
                Result.success()
            } else {
                FileLog.w(applicationContext, "SyncWorker failed -> retry")
                Result.retry()
            }
        } catch (t: Throwable) {
            FileLog.e(applicationContext, "SyncWorker exception: ${t.javaClass.simpleName}:${t.message} -> retry")
            Result.retry()
        }
    }

    private fun ping(urlStr: String): Boolean {
        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        return try {
            conn.connect()
            val code = conn.responseCode
            // generate_204: 204 代表通了
            code in 200..399
        } finally {
            conn.disconnect()
        }
    }
}