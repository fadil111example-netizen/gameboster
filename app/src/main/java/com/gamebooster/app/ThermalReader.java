package com.gamebooster.app;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * Membaca suhu CPU dari /sys/class/thermal. Banyak perangkat (Android 10+) memblokir akses ini;
 * jika tidak bisa dibaca, fungsi mengembalikan -1 dan aplikasi memakai suhu baterai saja.
 */
public final class ThermalReader {
    private ThermalReader() {}

    private static volatile boolean unavailable = false;

    public static float cpuTemp() {
        if (unavailable) return -1f;
        float best = -1f;
        int readable = 0;
        for (int i = 0; i < 40; i++) {
            File dir = new File("/sys/class/thermal/thermal_zone" + i);
            if (!dir.exists()) continue;
            String type = readLine(new File(dir, "type"));
            String temp = readLine(new File(dir, "temp"));
            if (temp == null) continue;
            readable++;
            if (type == null) continue;
            String t = type.toLowerCase();
            boolean cpuLike = t.contains("cpu") || t.contains("soc") || t.contains("tsens")
                    || t.contains("mtk") || t.contains("big") || t.contains("little");
            if (!cpuLike) continue;
            try {
                float v = Float.parseFloat(temp.trim());
                if (v > 1000f) v = v / 1000f;
                if (v > 10f && v < 120f && v > best) best = v;
            } catch (NumberFormatException ignored) {
            }
        }
        if (readable == 0) unavailable = true;
        return best;
    }

    private static String readLine(File f) {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(f));
            return br.readLine();
        } catch (Exception e) {
            return null;
        } finally {
            try {
                if (br != null) br.close();
            } catch (Exception ignored) {
            }
        }
    }
}
