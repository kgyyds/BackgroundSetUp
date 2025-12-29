package com.system.timeup

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object WorkKick {
    private const val UNIQUE_ONE_TIME = "timeup_one_time_sync_single"
    private const val UNIQUE_PERIODIC = "timeup_periodic_sync"

    /**
     * 立刻踢一次：稳定优先
     * - 不加网络约束（避免 stopReason=4 网络约束变更导致Work被系统停止）
     * - KEEP：避免互相替换取消
     */
    fun kickNow(context: Context, reason: String) {
        val app = context.applicationContext

        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf("reason" to reason))
            .build()

        WorkManager.getInstance(app).enqueueUniqueWork(
            UNIQUE_ONE_TIME,
            ExistingWorkPolicy.KEEP,
            req
        )

        FileLog.i(app, "已投递一次性任务：原因=$reason（KEEP，不加网络约束，稳定优先）")
    }

    /**
     * 周期保险（系统最小 15 分钟限制）
     * - 不加网络约束，避免被系统“网络状态波动”直接砍掉
     * - KEEP：稳定优先，不反复更新调度
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

        FileLog.i(app, "已确保周期保险任务存在（15分钟一次，稳定优先，不加网络约束）")
    }
}