package com.system.timeup

import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log

class SetupActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i(TAG, "SetupActivity -> schedule alarm, hide icon, finish")

        // 第一次快速触发：1分钟后打一次log，方便你确认链路OK
        AlarmScheduler.scheduleNext(this, 60_000L)

        disableLauncherAlias()
        finish()
    }

    private fun disableLauncherAlias() {
        val cn = ComponentName(this, "com.system.timeup.LauncherAlias")
        packageManager.setComponentEnabledSetting(
            cn,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        Log.i(TAG, "LauncherAlias disabled -> icon may disappear after launcher refresh")
    }

    companion object {
        private const val TAG = "TimeUpSetup"
    }
}