package com.system.timeup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        Log.i(TAG, "BootReceiver action=${intent?.action} -> ensure scheduled")
        TickScheduler.schedule(context)
    }

    companion object {
        private const val TAG = "TimeUpBoot"
    }
}