package com.system.timeup

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TickWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ts = sdf.format(Date())
        Log.i(TAG, "Tick! time=$ts  runAttemptCount=$runAttemptCount")
        return Result.success()
    }

    companion object {
        private const val TAG = "TimeUpTick"
        private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    }
}