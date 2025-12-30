package com.system.timeup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object WorkKick {
    private const val UNIQUE_PERIODIC = "timeup_periodic_sync"
    private const val UNIQUE_FGS_ONE_TIME = "timeup_one_time_fgs_start"

    /**
     * 周期保险：系统限制最小15分钟
     */
    fun ensurePeriodic(context: Context) {
        val app = context.applicationContext

        val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setInputData(workDataOf("reason" to "周期保险(15分钟)"))
            .build()

        WorkManager.getInstance(app).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            req
        )

        FileLog.i(app, "已确保周期保险任务存在（15分钟一次，系统限制）")
    }

    /**
     * 闹钟触发用：投递一次性Work，由Work去启动前台定位服务（更稳）
     *
     * ✅ KEEP：如果上一次还没跑完，就不再投递新的，避免互相取消/抢资源
     */
    fun kickFgsNow(context: Context, reason: String) {
        val app = context.applicationContext

        val req = OneTimeWorkRequestBuilder<FgStarterWorker>()
            .setInputData(workDataOf("reason" to reason))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(app).enqueueUniqueWork(
            UNIQUE_FGS_ONE_TIME,
            ExistingWorkPolicy.KEEP,
            req
        )

        FileLog.i(app, "已投递一次性任务：Work→启动前台定位服务（KEEP）原因=$reason")
    }
}