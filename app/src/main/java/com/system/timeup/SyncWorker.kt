package com.system.timeup

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val reason = inputData.getString("reason") ?: "未知原因"
        Persist.setLastWorkStart(applicationContext, System.currentTimeMillis())

        FileLog.i(applicationContext, "轨迹任务开始：原因=$reason，尝试次数=$runAttemptCount")

        if (!hasLocationPermission()) {
            FileLog.w(applicationContext, "轨迹任务失败：未检测到定位权限（请在系统设置中为本应用开启定位权限）")
            // 不狂重试：你开好权限后下一轮 Alarm 会再跑
            return Result.failure()
        }

        // 1) 定位：最多 2 次
        val loc = getLocationWithMaxAttempts(maxAttempts = 2)
        if (loc == null) {
            FileLog.w(applicationContext, "定位最终失败：已尝试2次仍获取不到，本轮结束，等待下一次调度重试")
            return Result.retry()
        }

        val lat = loc.latitude
        val lon = loc.longitude
        val acc = loc.accuracy
        val ts = loc.time
        val provider = loc.provider ?: "unknown"

        FileLog.i(
            applicationContext,
            "定位结果：纬度=$lat，经度=$lon，精度≈${acc}米，来源=$provider，时间戳=$ts"
        )

        // 2) 上报：最多 3 次
        val uploadOk = uploadWithMaxAttempts(
            maxAttempts = 3,
            urlStr = "https://你的域名/track/upload", // TODO：替换为你的接口
            lat = lat,
            lon = lon,
            acc = acc,
            ts = ts
        )

        return if (uploadOk) {
            FileLog.i(applicationContext, "轨迹上报成功 ✅")
            Result.success()
        } else {
            FileLog.w(applicationContext, "轨迹上报最终失败：已尝试3次仍失败，本轮结束，等待下一次调度重试")
            Result.retry()
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * 单次定位最多尝试 maxAttempts 次（每次都是“一次性获取”，不会持续监听）
     */
    private suspend fun getLocationWithMaxAttempts(maxAttempts: Int): android.location.Location? {
        for (i in 1..maxAttempts) {
            try {
                FileLog.i(applicationContext, "开始获取定位：第 $i/$maxAttempts 次")
                val loc = withTimeout(12_000L) { getOneShotLocation() }
                if (loc != null) {
                    FileLog.i(applicationContext, "获取定位成功：第 $i/$maxAttempts 次")
                    return loc
                }
                FileLog.w(applicationContext, "获取定位失败：第 $i/$maxAttempts 次（返回为空）")
            } catch (t: Throwable) {
                FileLog.w(applicationContext, "获取定位异常：第 $i/$maxAttempts 次，${t.javaClass.simpleName}:${t.message}")
            }

            // 两次之间稍微喘口气，避免连续打爆定位栈（也更像真实设备行为）
            if (i < maxAttempts) delay(800L)
        }
        return null
    }

    /**
     * 只取一次定位
     */
    private suspend fun getOneShotLocation() = suspendCancellableCoroutine { cont ->
        val client = LocationServices.getFusedLocationProviderClient(applicationContext)
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (cont.isActive) cont.resume(loc)
            }
            .addOnFailureListener { e ->
                FileLog.w(applicationContext, "单次定位失败：${e.javaClass.simpleName}:${e.message}")
                if (cont.isActive) cont.resume(null)
            }
    }

    /**
     * 上报最多尝试 maxAttempts 次
     */
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
                FileLog.i(applicationContext, "开始上报轨迹：第 $i/$maxAttempts 次")
                val ok = postLocation(urlStr, lat, lon, acc, ts)
                if (ok) {
                    FileLog.i(applicationContext, "上报成功：第 $i/$maxAttempts 次")
                    return true
                }
                FileLog.w(applicationContext, "上报失败：第 $i/$maxAttempts 次（HTTP非2xx）")
            } catch (t: Throwable) {
                FileLog.w(applicationContext, "上报异常：第 $i/$maxAttempts 次，${t.javaClass.simpleName}:${t.message}")
            }

            // 三次之间递增等待，避免疯狂打服务器
            if (i < maxAttempts) delay(1000L * i)
        }
        return false
    }

    /**
     * POST 上传（JSON）
     */
    private fun postLocation(urlStr: String, lat: Double, lon: Double, acc: Float, ts: Long): Boolean {
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
            conn.outputStream.use { os ->
                os.write(payload.toByteArray(Charsets.UTF_8))
            }
            val code = conn.responseCode
            code in 200..299
        } finally {
            conn.disconnect()
        }
    }
}