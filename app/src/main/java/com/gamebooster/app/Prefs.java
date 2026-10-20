package com.gamebooster.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public class Prefs {
    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getSharedPreferences("gb", Context.MODE_PRIVATE);
    }

    // ---- Daftar game ----
    public Set<String> games() {
        return new HashSet<>(sp.getStringSet("games", new HashSet<String>()));
    }

    public void setGames(Set<String> s) {
        sp.edit().putStringSet("games", new HashSet<>(s)).apply();
    }

    // ---- Do Not Disturb ----
    public boolean dnd() {
        return sp.getBoolean("dnd", false);
    }

    public void setDnd(boolean v) {
        sp.edit().putBoolean("dnd", v).apply();
    }

    public boolean dndApplied() {
        return sp.getBoolean("dnd_applied", false);
    }

    public int prevFilter() {
        return sp.getInt("dnd_prev", 1);
    }

    public void setDndApplied(boolean applied, int prev) {
        sp.edit().putBoolean("dnd_applied", applied).putInt("dnd_prev", prev).apply();
    }

    // ---- Auto Cooling ----
    public boolean cooling() {
        return sp.getBoolean("cooling", false);
    }

    public void setCooling(boolean v) {
        sp.edit().putBoolean("cooling", v).apply();
    }

    public int threshold() {
        return sp.getInt("threshold", 39);
    }

    public void setThreshold(int v) {
        sp.edit().putInt("threshold", v).apply();
    }

    // ---- Overlay monitor ----
    public boolean overlay() {
        return sp.getBoolean("overlay", false);
    }

    public void setOverlay(boolean v) {
        sp.edit().putBoolean("overlay", v).apply();
    }

    public int overlayX() {
        return sp.getInt("overlay_x", -1);
    }

    public int overlayY() {
        return sp.getInt("overlay_y", -1);
    }

    public void setOverlayPos(int x, int y) {
        sp.edit().putInt("overlay_x", x).putInt("overlay_y", y).apply();
    }

    // ---- Game Mode ----
    public int mode() {
        return sp.getInt("mode", GameMode.BALANCED);
    }

    public void setMode(int m) {
        sp.edit().putInt("mode", m).apply();
    }

    // ---- Mode per game (-1 = ikut mode global) ----
    public int gameMode(String pkg) {
        return sp.getInt("gm_" + pkg, -1);
    }

    public void setGameMode(String pkg, int mode) {
        if (mode < 0) sp.edit().remove("gm_" + pkg).apply();
        else sp.edit().putInt("gm_" + pkg, mode).apply();
    }

    public int sessionMode() {
        return sp.getInt("session_mode", GameMode.BALANCED);
    }

    public void setSessionMode(int m) {
        sp.edit().putInt("session_mode", m).apply();
    }

    // ---- Sesi game (aktif sejak game diluncurkan sampai kembali ke aplikasi ini) ----
    public void setSession(boolean active) {
        sp.edit().putBoolean("session", active)
                .putLong("session_start", active ? System.currentTimeMillis() : 0L).apply();
    }

    /** Sesi dianggap berakhir otomatis setelah 4 jam agar service tidak hidup selamanya. */
    public boolean sessionActive() {
        if (!sp.getBoolean("session", false)) return false;
        return System.currentTimeMillis() - sp.getLong("session_start", 0L) < 4L * 60L * 60L * 1000L;
    }

    public boolean sessionFlag() {
        return sp.getBoolean("session", false);
    }

    public long sessionStart() {
        return sp.getLong("session_start", 0L);
    }

    // ---- Kecerahan (dipulihkan setelah game) ----
    public boolean brightApplied() {
        return sp.getBoolean("bright_applied", false);
    }

    public int brightPrev() {
        return sp.getInt("bright_prev", 128);
    }

    public int brightModePrev() {
        return sp.getInt("bright_mode_prev", 0);
    }

    public void setBrightApplied(boolean applied, int prev, int modePrev) {
        sp.edit().putBoolean("bright_applied", applied).putInt("bright_prev", prev)
                .putInt("bright_mode_prev", modePrev).apply();
    }

    // ---- Daftar aman (tidak pernah ditutup saat boost) ----
    public Set<String> safe() {
        return new HashSet<>(sp.getStringSet("safe", new HashSet<String>()));
    }

    public void setSafe(Set<String> s) {
        sp.edit().putStringSet("safe", new HashSet<>(s)).apply();
    }

    // ---- Deteksi game otomatis ----
    public boolean autoDetect() {
        return sp.getBoolean("auto_detect", false);
    }

    public void setAutoDetect(boolean v) {
        sp.edit().putBoolean("auto_detect", v).apply();
    }

    public void setAutoSession(boolean auto, String pkg) {
        sp.edit().putBoolean("auto_session", auto).putString("session_pkg", pkg == null ? "" : pkg).apply();
    }

    /** True hanya jika sesi yang sedang berjalan dimulai oleh deteksi otomatis. */
    public boolean autoSession() {
        return sp.getBoolean("auto_session", false) && sp.getBoolean("session", false);
    }

    public String sessionPkg() {
        return sp.getString("session_pkg", "");
    }

    // ---- Optimasi memori 2 tahap ----
    public boolean memTweak() {
        return sp.getBoolean("mem_tweak", true);
    }

    public void setMemTweak(boolean v) {
        sp.edit().putBoolean("mem_tweak", v).apply();
    }
}
