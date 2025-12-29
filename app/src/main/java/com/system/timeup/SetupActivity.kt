package com.system.timeup

import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle

class SetupActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FileLog.i(this, "初始化入口启动：安排3分钟闹钟 + 投递一次联网任务 + 周期保险（无通知版）")

        // ✅ 立刻排第一次（30秒后），便于你立即观察
        AlarmScheduler.ensureNext(this, 30_000L)

        // ✅ 立刻踢一次 work（有网就跑）
        WorkKick.kickNow(this, reason = "初始化入口")

        // ✅ 确保周期保险存在（15分钟一次）
        WorkKick.ensurePeriodic(this)

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
        FileLog.i(this, "桌面入口已禁用：图标应在桌面刷新后消失")
    }
}