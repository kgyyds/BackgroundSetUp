package com.system.timeup

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object WorkKick {
    private const val UNIQUE_ONE_TIME = "timeup_one_time_sync"
    private const val UNIQUE_PERIODIC = "timeup_periodic_sync"

    fun kickNow(context: Context, reason: String) {
        val app = context.applicationContext
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf("reason" to reason))
            .build()

        // ✅ 不替换正在跑的任务：稳定优先（避免互相取消）
        WorkManager.getInstance(app).enqueueUniqueWork(
            UNIQUE_ONE_TIME,
            ExistingWorkPolicy.KEEP,
            req
        )

        FileLog.i(app, "已投递一次性任务（联网执行）: 原因=$reason（KEEP，避免互相取消）")
    }

    /**
     * 说明：WorkManager 的周期任务最小 15 分钟是系统限制。
     * 所以这里只能做“额外保险”，3分钟节奏主要由 Alarm 保证。
     */
    fun ensurePeriodic(context: Context) {
        val app = context.applicationContext
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setInputData(workDataOf("reason" to "周期保险(15分钟)"))
            .build()

        WorkManager.getInstance(app).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP, // ✅ 稳定优先
            req
        )

        FileLog.i(app, "已确保周期保险任务存在（15分钟一次，系统限制）")
    }
}