package com.system.timeup

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileLog {
    private const val FILE_NAME = "timeup.log"
    private const val MAX_BYTES = 512 * 1024
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)

    fun i(context: Context, msg: String) = write(context, "信息", msg)
    fun w(context: Context, msg: String) = write(context, "警告", msg)
    fun e(context: Context, msg: String) = write(context, "错误", msg)

    @Synchronized
    private fun write(context: Context, level: String, msg: String) {
        val f = File(context.filesDir, FILE_NAME)
        if (f.exists() && f.length() > MAX_BYTES) {
            val bak = File(context.filesDir, "$FILE_NAME.1")
            if (bak.exists()) bak.delete()
            f.renameTo(bak)
        }
        f.appendText("${sdf.format(Date())} [$level] $msg\n")
    }
}