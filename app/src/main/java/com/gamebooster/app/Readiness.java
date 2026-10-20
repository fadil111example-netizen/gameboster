package com.gamebooster.app;

import android.app.ActivityManager;
import android.content.Context;
import android.os.PowerManager;

import java.util.ArrayList;
import java.util.List;

/** Skor 0-100: seberapa siap perangkat untuk bermain sekarang (heuristik dari data nyata perangkat). */
public final class Readiness {
    private Readiness() {}

    public static final class Result {
        public int score;
        public String title = "";
        public int color = 0xFFFFFFFF;
        public final List<String> tips = new ArrayList<>();
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    public static Result compute(Context c, Prefs prefs) {
        Result r = new Result();
        float total = 0f;

        // RAM bebas (30)
        ActivityManager.MemoryInfo mi = Booster.mem(c);
        float freeRatio = mi.availMem / (float) mi.totalMem;
        float ramScore = clamp01((freeRatio - 0.10f) / 0.35f) * 30f;
        total += ramScore;
        if (freeRatio < 0.25f) r.tips.add("💾 RAM bebas tinggal " + Math.round(freeRatio * 100) + "% — tekan BOOST dulu");

        // Suhu (25)
        float bt = Booster.batteryTemp(c);
        int ts = ThermalInfo.status(c);
        float tempScore = 25f;
        if (bt > 0) tempScore = clamp01((42f - bt) / 8f) * 25f;
        if (ts >= 2) tempScore = Math.min(tempScore, 5f);
        else if (ts == 1) tempScore = Math.min(tempScore, 15f);
        total += tempScore;
        if (ts >= 2) r.tips.add("🔥 Sistem sudah men-throttle (" + ThermalInfo.statusName(ts) + ") — dinginkan dulu");
        else if (bt >= prefs.threshold()) r.tips.add("🌡 Suhu " + String.format(java.util.Locale.US, "%.1f", bt) + "°C — istirahat sebentar");

        // Baterai (15)
        float pct = Booster.batteryPct(c);
        boolean charging = Booster.isCharging(c);
        float battScore = 15f;
        if (pct >= 0) {
            if (pct < 20f && !charging) battScore = 3f;
            else if (pct < 40f && !charging) battScore = 9f;
        }
        total += battScore;
        if (pct >= 0 && pct < 20f && !charging) r.tips.add("🔋 Baterai " + Math.round(pct) + "% — colok charger");
        if (charging && bt >= 37f) r.tips.add("🔌 Mengisi daya sambil main menambah panas");

        // Penghemat Baterai Android (10)
        float psScore = 10f;
        try {
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            if (pm != null && pm.isPowerSaveMode()) {
                psScore = 0f;
                r.tips.add("🪫 Penghemat Baterai Android aktif — membatasi performa");
            }
        } catch (Exception ignored) {
        }
        total += psScore;

        // Penyimpanan (10)
        float freeGb = Booster.gb(Booster.storageFree());
        float stScore = clamp01((freeGb - 1f) / 4f) * 10f;
        total += stScore;
        if (freeGb < 2f) r.tips.add("📦 Penyimpanan tinggal " + String.format(java.util.Locale.US, "%.1f", freeGb) + " GB");

        // Jaringan (10)
        int ping = PingMonitor.last();
        float netScore = 7f;
        if (ping >= 0) netScore = clamp01((220f - ping) / 160f) * 10f;
        total += netScore;
        if (ping > 150) r.tips.add("📶 Ping tinggi (" + ping + " ms) — cek koneksi");

        r.score = Math.round(Math.max(0f, Math.min(100f, total)));
        if (r.score >= 85) {
            r.title = "🔥 Siap tempur";
            r.color = 0xFF4ADE80;
        } else if (r.score >= 65) {
            r.title = "👍 Cukup siap";
            r.color = 0xFF22D3EE;
        } else if (r.score >= 40) {
            r.title = "⚠️ Kurang optimal";
            r.color = 0xFFFACC15;
        } else {
            r.title = "🛑 Belum siap";
            r.color = 0xFFF87171;
        }
        if (r.tips.isEmpty()) r.tips.add("Semua indikator bagus. Selamat bermain! 🎮");
        while (r.tips.size() > 3) r.tips.remove(r.tips.size() - 1);
        return r;
    }
}
