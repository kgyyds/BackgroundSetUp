package com.system.timeup

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LocationFgService : Service() {

    companion object {
        private const val CH_ID = "timeup_loc"
        private const val NOTIF_ID = 1001

        fun start(context: Context, reason: String) {
            val i = Intent(context, LocationFgService::class.java).apply {
                putExtra("reason", reason)
            }
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val reason = intent?.getStringExtra("reason") ?: "未知原因"
        val notif = buildLowKeyNotification("正在获取定位…")
        startForeground(NOTIF_ID, notif)

        FileLog.i(this, "前台定位服务启动：原因=$reason（低调通知，获取到即退出）")

        scope.launch {
            try {
                val loc = withTimeoutOrNull(15_000L) { getOneShotLocation() }
                if (loc == null) {
                    FileLog.w(this@LocationFgService, "前台定位失败：15秒内未拿到定位点（可能室内/系统限制）")
                } else {
                    FileLog.i(
                        this@LocationFgService,
                        "前台定位成功：纬度=${loc.latitude} 经度=${loc.longitude} 精度≈${loc.accuracy}m"
                    )
                    // 交给 Worker 上报（带重试策略），也可以改成直接在服务里上传
                    SyncWorker.enqueueOnce(this@LocationFgService, reason = "前台定位:$reason", loc = loc)
                }
            } catch (t: Throwable) {
                FileLog.e(this@LocationFgService, "前台定位异常：${t.javaClass.simpleName}:${t.message}")
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                FileLog.i(this@LocationFgService, "前台定位服务结束：通知已移除")
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun getOneShotLocation(): Location? {
        val client = LocationServices.getFusedLocationProviderClient(this)
        return suspendCancellableCoroutine { cont ->
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                .addOnFailureListener { e ->
                    FileLog.w(this, "getCurrentLocation失败：${e.javaClass.simpleName}:${e.message}")
                    if (cont.isActive) cont.resume(null)
                }
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(
                CH_ID,
                "TimeUp 定位服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                description = "用于短时间获取定位并立即结束"
            }
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildLowKeyNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CH_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("TimeUp")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}