package com.system.timeup

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object TickScheduler {
    private const val UNIQUE_WORK_NAME = "tick_15m_work"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<TickWorker>(15, TimeUnit.MINUTES)
            // 你后续做联网上传时，可开启网络约束
            // .setConstraints(
            //     Constraints.Builder()
            //         .setRequiredNetworkType(NetworkType.CONNECTED)
            //         .build()
            // )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE, // 稳定：确保约束/逻辑更新后也生效
            request
        )
    }
}