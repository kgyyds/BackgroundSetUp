package com.system.timeup

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class FgStarterWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val reason = inputData.getString("reason") ?: "未知原因"
        FileLog.i(applicationContext, "Work触发：尝试启动前台定位服务，原因=$reason，尝试次数=$runAttemptCount")

        return try {
            LocationFgService.start(applicationContext, reason = reason)
            FileLog.i(applicationContext, "Work已请求启动前台定位服务（系统允许则会短暂显示通知并拿一次定位）")
            Result.success()
        } catch (e: ForegroundServiceStartNotAllowedException) {
            // Android 12+ 常见：系统当前不允许从后台拉起FGS
            FileLog.w(applicationContext, "Work启动前台服务被系统拒绝：${e.message}")
            Result.success()
        } catch (t: Throwable) {
            FileLog.e(applicationContext, "Work启动前台服务异常：${t.javaClass.simpleName}:${t.message}")
            Result.success()
        }
    }
}