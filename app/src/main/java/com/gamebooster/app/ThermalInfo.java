package com.gamebooster.app;

import android.content.Context;
import android.os.Build;
import android.os.PowerManager;

import java.util.Locale;

/**
 * Thermal API resmi Android: status throttling sistem (Android 10+) dan perkiraan
 * "headroom" termal (Android 11+, 1.0 = batas throttling). Aman di semua versi: mengembalikan nilai netral.
 */
public final class ThermalInfo {
    private ThermalInfo() {}

    private static volatile float cachedHeadroom = Float.NaN;
    private static volatile long cachedAt = 0;

    /** 0 normal, 1 hangat, 2 sedang, 3 berat, 4 kritis, 5 darurat, 6 mati; -1 = tidak didukung. */
    public static int status(Context c) {
        if (Build.VERSION.SDK_INT < 29) return -1;
        try {
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            return pm == null ? -1 : pm.getCurrentThermalStatus();
        } catch (Exception e) {
            return -1;
        }
    }

    /** Perkiraan 10 detik ke depan. NaN jika tidak didukung. Dibatasi 1x per 2 detik sesuai aturan API. */
    public static float headroom(Context c, int forecastSeconds) {
        if (Build.VERSION.SDK_INT < 30) return Float.NaN;
        long now = System.currentTimeMillis();
        if (now - cachedAt < 2000) return cachedHeadroom;
        try {
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            float v = pm == null ? Float.NaN : pm.getThermalHeadroom(forecastSeconds);
            cachedHeadroom = v;
        } catch (Exception e) {
            cachedHeadroom = Float.NaN;
        }
        cachedAt = now;
        return cachedHeadroom;
    }

    public static String statusName(int s) {
        switch (s) {
            case 0:
                return "Normal";
            case 1:
                return "Hangat";
            case 2:
                return "Sedang";
            case 3:
                return "Berat";
            case 4:
                return "Kritis";
            case 5:
                return "Darurat";
            case 6:
                return "Mati";
            default:
                return "tidak didukung";
        }
    }

    public static String describe(Context c) {
        int s = status(c);
        if (s < 0) return "Thermal sistem: tidak didukung (Android 10+)";
        float h = headroom(c, 10);
        String t = "Thermal sistem: " + statusName(s);
        if (!Float.isNaN(h)) t += String.format(Locale.US, " • %.0f%% dari batas throttling", h * 100f);
        return t;
    }
}
