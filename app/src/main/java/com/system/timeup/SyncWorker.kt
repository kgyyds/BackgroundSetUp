package com.system.timeup

import android.content.Context
import android.location.Location
import androidx.work.*
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val reason = inputData.getString("reason") ?: "未知原因"
        val lat = inputData.getDouble("lat", Double.NaN)
        val lon = inputData.getDouble("lon", Double.NaN)
        val acc = inputData.getFloat("acc", Float.NaN)
        val ts = inputData.getLong("ts", 0L)

        if (lat.isNaN() || lon.isNaN()) {
            FileLog.w(applicationContext, "上报任务取消：没有定位数据（reason=$reason）")
            return Result.failure()
        }

        FileLog.i(applicationContext, "开始上报：reason=$reason lat=$lat lon=$lon acc=$acc ts=$ts")

        val ok = uploadWithMaxAttempts(
            maxAttempts = 3,
            urlStr = "https://你的域名/track/upload", // TODO 替换
            lat = lat, lon = lon, acc = acc, ts = ts
        )

        return if (ok) {
            FileLog.i(applicationContext, "上报成功 ✅")
            Result.success()
        } else {
            FileLog.w(applicationContext, "上报失败：已尝试3次，等待下次重试")
            Result.retry()
        }
    }

    private suspend fun uploadWithMaxAttempts(
        maxAttempts: Int,
        urlStr: String,
        lat: Double,
        lon: Double,
        acc: Float,
        ts: Long
    ): Boolean {
        for (i in 1..maxAttempts) {
            try {
                FileLog.i(applicationContext, "上报尝试：第 $i/$maxAttempts 次")
                val ok = postJson(urlStr, lat, lon, acc, ts)
                if (ok) return true
                FileLog.w(applicationContext, "上报失败：第 $i/$maxAttempts 次（HTTP非2xx）")
            } catch (t: Throwable) {
                FileLog.w(applicationContext, "上报异常：第 $i/$maxAttempts 次 ${t.javaClass.simpleName}:${t.message}")
            }
            if (i < maxAttempts) delay(1000L * i)
        }
        return false
    }

    private fun postJson(urlStr: String, lat: Double, lon: Double, acc: Float, ts: Long): Boolean {
        val payload = JSONObject().apply {
            put("lat", lat)
            put("lon", lon)
            put("acc", acc)
            put("ts", ts)
            put("model", android.os.Build.MODEL ?: "unknown")
        }.toString()

        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        return try {
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            conn.responseCode in 200..299
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        fun enqueueOnce(context: Context, reason: String, loc: Location) {
            val data = workDataOf(
                "reason" to reason,
                "lat" to loc.latitude,
                "lon" to loc.longitude,
                "acc" to loc.accuracy,
                "ts" to loc.time
            )

            val req = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(data)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueue(req)
            FileLog.i(context, "已投递上报任务：reason=$reason")
        }
    }
}