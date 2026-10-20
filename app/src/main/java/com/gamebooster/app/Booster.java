package com.gamebooster.app;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class Booster {
    private Booster() {}

    public interface Progress {
        void onStage(String text);
    }

    public static final class Result {
        public final int apps;
        public final long cacheBytes;

        Result(int apps, long cacheBytes) {
            this.apps = apps;
            this.cacheBytes = cacheBytes;
        }
    }

    public static ActivityManager.MemoryInfo mem(Context c) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi;
    }

    /**
     * Menutup proses latar belakang aplikasi non-sistem.
     * Aplikasi sendiri dan semua paket di "keep" TIDAK disentuh.
     * Mengembalikan jumlah aplikasi yang diproses.
     */
    public static int cleanBackground(Context c, Set<String> keep) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        PackageManager pm = c.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        Set<String> safe = protectedPackages(c);
        int n = 0;
        for (ApplicationInfo ai : apps) {
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            if ((ai.flags & ApplicationInfo.FLAG_STOPPED) != 0) continue;
            String p = ai.packageName;
            if (p.equals(c.getPackageName())) continue;
            if (keep != null && keep.contains(p)) continue;
            if (safe.contains(p)) continue;
            try {
                am.killBackgroundProcesses(p);
                n++;
            } catch (Exception ignored) {
            }
        }
        return n;
    }

    /**
     * Optimasi memori. Jalankan di THREAD LATAR (ada Thread.sleep).
     * deep = false : 1 tahap (tutup proses latar)
     * deep = true  : 2 tahap (tutup proses -> bersihkan cache & GC -> tutup lagi
     *                untuk menangkap service yang menyalakan dirinya kembali)
     */
    public static Result optimize(Context c, Set<String> keep, boolean deep, Progress p) {
        try {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
        } catch (Exception ignored) {
        }
        if (p != null) p.onStage("🧹 Menutup proses latar…");
        int apps = cleanBackground(c, keep);
        long cache = 0;

        if (deep) {
            if (p != null) p.onStage("🗜️ Membersihkan cache & memori…");
            sleep(500);
            cache = clearOwnCache(c);
            if (p != null) p.onStage("⚙️ Engine: trim memori & GC…");
            engineBoost(c);

            if (p != null) p.onStage("🔁 Pembersihan tahap 2…");
            sleep(300);
            cleanBackground(c, keep);
        }
        return new Result(apps, cache);
    }

    /**
     * Engine tweak: melepas memori heap aplikasi ini (GC dua kali) agar lebih banyak RAM
     * tersisa untuk game. Aman dipanggil dari thread mana pun (jangan ubah prioritas di sini).
     */
    public static void engineBoost(Context c) {
        try {
            System.gc();
            Thread.yield();
            Runtime.getRuntime().gc();
        } catch (Exception ignored) {
        }
    }

    /** Menghapus cache milik aplikasi ini (Android tidak mengizinkan menghapus cache aplikasi lain tanpa root). */
    public static long clearOwnCache(Context c) {
        long freed = 0;
        freed += deleteRecursive(c.getCacheDir(), false);
        File ext = c.getExternalCacheDir();
        if (ext != null) freed += deleteRecursive(ext, false);
        return freed;
    }

    private static long deleteRecursive(File f, boolean deleteSelf) {
        if (f == null || !f.exists()) return 0;
        long size = 0;
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) size += deleteRecursive(k, true);
        }
        if (deleteSelf) {
            long len = f.isFile() ? f.length() : 0;
            if (f.delete()) size += len;
        }
        return size;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    public static float batteryTemp(Context c) {
        try {
            Intent i = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (i == null) return -1f;
            return i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f;
        } catch (Exception e) {
            return -1f;
        }
    }

    /** Paket yang tidak boleh ditutup: Daftar Aman pengguna + launcher default + keyboard aktif. */
    public static Set<String> protectedPackages(Context c) {
        Set<String> s = new HashSet<>(new Prefs(c).safe());
        try {
            PackageManager pm = c.getPackageManager();
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            ResolveInfo ri = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            if (ri != null && ri.activityInfo != null) s.add(ri.activityInfo.packageName);
            String ime = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            if (ime != null) {
                ComponentName cn = ComponentName.unflattenFromString(ime);
                if (cn != null) s.add(cn.getPackageName());
            }
        } catch (Exception ignored) {
        }
        return s;
    }

    public static boolean isCharging(Context c) {
        try {
            Intent i = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (i == null) return false;
            int st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            return st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL;
        } catch (Exception e) {
            return false;
        }
    }

    public static float batteryPct(Context c) {
        try {
            Intent i = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (i == null) return -1f;
            int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            if (level < 0 || scale <= 0) return -1f;
            return level * 100f / scale;
        } catch (Exception e) {
            return -1f;
        }
    }

    public static long storageFree() {
        StatFs s = new StatFs(Environment.getDataDirectory().getPath());
        return s.getAvailableBytes();
    }

    public static float gb(long bytes) {
        return (float) (bytes / (1024.0 * 1024.0 * 1024.0));
    }

    public static String fmt(long bytes) {
        double gb = bytes / (1024.0 * 1024.0 * 1024.0);
        if (gb >= 1.0) return String.format(Locale.US, "%.1f GB", gb);
        return String.format(Locale.US, "%d MB", bytes / (1024 * 1024));
    }
}
