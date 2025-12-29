package com.system.timeup

import android.content.Context

object Persist {
    private const val SP = "timeup_sp"

    fun setLastWorkStart(ctx: Context, ts: Long) {
        ctx.getSharedPreferences(SP, Context.MODE_PRIVATE)
            .edit().putLong("last_work_start", ts).apply()
    }
}