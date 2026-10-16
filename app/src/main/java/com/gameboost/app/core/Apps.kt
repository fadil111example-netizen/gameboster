package com.gameboost.app.core

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppItem(
    val pkg: String,
    val label: String,
    val icon: ImageBitmap,
    val isGame: Boolean,
)

@Suppress("DEPRECATION")
private fun ApplicationInfo.looksLikeGame(): Boolean =
    category == ApplicationInfo.CATEGORY_GAME || (flags and ApplicationInfo.FLAG_IS_GAME) != 0

object Apps {

    private fun launcherActivities(ctx: Context) =
        ctx.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).filter { it.activityInfo.packageName != ctx.packageName }
            .distinctBy { it.activityInfo.packageName }

    /** Semua aplikasi yang bisa dibuka, lengkap dengan ikon. Panggil dari thread background. */
    fun launchable(ctx: Context): List<AppItem> {
        val pm = ctx.packageManager
        return launcherActivities(ctx).map { ri ->
            AppItem(
                pkg = ri.activityInfo.packageName,
                label = ri.loadLabel(pm).toString(),
                icon = ri.loadIcon(pm).toBitmap(96, 96).asImageBitmap(),
                isGame = ri.activityInfo.applicationInfo.looksLikeGame(),
            )
        }.sortedBy { it.label.lowercase() }
    }

    /** Paket game di perpustakaan (tanpa memuat ikon, jadi ringan). */
    fun gamePackages(ctx: Context): Set<String> {
        val prefs = Prefs(ctx)
        val auto = launcherActivities(ctx)
            .filter { it.activityInfo.applicationInfo.looksLikeGame() }
            .map { it.activityInfo.packageName }
            .toSet() - prefs.hiddenGames
        return auto + prefs.manualGames
    }
}
