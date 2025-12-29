package com.system.timeup

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
        // ✅ 修双跑：同一时刻只允许一个 Worker 真正执行（稳定优先）
        private val RUNNING = java.util.concurrent.atomic.AtomicBoolean(false)
    }

    override suspend fun doWork(): Result {
        val reason = inputData.getString("reason") ?: "未知原因"

        if (!RUNNING.compareAndSet(false, true)) {
            FileLog.w(applicationContext, "检测到并发触发：本次跳过（稳定优先）。原因=$reason")
            return Result.success()
        }

        return try {
            Persist.setLastWorkStart(applicationContext, System.currentTimeMillis())
            FileLog.i(applicationContext, "轨迹任务开始：原因=$reason，尝试次数=$runAttemptCount")

            if (!hasLocationPermission()) {
                FileLog.w(applicationContext, "未检测到定位权限：请在系统设置给本应用开启定位权限（建议：始终允许）")
                return Result.failure()
            }

            // 1) 定位：最多 2 次，每次 12 秒超时，避免卡死被系统干掉
            val loc = getLocationWithMaxAttempts(maxAttempts = 2)
            if (loc == null) {
                FileLog.w(applicationContext, "定位最终失败：已尝试2次仍获取不到，本轮结束，等待下一次调度")
                return Result.retry()
            }

            logLocation(loc)

            // 2) 网络判断：不让 WorkManager 用 constraint 砍你（我们自己控制）
            if (!isNetworkAvailable()) {
                FileLog.w(applicationContext, "当前无可用网络：本次仅记录定位，不进行上报（等待下次调度）")
                return Result.success()
            }

            // 3) 上报：最多 3 次
            val ok = uploadWithMaxAttempts(
                maxAttempts = 3,
                urlStr = "https://你的域名/track/upload", // TODO 替换成你的接口
                loc = loc
            )

            if (ok) {
                FileLog.i(applicationContext, "轨迹上报成功 ✅")
                Result.success()
            } else {
                FileLog.w(applicationContext, "轨迹上报最终失败：已尝试3次仍失败，等待下一次调度重试")
                Result.retry()
            }
        } catch (t: Throwable) {
            if (t is CancellationException) {
                val sr = try { stopReason } catch (_: Throwable) { -1 }
                FileLog.w(applicationContext, "任务被系统取消：${t.javaClass.simpleName}:${t.message} stopReason=$sr isStopped=$isStopped")
                return Result.retry()
            }
            FileLog.e(applicationContext, "任务异常：${t.javaClass.simpleName}:${t.message}，准备重试")
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

    private fun isNetworkAvailable(): Boolean {
        return try {
            val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * 定位最多尝试 maxAttempts 次：
     * 每次：currentLocation -> fused lastLocation -> LocationManager lastKnown（MIUI兜底）
     */
    private suspend fun getLocationWithMaxAttempts(maxAttempts: Int): Location? {
        for (i in 1..maxAttempts) {
            try {
                FileLog.i(applicationContext, "开始获取定位：第 $i/$maxAttempts 次（current→fused缓存→系统缓存）")

                val loc = withTimeout(12_000L) { getBestOneShotLocation() }
                if (loc != null) {
                    FileLog.i(applicationContext, "获取定位成功：第 $i/$maxAttempts 次")
                    return loc
                }

                FileLog.w(applicationContext, "获取定位失败：第 $i/$maxAttempts 次（全部为空）")
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

    /**
     * 一次性获取“最可能拿到的点”：
     * 1) fused current（新点，最理想）
     * 2) fused last（缓存点）
     * 3) LocationManager lastKnown（系统缓存兜底，MIUI 上经常更有用）
     */
    private suspend fun getBestOneShotLocation(): Location? {
        val client = LocationServices.getFusedLocationProviderClient(applicationContext)

        // 1) currentLocation（一次性）
        val current = suspendCancellableCoroutine<Location?> { cont ->
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                .addOnFailureListener { e ->
                    FileLog.w(applicationContext, "currentLocation 失败：${e.javaClass.simpleName}:${e.message}")
                    if (cont.isActive) cont.resume(null)
                }
        }
        if (current != null) return current

        FileLog.w(applicationContext, "currentLocation 返回为空，尝试 fused lastLocation（缓存兜底）")

        // 2) fused lastLocation（缓存）
        val fusedLast = suspendCancellableCoroutine<Location?> { cont ->
            client.lastLocation
                .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                .addOnFailureListener { e ->
                    FileLog.w(applicationContext, "fused lastLocation 失败：${e.javaClass.simpleName}:${e.message}")
                    if (cont.isActive) cont.resume(null)
                }
        }
        if (fusedLast != null) return fusedLast

        FileLog.w(applicationContext, "fused lastLocation 仍为空，尝试 LocationManager.getLastKnownLocation（系统缓存兜底）")

        // 3) LocationManager lastKnown（系统缓存兜底）
        return getLastKnownFromLocationManager()
    }

    private fun getLastKnownFromLocationManager(): Location? {
        return try {
            val lm = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val gps = runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
            val net = runCatching { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
            val pass = runCatching { lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER) }.getOrNull()

            // 选一个“时间最新”的
            listOfNotNull(gps, net, pass).maxByOrNull { it.time }
        } catch (t: Throwable) {
            FileLog.w(applicationContext, "LocationManager缓存定位读取失败：${t.javaClass.simpleName}:${t.message}")
            null
        }
    }

    private fun logLocation(loc: Location) {
        val ageMs = max(0L, System.currentTimeMillis() - loc.time)
        FileLog.i(
            applicationContext,
            "定位结果：纬度=${loc.latitude} 经度=${loc.longitude} 精度≈${loc.accuracy}m 来源=${loc.provider ?: "unknown"} 点龄≈${ageMs}ms"
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
            conn.outputStream.use { os -> os.write(payload.toByteArray(Charsets.UTF_8)) }
            conn.responseCode in 200..299
        } finally {
            conn.disconnect()
        }
    }
}