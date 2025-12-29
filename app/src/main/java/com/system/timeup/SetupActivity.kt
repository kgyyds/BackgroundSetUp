package com.system.timeup

import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log

class SetupActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i(TAG, "SetupActivity opened -> schedule work, hide launcher icon, then finish")

        // 1) 立刻补排 WorkManager 周期任务（解除 stopped state 的同时保证已安排）
        TickScheduler.schedule(this)

        // 2) 隐藏桌面入口（activity-alias）
        disableLauncherAlias()

        // 3) 秒退（无界面）
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