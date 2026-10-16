package com.gameboost.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.gameboost.app.MainActivity
import com.gameboost.app.R
import com.gameboost.app.core.GameSession
import com.gameboost.app.core.SystemStats

/** Overlay kecil yang bisa digeser: RAM, suhu baterai, level baterai. Tombol ✕ mengakhiri sesi. */
class OverlayService : Service() {

    private var wm: WindowManager? = null
    private var box: LinearLayout? = null
    private var label: TextView? = null
    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            updateLabel()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            GameSession.end(this)
            return START_NOT_STICKY
        }
        startAsForeground()
        if (box == null) addOverlay()
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Sesi game", NotificationManager.IMPORTANCE_LOW)
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_boost)
            .setContentTitle("Game Mode aktif")
            .setContentText("Ketuk untuk membuka, atau akhiri sesi.")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_stat_boost), "Akhiri sesi", stop
                ).build()
            )
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun addOverlay() {
        if (!Settings.canDrawOverlays(this)) return
        val dp = resources.displayMetrics.density
        val manager = getSystemService(WINDOW_SERVICE) as WindowManager

        val text = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            text = "…"
        }
        val close = TextView(this).apply {
            this.text = "✕"
            setTextColor(Color.parseColor("#FF4081"))
            textSize = 14f
            setPadding((12 * dp).toInt(), 0, 0, 0)
            setOnClickListener { GameSession.end(this@OverlayService) }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * dp).toInt(), (8 * dp).toInt(), (14 * dp).toInt(), (8 * dp).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 40 * dp
                setColor(Color.parseColor("#CC0B0F1A"))
                setStroke((1 * dp).toInt(), Color.parseColor("#5500E5FF"))
            }
            addView(text)
            addView(close)
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (16 * dp).toInt()
            y = (64 * dp).toInt()
        }

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        root.setOnTouchListener { _: View, e: MotionEvent ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x; startY = lp.y
                    touchX = e.rawX; touchY = e.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = startX + (e.rawX - touchX).toInt()
                    lp.y = startY + (e.rawY - touchY).toInt()
                    manager.updateViewLayout(root, lp)
                    true
                }
                else -> false
            }
        }

        manager.addView(root, lp)
        wm = manager
        box = root
        label = text
    }

    private fun updateLabel() {
        val m = SystemStats.memory(this)
        val b = SystemStats.battery(this)
        label?.text = String.format("RAM %d%%  •  %.0f°C  •  %d%%", m.percent, b.tempC, b.level)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        box?.let { runCatching { wm?.removeView(it) } }
        box = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.gameboost.app.STOP"
        private const val CHANNEL = "game_session"
        private const val NOTIF_ID = 42
    }
}
