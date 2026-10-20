package com.gamebooster.app;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.Looper;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    public static final String ACTION_PLAY = "com.gamebooster.app.PLAY";
    public static final String EXTRA_PKG = "pkg";

    private static final int MIN_TH = 34;
    private static final int MAX_TH = 46;

    private GaugeView gauge;
    private TextView tvStatus, tvTemp, tvStorage, tvFreeRam, tvEmpty, btnBoost;
    private TextView tvCoolChip, tvCoolState, tvTempDetail, tvThreshold, tvCoolLast;
    private View pulse, pulse2;
    private LinearLayout gamesContainer;
    private SwitchCompat swDnd, swCool, swMem, swOverlay, swAuto;
    private final Map<String, TextView> statViews = new HashMap<>();
    private TextView[] modeBtns;
    private TextView tvScore, tvScoreTitle, tvScoreTips, tvSafeCount;
    private android.widget.ProgressBar scoreBar;
    private final List<ObjectAnimator> infinite = new ArrayList<>();
    private TextView tvModeDesc;
    private Prefs prefs;
    private NotificationManager nm;
    private boolean boosting = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private float shownTemp = 0f, shownStorage = 0f, shownFree = 0f;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!boosting) refreshStats();
            updateCooling();
            PingMonitor.poke();
            updateReadiness();
            BoosterWidget.updateAll(MainActivity.this);
            handler.postDelayed(this, 3000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new Prefs(this);
        nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        gauge = findViewById(R.id.gauge);
        tvStatus = findViewById(R.id.tvStatus);
        tvTemp = findViewById(R.id.tvTemp);
        tvStorage = findViewById(R.id.tvStorage);
        tvFreeRam = findViewById(R.id.tvFreeRam);
        tvEmpty = findViewById(R.id.tvEmpty);
        btnBoost = findViewById(R.id.btnBoost);
        pulse = findViewById(R.id.pulse);
        pulse2 = findViewById(R.id.pulse2);
        gamesContainer = findViewById(R.id.gamesContainer);
        swDnd = findViewById(R.id.swDnd);
        swCool = findViewById(R.id.swCool);
        swMem = findViewById(R.id.swMem);
        tvCoolChip = findViewById(R.id.tvCoolChip);
        tvCoolState = findViewById(R.id.tvCoolState);
        tvTempDetail = findViewById(R.id.tvTempDetail);
        tvThreshold = findViewById(R.id.tvThreshold);
        tvCoolLast = findViewById(R.id.tvCoolLast);

        // --- Do Not Disturb ---
        swDnd.setChecked(prefs.dnd());
        swDnd.setOnCheckedChangeListener((b, on) -> {
            prefs.setDnd(on);
            if (on && !nm.isNotificationPolicyAccessGranted()) {
                Toast.makeText(this, "Izinkan akses Jangan Ganggu untuk Game Booster", Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
            }
        });

        // --- Optimasi memori ---
        swMem.setChecked(prefs.memTweak());
        swMem.setOnCheckedChangeListener((b, on) -> prefs.setMemTweak(on));

        // --- Overlay Monitor ---
        swOverlay = findViewById(R.id.swOverlay);
        final View btnTestOverlay = findViewById(R.id.btnTestOverlay);
        swOverlay.setChecked(prefs.overlay());
        swOverlay.setOnCheckedChangeListener((b, on) -> {
            prefs.setOverlay(on);
            if (on) {
                if (!canOverlay()) requestOverlayPermission();
            } else {
                stopService(new Intent(this, OverlayService.class));
            }
        });
        btnTestOverlay.setOnClickListener(v -> {
            if (!canOverlay()) {
                requestOverlayPermission();
            } else {
                startOverlay(false);
                Toast.makeText(this, "Overlay tampil. Tekan ✕ pada overlay untuk menutup", Toast.LENGTH_LONG).show();
            }
        });
        Anim.press(btnTestOverlay);

        // --- Deteksi Game Otomatis ---
        swAuto = findViewById(R.id.swAuto);
        swAuto.setChecked(prefs.autoDetect());
        swAuto.setOnCheckedChangeListener((b, on) -> {
            prefs.setAutoDetect(on);
            if (on) {
                if (!GameDetector.hasAccess(this)) requestUsageAccess();
                startCooling();
            }
        });

        // --- Game Mode ---
        modeBtns = new TextView[]{
                findViewById(R.id.modeSaver),
                findViewById(R.id.modeBalanced),
                findViewById(R.id.modeUltra)
        };
        tvModeDesc = findViewById(R.id.tvModeDesc);
        for (int i = 0; i < modeBtns.length; i++) {
            final int id = i;
            modeBtns[i].setOnClickListener(v -> selectMode(id, true));
            Anim.press(modeBtns[i]);
        }
        selectMode(prefs.mode(), false);

        // --- Auto Cooling ---
        swCool.setChecked(prefs.cooling());
        swCool.setOnCheckedChangeListener((b, on) -> {
            prefs.setCooling(on);
            if (on) {
                startCooling();
            } else {
                stopService(new Intent(this, CoolingService.class));
            }
            updateCooling();
        });
        tvThreshold.setText(prefs.threshold() + "°C");
        findViewById(R.id.btnMinus).setOnClickListener(v -> changeThreshold(-1));
        findViewById(R.id.btnPlus).setOnClickListener(v -> changeThreshold(1));

        btnBoost.setOnClickListener(v -> doBoost(null));
        findViewById(R.id.btnAdd).setOnClickListener(v -> showPicker());

        Anim.press(btnBoost, findViewById(R.id.btnAdd), findViewById(R.id.btnMinus), findViewById(R.id.btnPlus));

        tvScore = findViewById(R.id.tvScore);
        tvScoreTitle = findViewById(R.id.tvScoreTitle);
        tvScoreTips = findViewById(R.id.tvScoreTips);
        scoreBar = findViewById(R.id.scoreBar);
        tvSafeCount = findViewById(R.id.tvSafeCount);

        View btnSafe = findViewById(R.id.btnSafe);
        btnSafe.setOnClickListener(v -> showSafePicker());
        View btnDiag = findViewById(R.id.btnDiag);
        btnDiag.setOnClickListener(v -> startActivity(new Intent(this, DiagnosticsActivity.class)));
        Anim.press(btnSafe, btnDiag);
        updateSafeCount();

        View btnHistory = findViewById(R.id.btnHistory);
        btnHistory.setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
        Anim.press(btnHistory);

        gauge.setCaption("RAM TERPAKAI");
        refreshStats();
        updateCooling();
        renderGames();
        playIntro();

        // Efek hidup: glow berdenyut + orb melayang
        infinite.add(Anim.pulse(pulse, 0, 1.12f, 1.5f));
        infinite.add(Anim.pulse(pulse2, 900, 1.12f, 1.5f));
        infinite.add(Anim.drift(findViewById(R.id.orb1), dp(30), dp(40), 7000));
        infinite.add(Anim.drift(findViewById(R.id.orb2), dp(40), dp(30), 9000));

        // Pulihkan service jika sebelumnya aktif (mis. setelah aplikasi dimatikan sistem)
        if (prefs.cooling() && !CoolingService.isRunning()) startCooling();

        // Dibuka dari shortcut "Boost & Main"
        if (savedInstanceState == null) handleIntent(getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs.dndApplied() && nm.isNotificationPolicyAccessGranted()) {
            nm.setInterruptionFilter(prefs.prevFilter());
            prefs.setDndApplied(false, prefs.prevFilter());
            Toast.makeText(this, "Mode Jangan Ganggu dikembalikan", Toast.LENGTH_SHORT).show();
        }
        // Kembali dari game: akhiri sesi & pulihkan kecerahan
        // Akhiri sesi: tutup catatan riwayat lebih dulu (butuh waktu mulai sesi)
        if (prefs.sessionFlag()) SessionStore.end(getApplicationContext(), prefs.sessionStart());
        prefs.setSession(false);
        prefs.setAutoSession(false, "");
        loadGameStats();
        restoreBrightness();
        for (ObjectAnimator a : infinite) {
            if (a.isPaused()) a.resume();
        }
        // Overlay otomatis (dari peluncuran game) ditutup saat kembali ke aplikasi ini
        if (OverlayService.isRunning() && OverlayService.isAuto()) {
            stopService(new Intent(this, OverlayService.class));
        }
        // Izin overlay dicabut / ditolak -> matikan switch
        if (prefs.overlay() && !canOverlay()) {
            swOverlay.setChecked(false);
        }
        // Izin Akses Penggunaan dicabut / ditolak -> matikan switch
        if (prefs.autoDetect() && !GameDetector.hasAccess(this)) {
            swAuto.setChecked(false);
        }
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
        for (ObjectAnimator a : infinite) {
            if (a.isRunning()) a.pause();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        for (ObjectAnimator a : infinite) a.cancel();
        infinite.clear();
        super.onDestroy();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_UI_HIDDEN) Booster.clearOwnCache(getApplicationContext());
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ------------------------------------------------------------ animasi

    private void playIntro() {
        LinearLayout content = findViewById(R.id.content);
        for (int i = 0; i < content.getChildCount(); i++) {
            View v = content.getChildAt(i);
            v.setAlpha(0f);
            v.setTranslationY(dp(40));
            v.animate().alpha(1f).translationY(0f)
                    .setStartDelay(70L * i).setDuration(550)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    /** Angka berubah halus (count-up/down) bukan loncat. */
    private void animateNumber(final TextView tv, float from, final float to, final String format) {
        Object old = tv.getTag();
        if (old instanceof ValueAnimator) ((ValueAnimator) old).cancel();
        if (Math.abs(from - to) < 0.05f) {
            tv.setText(String.format(Locale.US, format, to));
            return;
        }
        ValueAnimator a = ValueAnimator.ofFloat(from, to);
        a.setDuration(700);
        a.setInterpolator(new DecelerateInterpolator());
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator va) {
                tv.setText(String.format(Locale.US, format, (float) va.getAnimatedValue()));
            }
        });
        tv.setTag(a);
        a.start();
    }

    // ------------------------------------------------------------ statistik

    private void refreshStats() {
        ActivityManager.MemoryInfo mi = Booster.mem(this);
        long used = mi.totalMem - mi.availMem;
        gauge.animateTo(used * 100f / mi.totalMem, 800);
        gauge.setSub(Booster.fmt(used) + " / " + Booster.fmt(mi.totalMem));

        float free = Booster.gb(mi.availMem);
        animateNumber(tvFreeRam, shownFree, free, "%.1f GB");
        shownFree = free;

        float st = Booster.gb(Booster.storageFree());
        animateNumber(tvStorage, shownStorage, st, "%.1f GB");
        shownStorage = st;

        float t = Booster.batteryTemp(this);
        if (t > 0) {
            animateNumber(tvTemp, shownTemp, t, "%.1f°C");
            shownTemp = t;
            tvTemp.setTextColor(t >= prefs.threshold() ? 0xFFFB923C : 0xFFFFFFFF);
        }
    }

    // ------------------------------------------------------------ skor kesiapan & daftar aman

    private void updateReadiness() {
        Readiness.Result r = Readiness.compute(this, prefs);
        tvScoreTitle.setText(r.title);
        tvScore.setText(String.valueOf(r.score));
        tvScore.setTextColor(r.color);
        scoreBar.setProgress(r.score, true);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < r.tips.size(); i++) {
            if (i > 0) sb.append("\n");
            sb.append("• ").append(r.tips.get(i));
        }
        tvScoreTips.setText(sb.toString());
    }

    private void updateSafeCount() {
        tvSafeCount.setText(prefs.safe().size() + " aplikasi dilindungi (keyboard & launcher otomatis aman)");
    }

    private void showSafePicker() {
        openPicker("🛡  Daftar Aman", prefs.safe(), new GamePickerDialog.OnSave() {
            @Override
            public void onSave(Set<String> selected) {
                prefs.setSafe(selected);
                updateSafeCount();
                Toast.makeText(MainActivity.this, "Daftar Aman disimpan", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ------------------------------------------------------------ auto cooling

    private void changeThreshold(int d) {
        int v = Math.max(MIN_TH, Math.min(MAX_TH, prefs.threshold() + d));
        prefs.setThreshold(v);
        tvThreshold.setText(v + "°C");
        updateCooling();
    }

    private void ensureNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }
    }

    // ------------------------------------------------------------ overlay

    private boolean canOverlay() {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
    }

    private void requestOverlayPermission() {
        Toast.makeText(this, "Izinkan \"Tampil di atas aplikasi lain\" untuk Game Booster", Toast.LENGTH_LONG).show();
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            Toast.makeText(this, "Buka Pengaturan > Aplikasi > Game Booster > Tampil di atas aplikasi lain",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void requestUsageAccess() {
        Toast.makeText(this, "Aktifkan akses untuk Game Booster di halaman Akses Penggunaan", Toast.LENGTH_LONG).show();
        try {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this, "Buka Pengaturan > Keamanan/Privasi > Akses penggunaan", Toast.LENGTH_LONG).show();
        }
    }

    private void startOverlay(boolean auto) {
        ensureNotifPermission();
        try {
            Intent i = new Intent(this, OverlayService.class);
            i.putExtra(OverlayService.EXTRA_AUTO, auto);
            ContextCompat.startForegroundService(this, i);
        } catch (Exception e) {
            Toast.makeText(this, "Gagal menampilkan overlay: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void startCooling() {
        ensureNotifPermission();
        try {
            ContextCompat.startForegroundService(this, new Intent(this, CoolingService.class));
        } catch (Exception e) {
            Toast.makeText(this, "Gagal menjalankan Auto Cooling: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setChip(String text, int textColor, int strokeColor, int fillColor) {
        tvCoolChip.setText(text);
        tvCoolChip.setTextColor(textColor);
        GradientDrawable g = (GradientDrawable) tvCoolChip.getBackground().mutate();
        g.setColor(fillColor);
        g.setStroke(dp(1), strokeColor);
    }

    private void updateCooling() {
        float bt = Booster.batteryTemp(this);
        float ct = ThermalReader.cpuTemp();
        String cpu = ct > 0 ? String.format(Locale.US, "%.0f°C", ct) : "tidak tersedia";
        String bat = bt > 0 ? String.format(Locale.US, "%.1f°C", bt) : "--";
        tvTempDetail.setText("🔋 Baterai " + bat + "   •   🧠 CPU " + cpu + "\n🌡 " + ThermalInfo.describe(this));

        String last = CoolingService.lastAction();
        if (last.length() > 0) tvCoolLast.setText(last);

        if (!prefs.cooling()) {
            tvCoolState.setText("Nonaktif");
            setChip("❄️ Cooling OFF", 0xFFC7D2FE, 0x44FFFFFF, 0x22FFFFFF);
        } else if (CoolingService.isCooling()) {
            tvCoolState.setText("🔥 Mendinginkan perangkat…");
            setChip("🔥 COOLING AKTIF", 0xFFFFEDD5, 0xFFFB923C, 0x44FB923C);
        } else {
            tvCoolState.setText("Siaga • ambang " + prefs.threshold() + "°C");
            setChip("❄️ Cooling siaga", 0xFF67E8F9, 0xFF22D3EE, 0x3322D3EE);
        }
    }

    // ------------------------------------------------------------ game mode

    private void selectMode(int id, boolean userAction) {
        final GameMode gm = GameMode.of(id);
        prefs.setMode(gm.id);
        int[] bg = {R.drawable.bg_mode_saver, R.drawable.bg_mode_balanced, R.drawable.bg_mode_ultra};
        for (int i = 0; i < modeBtns.length; i++) {
            boolean sel = i == gm.id;
            modeBtns[i].setBackgroundResource(sel ? bg[i] : R.drawable.bg_btn_ghost);
            modeBtns[i].setTextColor(sel ? 0xFFFFFFFF : 0xFFC7D2FE);
        }
        final String text = gm.emoji + " " + gm.name + "\n" + gm.desc;
        if (userAction) {
            tvModeDesc.animate().alpha(0f).setDuration(120).withEndAction(new Runnable() {
                @Override
                public void run() {
                    tvModeDesc.setText(text);
                    tvModeDesc.animate().alpha(1f).setDuration(220).start();
                }
            }).start();
        } else {
            tvModeDesc.setText(text);
        }
        if (userAction && gm.brightnessCap >= 0 && !canWriteSettings()) {
            Toast.makeText(this, "Izinkan \"Ubah pengaturan sistem\" agar kecerahan bisa dibatasi", Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {
            }
        }
    }

    private boolean canWriteSettings() {
        return Build.VERSION.SDK_INT < 23 || Settings.System.canWrite(this);
    }

    private void applyBrightness(GameMode gm) {
        if (gm.brightnessCap < 0 || !canWriteSettings() || prefs.brightApplied()) return;
        try {
            ContentResolver cr = getContentResolver();
            int cur = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128);
            int curMode = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            int cap = gm.brightnessCap * 255 / 100;
            if (cur <= cap && curMode == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) return;
            prefs.setBrightApplied(true, cur, curMode);
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, Math.min(cur, cap));
        } catch (Exception ignored) {
        }
    }

    private void restoreBrightness() {
        if (!prefs.brightApplied()) return;
        if (canWriteSettings()) {
            try {
                ContentResolver cr = getContentResolver();
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, prefs.brightModePrev());
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, prefs.brightPrev());
                Toast.makeText(this, "Kecerahan dikembalikan", Toast.LENGTH_SHORT).show();
            } catch (Exception ignored) {
            }
        }
        prefs.setBrightApplied(false, prefs.brightPrev(), prefs.brightModePrev());
    }

    // ------------------------------------------------------------ boost

    private void stage(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvStatus.setText(text);
            }
        });
    }

    private void doBoost(final String launchPkg) {
        if (boosting) return;
        boosting = true;
        tvStatus.setText("🔍 Memindai aplikasi…");
        btnBoost.setText("⏳  MENGOPTIMALKAN…");
        gauge.setCaption("MENGOPTIMALKAN");
        gauge.setScanning(true);
        gauge.animateTo(100f, 700);

        final Context app = getApplicationContext();
        final boolean deep = (launchPkg != null ? modeFor(launchPkg) : GameMode.of(prefs.mode()))
                .deep(prefs.memTweak());
        final Set<String> keep = prefs.games();
        if (launchPkg != null) keep.add(launchPkg);
        final long before = Booster.mem(app).availMem;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                }
                final Booster.Result r = Booster.optimize(app, keep, deep, new Booster.Progress() {
                    @Override
                    public void onStage(String text) {
                        stage(text);
                    }
                });
                try {
                    Thread.sleep(900);
                } catch (InterruptedException ignored) {
                }
                final long after = Booster.mem(app).availMem;
                final long freed = Math.max(0, after - before);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        boosting = false;
                        if (isFinishing() || isDestroyed()) return;
                        gauge.setScanning(false);
                        gauge.setCaption("RAM TERPAKAI");
                        btnBoost.setText("🚀  BOOST SEKARANG");
                        btnBoost.performHapticFeedback(Build.VERSION.SDK_INT >= 30
                                ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.LONG_PRESS);
                        String msg = "✅ " + r.apps + " aplikasi ditutup • +" + Booster.fmt(freed) + " RAM bebas";
                        if (r.cacheBytes > 0) msg += " • cache " + Booster.fmt(r.cacheBytes);
                        tvStatus.setText(msg);
                        refreshStats();
                        if (launchPkg != null) launchGame(launchPkg);
                    }
                });
            }
        }).start();
    }

    private void launchGame(String pkg) {
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) {
            Toast.makeText(this, "Game tidak ditemukan", Toast.LENGTH_SHORT).show();
            return;
        }
        GameMode gm = modeFor(pkg);

        try {
            PowerManager pwr = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pwr != null && pwr.isPowerSaveMode() && gm.id != GameMode.SAVER) {
                Toast.makeText(this, "Penghemat Baterai Android aktif — performa game bisa dibatasi",
                        Toast.LENGTH_LONG).show();
            }
        } catch (Exception ignored) {
        }

        if (gm.dnd(prefs.dnd())) {
            if (nm.isNotificationPolicyAccessGranted()) {
                if (!prefs.dndApplied()) {
                    int prev = nm.getCurrentInterruptionFilter();
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
                    prefs.setDndApplied(true, prev);
                }
            } else if (gm.forceDnd) {
                Toast.makeText(this, gm.name + ": izinkan akses Jangan Ganggu agar hasil maksimal",
                        Toast.LENGTH_LONG).show();
            }
        }

        applyBrightness(gm);

        if (gm.overlay(prefs.overlay()) && canOverlay()) {
            startOverlay(true);
        }

        // Mulai sesi game: service menangani cooling paksa & pembersihan berkala sesuai mode
        prefs.setSessionMode(gm.id);
        prefs.setSession(true);
        prefs.setAutoSession(false, pkg);
        String lbl = pkg;
        try {
            PackageManager pmg = getPackageManager();
            lbl = pmg.getApplicationLabel(pmg.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception ignored) {
        }
        SessionStore.begin(getApplicationContext(), prefs.sessionStart(), pkg, lbl, gm.id);
        startCooling(); // service merekam sesi + menjalankan cooling/pembersihan sesuai mode
        // Engine tweak tepat sebelum game diluncurkan
        Booster.engineBoost(getApplicationContext());
        Booster.clearOwnCache(getApplicationContext());
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    // ------------------------------------------------------------ daftar game

    private GameMode modeFor(String pkg) {
        int m = prefs.gameMode(pkg);
        return GameMode.of(m >= 0 ? m : prefs.mode());
    }

    private void bindChip(TextView chip, String pkg) {
        int m = prefs.gameMode(pkg);
        if (m < 0) {
            chip.setText("🌐 Mode global");
        } else {
            GameMode gm = GameMode.of(m);
            chip.setText(gm.emoji + " " + gm.name);
        }
    }

    private void askWriteSettingsIfNeeded(GameMode gm) {
        if (gm.brightnessCap >= 0 && !canWriteSettings()) {
            Toast.makeText(this, "Mode Hemat Baterai: izinkan \"Ubah pengaturan sistem\" agar kecerahan bisa dibatasi",
                    Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {
            }
        }
    }

    private void renderGames() {
        gamesContainer.removeAllViews();
        statViews.clear();
        PackageManager pm = getPackageManager();
        int count = 0;
        for (final String p : prefs.games()) {
            try {
                ApplicationInfo ai = pm.getApplicationInfo(p, 0);
                final String label = pm.getApplicationLabel(ai).toString();
                final Drawable icon = pm.getApplicationIcon(ai);
                View v = getLayoutInflater().inflate(R.layout.item_game, gamesContainer, false);
                ((ImageView) v.findViewById(R.id.gameIcon)).setImageDrawable(icon);
                ((TextView) v.findViewById(R.id.gameName)).setText(label);
                statViews.put(p, (TextView) v.findViewById(R.id.gameStats));

                final TextView chip = v.findViewById(R.id.gameMode);
                bindChip(chip, p);
                chip.setOnClickListener(x -> {
                    int cur = prefs.gameMode(p);
                    int next = cur >= 2 ? -1 : cur + 1;
                    prefs.setGameMode(p, next);
                    bindChip(chip, p);
                    if (next >= 0) askWriteSettingsIfNeeded(GameMode.of(next));
                });

                View play = v.findViewById(R.id.gamePlay);
                play.setOnClickListener(x -> doBoost(p));
                Anim.press(play, chip);

                v.setOnLongClickListener(x -> {
                    showGameMenu(p, label, icon);
                    return true;
                });
                v.setAlpha(0f);
                v.setTranslationX(dp(30));
                v.animate().alpha(1f).translationX(0f).setDuration(400)
                        .setStartDelay(70L * count).setInterpolator(new DecelerateInterpolator()).start();
                gamesContainer.addView(v);
                count++;
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        tvEmpty.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        loadGameStats();
    }

    private String fmtPlay(long sec) {
        if (sec >= 3600) return String.format(Locale.US, "%dj %dm", sec / 3600, (sec % 3600) / 60);
        if (sec >= 60) return String.format(Locale.US, "%dm", sec / 60);
        return "<1m";
    }

    /** Total waktu main & jumlah sesi per game (dari riwayat), dihitung di thread latar. */
    private void loadGameStats() {
        if (statViews.isEmpty()) return;
        final Map<String, TextView> views = new HashMap<>(statViews);
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<SessionStore.Session> list = SessionStore.list(getApplicationContext());
                final Map<String, long[]> agg = new HashMap<>();
                for (SessionStore.Session s : list) {
                    long[] a = agg.get(s.pkg);
                    if (a == null) {
                        a = new long[2];
                        agg.put(s.pkg, a);
                    }
                    a[0] += s.durationSec();
                    a[1]++;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || isDestroyed()) return;
                        for (Map.Entry<String, TextView> e : views.entrySet()) {
                            long[] a = agg.get(e.getKey());
                            e.getValue().setText(a == null ? "Belum pernah dimainkan"
                                    : "⏱ " + fmtPlay(a[0]) + " • " + a[1] + " sesi");
                        }
                    }
                });
            }
        }).start();
    }

    private void showGameMenu(final String pkg, final String label, final Drawable icon) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(label)
                .setItems(new String[]{"📌  Tambah shortcut ke layar utama", "🗑  Hapus dari daftar"},
                        (d, which) -> {
                            if (which == 0) {
                                pinShortcut(pkg, label, icon);
                            } else {
                                Set<String> s = prefs.games();
                                s.remove(pkg);
                                prefs.setGames(s);
                                prefs.setGameMode(pkg, -1);
                                renderGames();
                            }
                        })
                .setNegativeButton("Batal", null)
                .show();
    }

    // ------------------------------------------------------------ shortcut "Boost & Main"

    private Bitmap toBitmap(Drawable src) {
        Drawable d = src;
        if (src.getConstantState() != null) d = src.getConstantState().newDrawable().mutate();
        int size = dp(96);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        d.setBounds(0, 0, size, size);
        d.draw(c);
        return bmp;
    }

    private void pinShortcut(String pkg, String label, Drawable icon) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            Toast.makeText(this, "Launcher HP kamu belum mendukung shortcut di layar utama", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.setAction(ACTION_PLAY);
            i.putExtra(EXTRA_PKG, pkg);
            ShortcutInfoCompat info = new ShortcutInfoCompat.Builder(this, "play_" + pkg)
                    .setShortLabel(label)
                    .setLongLabel("Boost & Main " + label)
                    .setIcon(IconCompat.createWithBitmap(toBitmap(icon)))
                    .setIntent(i)
                    .build();
            ShortcutManagerCompat.requestPinShortcut(this, info, null);
        } catch (Exception e) {
            Toast.makeText(this, "Gagal membuat shortcut: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent in) {
        if (in == null || !ACTION_PLAY.equals(in.getAction())) return;
        final String pkg = in.getStringExtra(EXTRA_PKG);
        in.setAction(null);
        if (pkg == null) return;
        if (getPackageManager().getLaunchIntentForPackage(pkg) == null) {
            Toast.makeText(this, "Game tidak ditemukan", Toast.LENGTH_SHORT).show();
            return;
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                doBoost(pkg);
            }
        }, 700);
    }

    @SuppressWarnings("deprecation")
    private boolean looksLikeGame(ApplicationInfo ai) {
        if ((ai.flags & ApplicationInfo.FLAG_IS_GAME) != 0) return true;
        return Build.VERSION.SDK_INT >= 26 && ai.category == ApplicationInfo.CATEGORY_GAME;
    }

    private void showPicker() {
        openPicker("🎮  Pilih Game", prefs.games(), new GamePickerDialog.OnSave() {
            @Override
            public void onSave(Set<String> selected) {
                prefs.setGames(selected);
                renderGames();
            }
        });
    }

    private void openPicker(final String title, final Set<String> initial, final GamePickerDialog.OnSave cb) {
        tvStatus.setText("📂 Memuat daftar aplikasi…");
        final Set<String> current = new HashSet<>(initial);
        new Thread(new Runnable() {
            @Override
            public void run() {
                PackageManager pm = getPackageManager();
                Intent mi = new Intent(Intent.ACTION_MAIN);
                mi.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> list = pm.queryIntentActivities(mi, 0);

                final List<AppEntry> apps = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                for (ResolveInfo ri : list) {
                    String p = ri.activityInfo.packageName;
                    if (p.equals(getPackageName()) || !seen.add(p)) continue;
                    boolean game = false;
                    try {
                        game = looksLikeGame(pm.getApplicationInfo(p, 0));
                    } catch (PackageManager.NameNotFoundException ignored) {
                    }
                    apps.add(new AppEntry(ri.loadLabel(pm).toString(), p, ri.loadIcon(pm), game));
                }
                // Terpilih dulu, lalu urut abjad
                Collections.sort(apps, new Comparator<AppEntry>() {
                    @Override
                    public int compare(AppEntry a, AppEntry b) {
                        boolean sa = current.contains(a.pkg);
                        boolean sb = current.contains(b.pkg);
                        if (sa != sb) return sa ? -1 : 1;
                        return a.label.compareToIgnoreCase(b.label);
                    }
                });

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || isDestroyed()) return;
                        if (!boosting) tvStatus.setText("Siap dioptimalkan");
                        new GamePickerDialog(MainActivity.this, apps, current, cb).setTitle(title).show();
                    }
                });
            }
        }).start();
    }
}
