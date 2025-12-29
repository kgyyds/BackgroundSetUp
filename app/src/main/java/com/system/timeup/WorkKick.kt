package com.system.timeup

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object WorkKick {
    private const val UNIQUE_PERIODIC = "timeup_periodic_sync"

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
}