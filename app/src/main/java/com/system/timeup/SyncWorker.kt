package com.system.timeup

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.math.max

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        // ✅ 全局互斥：同一时刻只允许一个 SyncWorker 真正执行
        private val RUNNING = java.util.concurrent.atomic.AtomicBoolean(false)
    }

    override suspend fun doWork(): Result {
        val reason = inputData.getString("reason") ?: "未知原因"

        // ✅ 修双跑：如果已经有一个在跑，直接跳过（稳定优先）
        if (!RUNNING.compareAndSet(false, true)) {
            FileLog.w(applicationContext, "检测到任务并发触发：本次跳过（稳定优先，避免互相取消/抢定位）。原因=$reason")
            return Result.success()
        }

        return try {
            Persist.setLastWorkStart(applicationContext, System.currentTimeMillis())
            FileLog.i(applicationContext, "轨迹任务开始：原因=$reason，尝试次数=$runAttemptCount")

            if (!hasLocationPermission()) {
                FileLog.w(applicationContext, "轨迹任务失败：未检测到定位权限（请在系统设置中为本应用开启定位权限）")
                return Result.failure()
            }

            // 1) 定位：最多 2 次（每次：current -> last兜底）
            val loc = getLocationWithMaxAttempts(maxAttempts = 2)
            if (loc == null) {
                FileLog.w(applicationContext, "定位最终失败：已尝试2次仍获取不到，本轮结束，等待下一次调度重试")
                return Result.retry()
            }

            logLocation(loc)

            // 2) 上报：最多 3 次
            val ok = uploadWithMaxAttempts(
                maxAttempts = 3,
                urlStr = "https://你的域名/track/upload", // TODO：替换成你的接口
                loc = loc
            )

            if (ok) {
                FileLog.i(applicationContext, "轨迹上报成功 ✅")
                Result.success()
            } else {
                FileLog.w(applicationContext, "轨迹上报最终失败：已尝试3次仍失败，本轮结束，等待下一次调度重试")
                Result.retry()
            }
        } catch (t: Throwable) {
            if (t is CancellationException) {
                val sr = try { stopReason } catch (_: Throwable) { -1 }
                FileLog.w(applicationContext, "轨迹任务被系统取消：${t.javaClass.simpleName}:${t.message} stopReason=$sr isStopped=$isStopped")
                return Result.retry()
            }
            FileLog.e(applicationContext, "轨迹任务异常：${t.javaClass.simpleName}:${t.message}，准备重试")
            Result.retry()
        } finally {
            RUNNING.set(false)
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private suspend fun getLocationWithMaxAttempts(maxAttempts: Int): Location? {
        for (i in 1..maxAttempts) {
            try {
                FileLog.i(applicationContext, "开始获取定位：第 $i/$maxAttempts 次（先current，失败再last）")

                val loc = withTimeout(12_000L) { getCurrentThenLastOnce() }
                if (loc != null) {
                    FileLog.i(applicationContext, "获取定位成功：第 $i/$maxAttempts 次")
                    return loc
                }

                FileLog.w(applicationContext, "获取定位失败：第 $i/$maxAttempts 次（current+last均为空）")
            } catch (t: Throwable) {
                if (t is CancellationException) {
                    val sr = try { stopReason } catch (_: Throwable) { -1 }
                    FileLog.w(applicationContext, "获取定位被取消：第 $i/$maxAttempts 次，${t.javaClass.simpleName}:${t.message} stopReason=$sr isStopped=$isStopped")
                    return null
                }
                FileLog.w(applicationContext, "获取定位异常：第 $i/$maxAttempts 次，${t.javaClass.simpleName}:${t.message}")
            }

            if (i < maxAttempts) delay(800L)
        }
        return null
    }

    private suspend fun getCurrentThenLastOnce(): Location? {
        val client = LocationServices.getFusedLocationProviderClient(applicationContext)

        val current = suspendCancellableCoroutine<Location?> { cont ->
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                .addOnFailureListener { e ->
                    FileLog.w(applicationContext, "currentLocation 失败：${e.javaClass.simpleName}:${e.message}")
                    if (cont.isActive) cont.resume(null)
                }
        }
        if (current != null) return current

        FileLog.w(applicationContext, "currentLocation 返回为空，尝试读取 lastLocation（缓存兜底）")

        val last = suspendCancellableCoroutine<Location?> { cont ->
            client.lastLocation
                .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                .addOnFailureListener { e ->
                    FileLog.w(applicationContext, "lastLocation 失败：${e.javaClass.simpleName}:${e.message}")
                    if (cont.isActive) cont.resume(null)
                }
        }
        return last
    }

    private fun logLocation(loc: Location) {
        val lat = loc.latitude
        val lon = loc.longitude
        val acc = loc.accuracy
        val provider = loc.provider ?: "unknown"
        val ts = loc.time
        val ageMs = max(0L, System.currentTimeMillis() - ts)

        FileLog.i(
            applicationContext,
            "定位结果：纬度=$lat，经度=$lon，精度≈${acc}米，来源=$provider，时间戳=$ts，点龄≈${ageMs}ms"
        )
    }

    private suspend fun uploadWithMaxAttempts(maxAttempts: Int, urlStr: String, loc: Location): Boolean {
        for (i in 1..maxAttempts) {
            try {
                FileLog.i(applicationContext, "开始上报轨迹：第 $i/$maxAttempts 次")
                val ok = postLocation(urlStr, loc)
                if (ok) {
                    FileLog.i(applicationContext, "上报成功：第 $i/$maxAttempts 次")
                    return true
                }
                FileLog.w(applicationContext, "上报失败：第 $i/$maxAttempts 次（HTTP非2xx）")
            } catch (t: Throwable) {
                if (t is CancellationException) {
                    val sr = try { stopReason } catch (_: Throwable) { -1 }
                    FileLog.w(applicationContext, "上报被取消：第 $i/$maxAttempts 次，stopReason=$sr isStopped=$isStopped")
                    return false
                }
                FileLog.w(applicationContext, "上报异常：第 $i/$maxAttempts 次，${t.javaClass.simpleName}:${t.message}")
            }
            if (i < maxAttempts) delay(1000L * i)
        }
        return false
    }

    private fun postLocation(urlStr: String, loc: Location): Boolean {
        val payload = JSONObject().apply {
            put("lat", loc.latitude)
            put("lon", loc.longitude)
            put("acc", loc.accuracy)
            put("ts", loc.time)
            put("provider", loc.provider ?: "unknown")
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