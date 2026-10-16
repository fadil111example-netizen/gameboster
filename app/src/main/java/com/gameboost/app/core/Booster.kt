package com.gameboost.app.core

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class BoostResult(
    val cleaned: Int,
    val freedMb: Long,
    val ramBefore: Int,
    val ramAfter: Int,
)

object Booster {

    /**
     * Menghentikan proses latar belakang aplikasi pengguna (non-sistem) memakai
     * ActivityManager.killBackgroundProcesses, lalu mengukur RAM yang benar-benar kembali.
     * Game di perpustakaan dan aplikasi ini sendiri tidak disentuh.
     */
    @Suppress("DEPRECATION")
    suspend fun boost(ctx: Context): BoostResult = withContext(Dispatchers.Default) {
        val app = ctx.applicationContext
        val before = SystemStats.memory(app)
        val keep = Apps.gamePackages(app) + app.packageName
        val pm = app.packageManager
        val am = app.getSystemService(ActivityManager::class.java)

        var cleaned = 0
        pm.getInstalledApplications(0).forEach { ai ->
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val stopped = (ai.flags and ApplicationInfo.FLAG_STOPPED) != 0
            if (!isSystem && !stopped && ai.packageName !in keep &&
                pm.getLaunchIntentForPackage(ai.packageName) != null
            ) {
                am.killBackgroundProcesses(ai.packageName)
                cleaned++
            }
        }

        delay(1200) // beri waktu sistem melepas memori sebelum diukur ulang
        val after = SystemStats.memory(app)
        BoostResult(
            cleaned = cleaned,
            freedMb = (after.availMb - before.availMb).coerceAtLeast(0),
            ramBefore = before.percent,
            ramAfter = after.percent,
        )
    }
}
