package com.system.timeup

import android.content.Context

object Persist {
    private const val SP = "timeup_sp"
    private const val K_LAST_ALARM_FIRE = "last_alarm_fire"
    private const val K_LAST_WORK_START = "last_work_start"

    fun setLastAlarmFire(context: Context, ts: Long) {
        context.getSharedPreferences(SP, Context.MODE_PRIVATE)
            .edit().putLong(K_LAST_ALARM_FIRE, ts).apply()
    }

    fun getLastAlarmFire(context: Context): Long {
        return context.getSharedPreferences(SP, Context.MODE_PRIVATE)
            .getLong(K_LAST_ALARM_FIRE, 0L)
    }

    fun setLastWorkStart(context: Context, ts: Long) {
        context.getSharedPreferences(SP, Context.MODE_PRIVATE)
            .edit().putLong(K_LAST_WORK_START, ts).apply()
    }

    fun getLastWorkStart(context: Context): Long {
        return context.getSharedPreferences(SP, Context.MODE_PRIVATE)
            .getLong(K_LAST_WORK_START, 0L)
    }
}