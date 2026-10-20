package com.gamebooster.app;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

/**
 * Otak latar belakang GameBooster:
 * - Smart Auto-Cooling (polling adaptif, mode agresif, notifikasi statistik suhu).
 * - Perekam sesi game (suhu, RAM, ping, baterai) untuk grafik riwayat.
 * - Deteksi Game Otomatis (Usage Access): boost + overlay + sesi saat game di daftar dibuka dari mana pun.
 * Service berhenti sendiri jika tidak ada tugas.
 */
public class CoolingService extends Service {

    private static final String CHANNEL = "cooling";
    private static final String THERMAL_CHANNEL = "thermal";
    private static final int NOTIF_ID = 1001;
    private static final int THERMAL_ID = 1003;
    private static final int INFO_ID = 1004;
    private static final long COOLDOWN_MS = 60000;
    private static final long RESULT_DELAY_MS = 40000;

    private static volatile boolean running = false;
    private static volatile long coolingUntil = 0;
    private static volatile String lastAction = "";

    public static boolean isRunning() {
        return running;
    }

    public static boolean isCooling() {
        return System.currentTimeMillis() < coolingUntil;
    }

    public static String lastAction() {
        return lastAction;
    }

    private Handler handler;
    private Prefs prefs;
    private NotificationManager nm;
    private long lastRun = 0;
    private long lastPeriodic = 0;
    private long nextDelay = 5000;
    private volatile boolean busy = false;
    private boolean stopping = false;
    private String lastText = "";
    private String autoGame = null;
    private int awayCount = 0;

    private final Runnable loop = new Runnable() {
        @Override
        public void run() {
            try {
                check();
            } catch (Throwable t) {
                // service tidak boleh crash karena satu siklus gagal
                nextDelay = 10000;
            }
            if (!stopping) handler.postDelayed(this, nextDelay);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        prefs = new Prefs(this);
        nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createChannels();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = buildNotification("Memantau perangkat…", false);
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }
        running = true;
        stopping = false;
        nextDelay = 5000;
        handler.removeCallbacks(loop);
        handler.post(loop);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(loop);
        running = false;
        coolingUntil = 0;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private boolean isInteractive() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            return pm == null || pm.isInteractive();
        } catch (Exception e) {
            return true;
        }
    }

    private void check() {
        long now = System.currentTimeMillis();

        // ---- deteksi game otomatis (dijalankan lebih dulu agar status sesi segar) ----
        boolean autoOn = prefs.autoDetect() && GameDetector.hasAccess(this);
        boolean interactive = isInteractive();
        if (autoOn && interactive) detectGames();

        boolean session = prefs.sessionActive();
        GameMode gm = GameMode.of(session ? prefs.sessionMode() : prefs.mode());

        boolean coolingOn = prefs.cooling() || (session && gm.forceCooling);
        boolean periodicOn = session && gm.periodSec > 0;

        if (!coolingOn && !periodicOn && !session && !autoOn) {
            stopping = true;
            stopSelf();
            return;
        }

        final float bt = Booster.batteryTemp(this);
        final float ct = ThermalReader.cpuTemp();
        int th = Math.max(30, prefs.threshold() + (session ? gm.thresholdOffset : 0));

        // Thermal API resmi Android: status throttling + prakiraan 10 detik ke depan
        final int tStatus = ThermalInfo.status(this);
        final float tHead = ThermalInfo.headroom(this, 10);
        boolean predictive = tStatus >= 2 || (!Float.isNaN(tHead) && tHead >= 0.9f);

        boolean hot = coolingOn && (((bt > 0 && bt >= th) || (ct > 0 && ct >= th + 20)) || predictive);
        final boolean critical = coolingOn && (((bt > 0 && bt >= th + 3) || (ct > 0 && ct >= th + 23))
                || tStatus >= 3 || (!Float.isNaN(tHead) && tHead >= 1.0f));

        // ---- rekam sampel sesi game ----
        if (session) {
            try {
                PingMonitor.poke();
                ActivityManager.MemoryInfo mi = Booster.mem(this);
                float ram = (mi.totalMem - mi.availMem) * 100f / mi.totalMem;
                SessionStore.addSample(this, prefs.sessionStart(), bt, ram, ct,
                        PingMonitor.last(), Booster.batteryPct(this));
            } catch (Exception ignored) {
            }
        }

        // ---- polling adaptif (hemat CPU saat dingin) ----
        float maxT = Math.max(bt, ct > 0 ? ct - 20 : 0);
        if (session || hot) nextDelay = 5000;
        else if (bt <= 0) nextDelay = 10000;
        else if (maxT >= th - 3 || (!Float.isNaN(tHead) && tHead >= 0.7f)) nextDelay = 8000;
        else nextDelay = 20000;
        if (autoOn) nextDelay = Math.min(nextDelay, interactive ? 4000 : 15000);

        BoosterWidget.updateAll(this);

        long periodicBase = Math.max(lastPeriodic, prefs.sessionStart());
        boolean periodicDue = periodicOn && now - periodicBase >= gm.periodSec * 1000L;
        String prefix = session ? "🎮 " + gm.emoji + " " + gm.name + " • " : "";

        if (!busy && hot && now - lastRun >= COOLDOWN_MS) {
            busy = true;
            lastRun = now;
            coolingUntil = now + 20000;
            final float tBefore = bt > 0 ? bt : ct;
            update("🔥 " + prefix + "Auto Cooling AKTIF • " + temps(bt, ct), true);
            post(THERMAL_ID, "⚠️ Peringatan Thermal",
                    String.format(Locale.US, "Suhu %.1f°C • ambang %d°C%s — membersihkan aplikasi latar…",
                            tBefore, th, (critical ? " (mode agresif)" : "")
                                    + (predictive ? " • thermal OS: " + ThermalInfo.statusName(tStatus) : "")));
            final Context app = getApplicationContext();
            final Set<String> keep = prefs.games();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Booster.Result r = Booster.optimize(app, keep, true, null);
                    if (critical) {
                        try {
                            Thread.sleep(2500);
                        } catch (InterruptedException ignored) {
                        }
                        Booster.cleanBackground(app, keep);
                        Booster.clearOwnCache(app);
                    }
                    final int apps = r.apps;
                    String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
                    lastAction = "Cooling " + time + " • " + apps + " aplikasi ditutup";
                    busy = false;
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            update("✅ Selesai: " + lastAction, false);
                            scheduleResult(tBefore, apps);
                        }
                    });
                }
            }).start();
        } else if (!busy && periodicDue) {
            busy = true;
            lastPeriodic = now;
            final Context app = getApplicationContext();
            final Set<String> keep = prefs.games();
            final String modeName = gm.name;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    int n = Booster.cleanBackground(app, keep);
                    String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
                    lastAction = modeName + " " + time + " • " + n + " aplikasi dibersihkan";
                    busy = false;
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            update("🧹 " + lastAction, false);
                        }
                    });
                }
            }).start();
        } else if (!isCooling() && !busy) {
            String label;
            if (coolingOn) label = "Siaga • ";
            else if (session) label = "Merekam sesi • ";
            else label = "Menunggu game • ";
            update(prefix + label + temps(bt, ct) + (coolingOn ? " • ambang " + th + "°C" : ""), false);
        }
    }

    // ------------------------------------------------------------ deteksi game otomatis

    private void detectGames() {
        // sesi diakhiri dari luar (mis. pengguna kembali ke aplikasi) -> reset
        if (autoGame != null && !prefs.sessionFlag()) {
            autoGame = null;
            awayCount = 0;
        }
        if (autoGame == null && prefs.autoSession()) autoGame = prefs.sessionPkg();

        String fg = GameDetector.foreground(this);
        if (fg == null) return;

        boolean manualSession = prefs.sessionFlag() && !prefs.autoSession();
        if (autoGame == null) {
            if (!manualSession && prefs.games().contains(fg)) startAutoSession(fg);
            return;
        }

        if (fg.equals(autoGame)) {
            awayCount = 0;
        } else if (!fg.equals("com.android.systemui") && !fg.equals(getPackageName())) {
            awayCount++;
            if (awayCount >= 2) endAutoSession();
        }
    }

    private String labelOf(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    private void startAutoSession(final String pkg) {
        GameMode gm = GameMode.resolve(prefs, pkg);
        final String lbl = labelOf(pkg);
        prefs.setSessionMode(gm.id);
        prefs.setSession(true);
        prefs.setAutoSession(true, pkg);
        SessionStore.begin(getApplicationContext(), prefs.sessionStart(), pkg, lbl, gm.id);
        autoGame = pkg;
        awayCount = 0;

        // Jangan Ganggu
        try {
            if (gm.dnd(prefs.dnd()) && nm.isNotificationPolicyAccessGranted() && !prefs.dndApplied()) {
                int prev = nm.getCurrentInterruptionFilter();
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
                prefs.setDndApplied(true, prev);
            }
        } catch (Exception ignored) {
        }

        // Overlay (bisa ditolak sistem jika service dianggap di latar belakang)
        try {
            boolean canDraw = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
            if (gm.overlay(prefs.overlay()) && canDraw) {
                Intent oi = new Intent(this, OverlayService.class);
                oi.putExtra(OverlayService.EXTRA_AUTO, true);
                ContextCompat.startForegroundService(this, oi);
            }
        } catch (Exception ignored) {
        }

        // Boost otomatis
        final Context app = getApplicationContext();
        final Set<String> keep = prefs.games();
        keep.add(pkg);
        final boolean deep = gm.deep(prefs.memTweak());
        busy = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                Booster.Result r = Booster.optimize(app, keep, deep, null);
                String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
                lastAction = "Auto-boost " + time + " • " + r.apps + " aplikasi ditutup";
                busy = false;
            }
        }).start();

        post(INFO_ID, "🎮 Game terdeteksi: " + lbl,
                gm.emoji + " " + gm.name + " • Boost otomatis, sesi direkam");
    }

    private void endAutoSession() {
        SessionStore.end(getApplicationContext(), prefs.sessionStart());
        prefs.setSession(false);
        prefs.setAutoSession(false, "");
        autoGame = null;
        awayCount = 0;
        if (OverlayService.isRunning() && OverlayService.isAuto()) {
            stopService(new Intent(this, OverlayService.class));
        }
        try {
            if (prefs.dndApplied() && nm.isNotificationPolicyAccessGranted()) {
                nm.setInterruptionFilter(prefs.prevFilter());
                prefs.setDndApplied(false, prefs.prevFilter());
            }
        } catch (Exception ignored) {
        }
        post(INFO_ID, "✅ Sesi game selesai", "Riwayat tersimpan di Riwayat Sesi & Grafik");
    }

    // ------------------------------------------------------------ notifikasi

    /** 40 detik setelah pendinginan: bandingkan suhu dan kirim statistik penurunan suhu. */
    private void scheduleResult(final float before, final int apps) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                float bt = Booster.batteryTemp(CoolingService.this);
                float ct = ThermalReader.cpuTemp();
                float after = before > 0 && bt > 0 ? bt : (ct > 0 ? ct : bt);
                if (after <= 0 || before <= 0) return;
                float diff = after - before;
                String text;
                if (diff <= -0.1f) {
                    text = String.format(Locale.US, "✅ Suhu turun %.1f°C → %.1f°C (−%.1f°C) • %d aplikasi ditutup",
                            before, after, -diff, apps);
                } else if (diff < 0.2f) {
                    text = String.format(Locale.US, "Suhu stabil %.1f°C → %.1f°C • %d aplikasi ditutup",
                            before, after, apps);
                } else {
                    text = String.format(Locale.US, "Suhu naik %.1f°C → %.1f°C. Istirahat sebentar atau lepas casing/charger.",
                            before, after);
                }
                post(THERMAL_ID, "❄️ Hasil Auto Cooling", text);
            }
        }, RESULT_DELAY_MS);
    }

    private String temps(float bt, float ct) {
        String s = "Baterai " + (bt > 0 ? String.format(Locale.US, "%.1f°C", bt) : "--");
        if (ct > 0) s += " • CPU " + String.format(Locale.US, "%.0f°C", ct);
        return s;
    }

    private void update(String text, boolean active) {
        if (text.equals(lastText)) return;
        lastText = text;
        nm.notify(NOTIF_ID, buildNotification(text, active));
    }

    private PendingIntent openIntent() {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification buildNotification(String text, boolean active) {
        return new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(active ? "Auto Cooling Aktif" : "Game Booster")
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(openIntent())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void post(int id, String title, String text) {
        try {
            Notification n = new NotificationCompat.Builder(this, THERMAL_CHANNEL)
                    .setSmallIcon(R.drawable.ic_notif)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                    .setContentIntent(openIntent())
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .setTimeoutAfter(5 * 60 * 1000L)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build();
            nm.notify(id, n);
        } catch (Exception ignored) {
        }
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Game Booster",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Pemantauan suhu, deteksi game & perekam sesi");
            nm.createNotificationChannel(ch);

            NotificationChannel th = new NotificationChannel(THERMAL_CHANNEL, "Peringatan & Info",
                    NotificationManager.IMPORTANCE_LOW);
            th.setDescription("Peringatan suhu tinggi, statistik penurunan suhu & info sesi game");
            nm.createNotificationChannel(th);
        }
    }
}
