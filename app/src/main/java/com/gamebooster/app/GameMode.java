package com.gamebooster.app;

/** Profil Game Mode. Semua efek memakai fitur Android yang boleh diakses aplikasi biasa (tanpa root). */
public final class GameMode {
    public static final int SAVER = 0;
    public static final int BALANCED = 1;
    public static final int ULTRA = 2;

    public final int id;
    public final String emoji;
    public final String name;
    public final String desc;
    /** -1 = tidak pernah, 0 = ikut switch pengguna, 1 = selalu */
    public final int deepPolicy;
    /** Penyesuaian ambang suhu selama sesi game (negatif = mendinginkan lebih dini) */
    public final int thresholdOffset;
    /** Pembersihan berkala selama sesi (detik), 0 = mati */
    public final int periodSec;
    /** Auto Cooling aktif otomatis selama sesi game walau switch mati */
    public final boolean forceCooling;
    public final boolean forceDnd;
    public final boolean overlayAllowed;
    /** Batas kecerahan (%) selama bermain, -1 = tidak diubah */
    public final int brightnessCap;

    private GameMode(int id, String emoji, String name, String desc, int deepPolicy, int thresholdOffset,
                     int periodSec, boolean forceCooling, boolean forceDnd, boolean overlayAllowed,
                     int brightnessCap) {
        this.id = id;
        this.emoji = emoji;
        this.name = name;
        this.desc = desc;
        this.deepPolicy = deepPolicy;
        this.thresholdOffset = thresholdOffset;
        this.periodSec = periodSec;
        this.forceCooling = forceCooling;
        this.forceDnd = forceDnd;
        this.overlayAllowed = overlayAllowed;
        this.brightnessCap = brightnessCap;
    }

    private static final GameMode[] ALL = new GameMode[]{
            new GameMode(SAVER, "🔋", "Hemat Baterai",
                    "• Bersihkan RAM 1 tahap (ringan)\n"
                            + "• Overlay tidak ditampilkan\n"
                            + "• Kecerahan dibatasi 35% selama bermain (butuh izin ubah pengaturan sistem)\n"
                            + "• Auto Cooling aktif selama sesi, lebih dini (−3°C)",
                    -1, -3, 0, true, false, false, 35),
            new GameMode(BALANCED, "⚖️", "Seimbang",
                    "• Optimasi memori 2 tahap (ikut switch Optimasi Memori)\n"
                            + "• Jangan Ganggu & Overlay sesuai pilihanmu\n"
                            + "• Auto Cooling sesuai pengaturanmu",
                    0, 0, 0, false, false, true, -1),
            new GameMode(ULTRA, "🔥", "Ultra Performance",
                    "• Optimasi memori 2 tahap selalu aktif\n"
                            + "• Pembersihan berkala tiap 90 detik selama bermain\n"
                            + "• Jangan Ganggu otomatis (jika sudah diizinkan)\n"
                            + "• Auto Cooling aktif selama sesi, lebih dini (−2°C) agar tidak throttling",
                    1, -2, 90, true, true, true, -1)
    };

    public static GameMode of(int id) {
        if (id < 0 || id >= ALL.length) return ALL[BALANCED];
        return ALL[id];
    }

    /** Mode efektif untuk sebuah game: mode khusus game jika ada, jika tidak mode global. */
    public static GameMode resolve(Prefs prefs, String pkg) {
        int m = prefs.gameMode(pkg);
        return of(m >= 0 ? m : prefs.mode());
    }

    public boolean deep(boolean userPref) {
        if (deepPolicy < 0) return false;
        if (deepPolicy > 0) return true;
        return userPref;
    }

    public boolean dnd(boolean userPref) {
        return forceDnd || userPref;
    }

    public boolean overlay(boolean userPref) {
        return overlayAllowed && userPref;
    }
}
