package com.gameboost.app.core

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs

private const val MB = 1024L * 1024L
private const val GB = 1024.0 * 1024.0 * 1024.0

data class MemInfo(val totalMb: Long, val availMb: Long) {
    val usedMb: Long get() = (totalMb - availMb).coerceAtLeast(0)
    val percent: Int get() = if (totalMb == 0L) 0 else ((usedMb * 100) / totalMb).toInt()
}

data class BatteryInfo(val level: Int, val tempC: Float, val charging: Boolean)

data class Stats(
    val ramUsedMb: Long,
    val ramTotalMb: Long,
    val ramPercent: Int,
    val storageFreeGb: Double,
    val storageTotalGb: Double,
    val storagePercent: Int,
    val batteryLevel: Int,
    val tempC: Float,
    val charging: Boolean,
    val thermal: Int,
    val cores: Int,
)

object SystemStats {

    fun memory(ctx: Context): MemInfo {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return MemInfo(mi.totalMem / MB, mi.availMem / MB)
    }

    fun battery(ctx: Context): BatteryInfo {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val temp = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else 0
        return BatteryInfo(pct, temp, charging)
    }

    fun thermalStatus(ctx: Context): Int =
        if (Build.VERSION.SDK_INT >= 29)
            ctx.getSystemService(PowerManager::class.java).currentThermalStatus
        else 0

    fun snapshot(ctx: Context): Stats {
        val m = memory(ctx)
        val b = battery(ctx)
        val fs = StatFs(Environment.getDataDirectory().path)
        val total = fs.blockCountLong * fs.blockSizeLong
        val free = fs.availableBytes
        val usedPct = if (total > 0) (((total - free) * 100) / total).toInt() else 0
        return Stats(
            ramUsedMb = m.usedMb,
            ramTotalMb = m.totalMb,
            ramPercent = m.percent,
            storageFreeGb = free / GB,
            storageTotalGb = total / GB,
            storagePercent = usedPct,
            batteryLevel = b.level,
            tempC = b.tempC,
            charging = b.charging,
            thermal = thermalStatus(ctx),
            cores = Runtime.getRuntime().availableProcessors(),
        )
    }
}
