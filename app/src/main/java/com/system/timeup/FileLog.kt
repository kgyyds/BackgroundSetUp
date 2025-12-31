package com.system.timeup

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileLog {
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    private fun write(ctx: Context, level: String, msg: String) {
        val line = "${sdf.format(Date())} [$level] $msg\n"
        try {
            val f = ctx.getFileStreamPath("timeup.log")
            f.parentFile?.mkdirs()
            f.appendText(line, Charsets.UTF_8)
        } catch (_: Throwable) { }
    }

    fun i(ctx: Context, msg: String) = write(ctx, "信息", msg)
    fun w(ctx: Context, msg: String) = write(ctx, "警告", msg)
    fun e(ctx: Context, msg: String) = write(ctx, "错误", msg)
}

//test