package com.system.timeup

import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reason = intent.getStringExtra("reason") ?: "闹钟触发"
        FileLog.i(context, "闹钟触发：开始一轮调度 reason=$reason")

        // =====================================================
        // 1️⃣ 先续排（最重要：防断链）
        // =====================================================
        try {
            AlarmKick.ensure(context, seconds = 180, reason = "闹钟续排")
            FileLog.i(context, "闹钟：已续排下一次（3分钟）")
        } catch (t: Throwable) {
            FileLog.e(context, "闹钟：续排失败！${t.javaClass.simpleName}:${t.message}")
            // ⚠️ 不 return，本轮还能继续做事
        }

        // =====================================================
        // 2️⃣ 基站兜底采集（无论如何都执行）
        // =====================================================
        try {
            val cellSnap = CellFallbackCollector.collect(context, registeredOnly = true)

            if (cellSnap == null) {
                FileLog.w(context, "基站兜底：未采集到任何基站信息")
            } else {
                FileLog.i(
                    context,
                    "基站兜底：network=${cellSnap.networkOperator} cells=${cellSnap.cells.size}"
                )
                cellSnap.cells.forEachIndexed { index, c ->
                    FileLog.i(
                        context,
                        "基站[$index] type=${c.type} reg=${c.registered} " +
                            "mcc=${c.mcc} mnc=${c.mnc} " +
                            "lac=${c.lac} tac=${c.tac} " +
                            "cid=${c.cid} pci=${c.pci} arfcn=${c.arfcn} " +
                            "dbm=${c.signalDbm}"
                    )
                }
            }
        } catch (t: Throwable) {
            FileLog.w(context, "基站兜底：采集异常（不影响主链）${t.javaClass.simpleName}:${t.message}")
        }

        // =====================================================
        // 3️⃣ 尝试启动前台定位服务（GPS 主力）
        // =====================================================
        try {
            LocationFgService.start(context, reason = "闹钟:$reason")
            FileLog.i(context, "闹钟：已请求启动前台定位服务")
        } catch (e: ForegroundServiceStartNotAllowedException) {
            FileLog.w(context, "闹钟：前台服务被系统拒绝（后台限制）：${e.message}")
            // 👉 没关系，你已经有“基站兜底”了
        } catch (t: Throwable) {
            FileLog.e(context, "闹钟：启动前台服务异常 ${t.javaClass.simpleName}:${t.message}")
        }

        // =====================================================
        // 4️⃣ 周期保险（Work，不负责拉起 FGS）
        // =====================================================
        try {
            WorkKick.ensurePeriodic(context)
        } catch (t: Throwable) {
            FileLog.w(context, "闹钟：确保周期保险失败（不影响主链）${t.javaClass.simpleName}:${t.message}")
        }

        FileLog.i(context, "闹钟：本轮调度结束 reason=$reason")
    }
}