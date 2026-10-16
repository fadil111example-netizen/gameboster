package com.gameboost.app.core

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.gameboost.app.service.OverlayService

object GameSession {

    fun dndGranted(ctx: Context): Boolean =
        ctx.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    fun overlayGranted(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    /** Boost (opsional) -> Jangan Ganggu -> overlay monitor -> buka game. */
    suspend fun launch(ctx: Context, pkg: String): BoostResult? {
        val app = ctx.applicationContext
        val prefs = Prefs(app)
        val result = if (prefs.boostOnLaunch) Booster.boost(app) else null
        start(app)
        app.packageManager.getLaunchIntentForPackage(pkg)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(it)
        }
        return result
    }

    fun start(ctx: Context) {
        val app = ctx.applicationContext
        val prefs = Prefs(app)

        if (prefs.dnd && dndGranted(app)) {
            val nm = app.getSystemService(NotificationManager::class.java)
            if (prefs.prevFilter == -1) prefs.prevFilter = nm.currentInterruptionFilter
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        }
        prefs.sessionActive = true

        if (prefs.overlay && overlayGranted(app)) {
            ContextCompat.startForegroundService(app, Intent(app, OverlayService::class.java))
        }
    }

    fun end(ctx: Context) {
        val app = ctx.applicationContext
        val prefs = Prefs(app)

        if (prefs.prevFilter != -1 && dndGranted(app)) {
            app.getSystemService(NotificationManager::class.java)
                .setInterruptionFilter(prefs.prevFilter)
        }
        prefs.prevFilter = -1
        prefs.sessionActive = false
        app.stopService(Intent(app, OverlayService::class.java))
    }
}
