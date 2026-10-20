package com.gamebooster.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Choreographer;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

import java.util.Locale;
import java.util.Set;

/**
 * Overlay monitor melayang di atas aplikasi/game: FPS*, suhu, RAM bebas, jank.
 * FPS* = kelancaran frame sistem yang diukur lewat Choreographer (BUKAN FPS internal game;
 * itu hanya bisa dibaca dengan root/Shizuku).
 */
public class OverlayService extends Service {

    public static final String EXTRA_AUTO = "auto";
    public static final String ACTION_STOP = "com.gamebooster.app.STOP_OVERLAY";

    private static final String CHANNEL = "overlay";
    private static final int NOTIF_ID = 1002;

    private static volatile boolean running = false;
    private static volatile boolean auto = false;

    public static boolean isRunning() {
        return running;
    }

    public static boolean isAuto() {
        return auto;
    }

    private WindowManager wm;
    private NotificationManager nm;
    private Handler handler;
    private Prefs prefs;
    private View root;
    private WindowManager.LayoutParams lp;
    private TextView tvFps, tvDetail;
    private boolean expanded = true;
    private int tick = 0;
    private volatile boolean boostBusy = false;
    private String boostMsg = null;
    private long boostMsgUntil = 0;

    private float refreshHz = 60f;
    private long frameNanos = 16666666L;
    private long lastFrameNanos = 0;
    private int frames = 0;
    private int jank = 0;
    private long lastStat = 0;

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            frames++;
            if (lastFrameNanos != 0 && frameTimeNanos - lastFrameNanos > frameNanos * 3 / 2) jank++;
            lastFrameNanos = frameTimeNanos;
            if (root != null) Choreographer.getInstance().postFrameCallback(this);
        }
    };

    private final Runnable statsLoop = new Runnable() {
        @Override
        public void run() {
            updateStats();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        prefs = new Prefs(this);
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // startForeground WAJIB dipanggil dulu agar tidak crash
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }

        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        running = true;
        auto = intent != null && intent.getBooleanExtra(EXTRA_AUTO, false);

        if (!canDraw()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (root == null) addOverlay();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(statsLoop);
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        if (root != null) {
            try {
                wm.removeView(root);
            } catch (Exception ignored) {
            }
            root = null;
        }
        running = false;
        auto = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private boolean canDraw() {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private float displayHz() {
        try {
            DisplayManager dm = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
            Display d = dm.getDisplay(Display.DEFAULT_DISPLAY);
            if (d != null) return d.getRefreshRate();
        } catch (Exception ignored) {
        }
        return 60f;
    }

    private void addOverlay() {
        try {
            refreshHz = Math.max(30f, displayHz());
            frameNanos = (long) (1000000000L / refreshHz);

            root = LayoutInflater.from(this).inflate(R.layout.overlay_monitor, null);
            tvFps = root.findViewById(R.id.ovFps);
            tvDetail = root.findViewById(R.id.ovDetail);
            root.findViewById(R.id.ovClose).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    stopSelf();
                }
            });

            root.findViewById(R.id.ovBoost).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    quickBoost();
                }
            });

            int type = Build.VERSION.SDK_INT >= 26
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.x = prefs.overlayX() >= 0 ? prefs.overlayX() : dp(12);
            lp.y = prefs.overlayY() >= 0 ? prefs.overlayY() : dp(80);

            root.setOnTouchListener(new DragListener());
            wm.addView(root, lp);

            frames = 0;
            jank = 0;
            lastFrameNanos = 0;
            lastStat = SystemClock.elapsedRealtime();
            Choreographer.getInstance().postFrameCallback(frameCallback);
            handler.removeCallbacks(statsLoop);
            handler.postDelayed(statsLoop, 1000);
        } catch (Exception e) {
            root = null;
            stopSelf();
        }
    }

    private void updateStats() {
        if (root == null) return;
        tick++;
        if (tick % 3 == 1) PingMonitor.poke();
        long now = SystemClock.elapsedRealtime();
        long dt = Math.max(1, now - lastStat);
        float fps = frames * 1000f / dt;
        int j = jank;
        frames = 0;
        jank = 0;
        lastStat = now;

        int color;
        if (fps >= refreshHz * 0.9f) color = 0xFF4ADE80;
        else if (fps >= refreshHz * 0.6f) color = 0xFFFACC15;
        else color = 0xFFF87171;
        tvFps.setText("⚡ " + Math.round(fps) + " FPS*");
        tvFps.setTextColor(color);

        float bt = Booster.batteryTemp(this);
        float ct = ThermalReader.cpuTemp();
        StringBuilder sb = new StringBuilder();
        sb.append("🌡 ").append(bt > 0 ? String.format(Locale.US, "%.1f°C", bt) : "--");
        if (ct > 0) sb.append("   🧠 ").append(String.format(Locale.US, "%.0f°C", ct));
        int ths = ThermalInfo.status(this);
        if (ths >= 1) sb.append("   🔥 ").append(ThermalInfo.statusName(ths));
        android.app.ActivityManager.MemoryInfo mi = Booster.mem(this);
        int ramPct = Math.round((mi.totalMem - mi.availMem) * 100f / mi.totalMem);
        sb.append("\n💾 ").append(Booster.fmt(mi.availMem)).append(" bebas • RAM ").append(ramPct).append("%");
        int ping = PingMonitor.last();
        sb.append("\n📶 ").append(ping >= 0 ? ping + " ms" : "-- ms")
                .append(" • ").append(PingMonitor.networkType(this));
        sb.append("\n⚠ jank ").append(j);
        if (boostMsg != null && SystemClock.elapsedRealtime() < boostMsgUntil) {
            sb.append("\n").append(boostMsg);
        }
        tvDetail.setText(sb.toString());
    }

    /** Seret untuk memindah, ketuk untuk kecilkan/besarkan. */
    private class DragListener implements View.OnTouchListener {
        private int startX, startY;
        private float touchX, touchY;
        private boolean moved;
        private final int slop = ViewConfiguration.get(OverlayService.this).getScaledTouchSlop();

        @Override
        public boolean onTouch(View v, MotionEvent ev) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX = lp.x;
                    startY = lp.y;
                    touchX = ev.getRawX();
                    touchY = ev.getRawY();
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = ev.getRawX() - touchX;
                    float dy = ev.getRawY() - touchY;
                    if (Math.abs(dx) > slop || Math.abs(dy) > slop) moved = true;
                    if (moved) {
                        DisplayMetrics dm = getResources().getDisplayMetrics();
                        lp.x = Math.max(0, Math.min(dm.widthPixels - v.getWidth(), startX + (int) dx));
                        lp.y = Math.max(0, Math.min(dm.heightPixels - v.getHeight(), startY + (int) dy));
                        try {
                            wm.updateViewLayout(root, lp);
                        } catch (Exception ignored) {
                        }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (moved) {
                        prefs.setOverlayPos(lp.x, lp.y);
                    } else {
                        expanded = !expanded;
                        tvDetail.setVisibility(expanded ? View.VISIBLE : View.GONE);
                    }
                    return true;
                default:
                    return true;
            }
        }
    }

    /** Tombol 🚀 di overlay: bersihkan RAM & dinginkan tanpa meninggalkan game. */
    private void quickBoost() {
        if (boostBusy) return;
        boostBusy = true;
        boostMsg = "⏳ Membersihkan…";
        boostMsgUntil = SystemClock.elapsedRealtime() + 15000;
        final Context app = getApplicationContext();
        final Set<String> keep = prefs.games();
        final long before = Booster.mem(app).availMem;
        new Thread(new Runnable() {
            @Override
            public void run() {
                Booster.Result r = Booster.optimize(app, keep, true, null);
                try {
                    Thread.sleep(600);
                } catch (InterruptedException ignored) {
                }
                long freed = Math.max(0, Booster.mem(app).availMem - before);
                final String msg = "✅ +" + Booster.fmt(freed) + " • " + r.apps + " app";
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        boostMsg = msg;
                        boostMsgUntil = SystemClock.elapsedRealtime() + 5000;
                        boostBusy = false;
                    }
                });
            }
        }).start();
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE);

        Intent stop = new Intent(this, OverlayService.class);
        stop.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("Monitor Overlay Aktif")
                .setContentText("Menampilkan FPS*, suhu & RAM di atas layar")
                .setContentIntent(openPi)
                .addAction(0, "Tutup", stopPi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Monitor Overlay",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Overlay FPS, suhu & RAM saat bermain");
            nm.createNotificationChannel(ch);
        }
    }
}
