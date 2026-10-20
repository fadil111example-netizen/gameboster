package com.gamebooster.app;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;

/** Mendeteksi aplikasi yang sedang di layar lewat Usage Access (izin khusus dari pengguna). */
public final class GameDetector {
    private GameDetector() {}

    private static String lastFg = null;
    private static long lastQuery = 0;

    public static boolean hasAccess(Context c) {
        try {
            AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            @SuppressWarnings("deprecation")
            int mode = ops.checkOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), c.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    /** Paket aplikasi terakhir yang dibuka ke layar depan (null jika belum diketahui). */
    @SuppressWarnings("deprecation")
    public static String foreground(Context c) {
        try {
            UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return lastFg;
            long now = System.currentTimeMillis();
            long from = lastQuery == 0 ? now - 60000L : Math.max(lastQuery - 1000L, now - 120000L);
            UsageEvents ev = usm.queryEvents(from, now);
            UsageEvents.Event e = new UsageEvents.Event();
            while (ev != null && ev.hasNextEvent()) {
                ev.getNextEvent(e);
                if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    lastFg = e.getPackageName();
                }
            }
            lastQuery = now;
        } catch (Exception ignored) {
        }
        return lastFg;
    }
}
