package com.system.timeup

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileLog {
    private const val FILE_NAME = "timeup.log"
    private const val MAX_BYTES = 512 * 1024 // 512KB，够用又不爆
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun i(context: Context, msg: String) = write(context, "I", msg)
    fun w(context: Context, msg: String) = write(context, "W", msg)
    fun e(context: Context, msg: String) = write(context, "E", msg)

    @Synchronized
    private fun write(context: Context, level: String, msg: String) {
        val dir = context.filesDir
        val f = File(dir, FILE_NAME)

        // 简易滚动：大于 MAX_BYTES 就改名为 .1，重新写新文件
        if (f.exists() && f.length() > MAX_BYTES) {
            val bak = File(dir, "$FILE_NAME.1")
            if (bak.exists()) bak.delete()
            f.renameTo(bak)
        }

        val line = "${sdf.format(Date())} [$level] $msg\n"
        f.appendText(line)
    }
}