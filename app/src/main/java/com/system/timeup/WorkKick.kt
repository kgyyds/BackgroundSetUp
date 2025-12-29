package com.system.timeup

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object WorkKick {
    private const val UNIQUE_ONE_TIME = "timeup_one_time_sync"
    private const val UNIQUE_PERIODIC = "timeup_periodic_sync"

    fun kickNow(context: Context, reason: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf("reason" to reason))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_ONE_TIME,
            ExistingWorkPolicy.REPLACE,
            req
        )

        FileLog.i(context, "WorkKick.kickNow -> enqueued OneTimeWork reason=$reason")
    }

    fun ensurePeriodic(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            req
        )

        FileLog.i(context, "WorkKick.ensurePeriodic -> ensured PeriodicWork (15m)")
    }
}