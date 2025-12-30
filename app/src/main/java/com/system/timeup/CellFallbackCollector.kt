package com.system.timeup

import android.content.Context
import android.os.Build
import android.telephony.*
import androidx.annotation.RequiresApi

object CellFallbackCollector {

    data class CellSnapshot(
        val timestamp: Long,
        val networkOperator: String?,
        val cells: List<CellInfoSnapshot>
    )

    data class CellInfoSnapshot(
        val type: String,
        val registered: Boolean,
        val mcc: Int?,
        val mnc: Int?,
        val lac: Int?,
        val tac: Int?,
        val cid: Long?,
        val pci: Int?,
        val arfcn: Int?,
        val signalDbm: Int?
    )

    fun collect(
        context: Context,
        registeredOnly: Boolean = true
    ): CellSnapshot? {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return null

        val allCells = try {
            tm.allCellInfo
        } catch (e: SecurityException) {
            FileLog.w(context, "基站采集：无定位权限")
            return null
        }

        if (allCells.isNullOrEmpty()) {
            FileLog.w(context, "基站采集：allCellInfo 为空")
            return null
        }

        val snapshots = mutableListOf<CellInfoSnapshot>()

        for (cell in allCells) {
            if (registeredOnly && !cell.isRegistered) continue

            when (cell) {
                is CellInfoLte -> snapshots += parseLte(cell)
                is CellInfoNr -> snapshots += parseNr(cell)
                is CellInfoWcdma -> snapshots += parseWcdma(cell)
                is CellInfoGsm -> snapshots += parseGsm(cell)
                is CellInfoCdma -> snapshots += parseCdma(cell)
            }
        }

        return CellSnapshot(
            timestamp = System.currentTimeMillis(),
            networkOperator = tm.networkOperator,
            cells = snapshots
        )
    }

    // ====================== 各制式解析 ======================

    private fun parseLte(c: CellInfoLte): CellInfoSnapshot {
        val id = c.cellIdentity
        val ss = c.cellSignalStrength
        return CellInfoSnapshot(
            type = "LTE",
            registered = c.isRegistered,
            mcc = id.mcc,
            mnc = id.mnc,
            lac = null,
            tac = id.tac,
            cid = id.ci.toLong(),
            pci = id.pci,
            arfcn = id.earfcn,
            signalDbm = ss.dbm
        )
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun parseNr(c: CellInfoNr): CellInfoSnapshot {
        val id = c.cellIdentity as CellIdentityNr
        val ss = c.cellSignalStrength as CellSignalStrengthNr
        return CellInfoSnapshot(
            type = "NR",
            registered = c.isRegistered,
            mcc = id.mccString?.toIntOrNull(),
            mnc = id.mncString?.toIntOrNull(),
            lac = null,
            tac = id.tac,
            cid = id.nci,
            pci = id.pci,
            arfcn = id.nrarfcn,
            signalDbm = ss.dbm
        )
    }

    private fun parseWcdma(c: CellInfoWcdma): CellInfoSnapshot {
        val id = c.cellIdentity
        val ss = c.cellSignalStrength
        return CellInfoSnapshot(
            type = "WCDMA",
            registered = c.isRegistered,
            mcc = id.mcc,
            mnc = id.mnc,
            lac = id.lac,
            tac = null,
            cid = id.cid.toLong(),
            pci = id.psc,
            arfcn = null,
            signalDbm = ss.dbm
        )
    }

    private fun parseGsm(c: CellInfoGsm): CellInfoSnapshot {
        val id = c.cellIdentity
        val ss = c.cellSignalStrength
        return CellInfoSnapshot(
            type = "GSM",
            registered = c.isRegistered,
            mcc = id.mcc,
            mnc = id.mnc,
            lac = id.lac,
            tac = null,
            cid = id.cid.toLong(),
            pci = null,
            arfcn = id.arfcn,
            signalDbm = ss.dbm
        )
    }

    private fun parseCdma(c: CellInfoCdma): CellInfoSnapshot {
        val id = c.cellIdentity
        val ss = c.cellSignalStrength
        return CellInfoSnapshot(
            type = "CDMA",
            registered = c.isRegistered,
            mcc = null,
            mnc = null,
            lac = id.networkId,
            tac = null,
            cid = id.basestationId.toLong(),
            pci = null,
            arfcn = null,
            signalDbm = ss.dbm
        )
    }
}