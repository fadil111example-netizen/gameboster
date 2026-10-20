package com.gamebooster.app;

import android.Manifest;
import android.app.NotificationManager;
import android.app.StatusBarManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Display;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Locale;

/** Pusat izin & diagnostik: status tiap izin/fitur, tombol perbaiki, dan info perangkat (Android 12/13). */
public class DiagnosticsActivity extends AppCompatActivity {

    private static final int REQ_NOTIF = 200;
    private static final int OK = 0;
    private static final int WARN = 1;
    private static final int INFO = 2;

    private LinearLayout rows;
    private TextView tvDevice;
    private Prefs prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diag);
        prefs = new Prefs(this);
        rows = findViewById(R.id.diagRows);
        tvDevice = findViewById(R.id.tvDevice);
        View back = findViewById(R.id.btnDiagBack);
        back.setOnClickListener(v -> finish());
        Anim.press(back);

        LinearLayout content = findViewById(R.id.diagContent);
        for (int i = 0; i < content.getChildCount(); i++) {
            View v = content.getChildAt(i);
            v.setAlpha(0f);
            v.setTranslationY(40f);
            v.animate().alpha(1f).translationY(0f).setStartDelay(60L * i).setDuration(450)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private float refreshRate() {
        try {
            DisplayManager dm = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
            Display d = dm.getDisplay(Display.DEFAULT_DISPLAY);
            if (d != null) return d.getRefreshRate();
        } catch (Exception ignored) {
        }
        return 60f;
    }

    private void render() {
        String ver = "?";
        try {
            ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        android.app.ActivityManager.MemoryInfo mi = Booster.mem(this);
        tvDevice.setText("📱 " + Build.MANUFACTURER + " " + Build.MODEL
                + "\nAndroid " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"
                + "\nRAM " + Booster.fmt(mi.totalMem) + " • " + Runtime.getRuntime().availableProcessors() + " inti CPU"
                + " • layar " + String.format(Locale.US, "%.0f", refreshRate()) + " Hz"
                + "\n" + ThermalInfo.describe(this)
                + "\nGame Booster v" + ver);

        rows.removeAllViews();
        final NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // 1. Notifikasi
        boolean notifOk = Build.VERSION.SDK_INT < 33
                || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        addRow(notifOk ? OK : WARN, "Notifikasi",
                notifOk ? "Aktif — notifikasi layanan & peringatan suhu tampil"
                        : "Android 13+ butuh izin agar notifikasi layanan tampil",
                notifOk ? null : "Izinkan", new Runnable() {
                    @Override
                    public void run() {
                        if (Build.VERSION.SDK_INT >= 33) {
                            ActivityCompat.requestPermissions(DiagnosticsActivity.this,
                                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
                        } else {
                            openNotifSettings();
                        }
                    }
                });

        // 2. Overlay
        boolean overlayOk = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
        addRow(overlayOk ? OK : WARN, "Tampil di atas aplikasi lain",
                overlayOk ? "Diizinkan — Overlay Monitor bisa tampil di game"
                        : "Dibutuhkan untuk Overlay Monitor (FPS, suhu, ping)",
                overlayOk ? null : "Buka", new Runnable() {
                    @Override
                    public void run() {
                        open(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName())));
                    }
                });

        // 3. Akses penggunaan
        boolean usageOk = GameDetector.hasAccess(this);
        addRow(usageOk ? OK : WARN, "Akses Penggunaan",
                usageOk ? "Diizinkan — Deteksi Game Otomatis bisa bekerja"
                        : "Dibutuhkan untuk Deteksi Game Otomatis",
                usageOk ? null : "Buka", new Runnable() {
                    @Override
                    public void run() {
                        open(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                    }
                });

        // 4. Jangan Ganggu
        boolean dndOk = nm.isNotificationPolicyAccessGranted();
        addRow(dndOk ? OK : WARN, "Akses Jangan Ganggu",
                dndOk ? "Diizinkan — mode Jangan Ganggu otomatis saat main"
                        : "Dibutuhkan agar Jangan Ganggu aktif otomatis di game",
                dndOk ? null : "Buka", new Runnable() {
                    @Override
                    public void run() {
                        open(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
                    }
                });

        // 5. Ubah pengaturan sistem
        boolean writeOk = Build.VERSION.SDK_INT < 23 || Settings.System.canWrite(this);
        addRow(writeOk ? OK : WARN, "Ubah pengaturan sistem",
                writeOk ? "Diizinkan — mode Hemat Baterai bisa membatasi kecerahan"
                        : "Dibutuhkan hanya untuk pembatasan kecerahan (mode Hemat)",
                writeOk ? null : "Buka", new Runnable() {
                    @Override
                    public void run() {
                        open(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                Uri.parse("package:" + getPackageName())));
                    }
                });

        // 6. Optimasi baterai
        boolean battOk = false;
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            battOk = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        } catch (Exception ignored) {
        }
        addRow(battOk ? OK : WARN, "Bebas optimasi baterai",
                battOk ? "Aktif — sistem tidak mematikan layanan latar"
                        : "Disarankan agar Deteksi Game & Auto Cooling tidak dimatikan sistem",
                battOk ? null : "Atur", new Runnable() {
                    @Override
                    public void run() {
                        try {
                            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:" + getPackageName())));
                        } catch (Exception e) {
                            open(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                        }
                    }
                });

        // 7. Layanan latar
        boolean needService = prefs.cooling() || prefs.autoDetect();
        boolean svcRunning = CoolingService.isRunning();
        if (!needService) {
            addRow(INFO, "Layanan latar", "Tidak dibutuhkan — Auto Cooling & Deteksi Game sedang mati", null, null);
        } else {
            addRow(svcRunning ? OK : WARN, "Layanan latar",
                    svcRunning ? "Berjalan — memantau suhu & game"
                            : "Belum berjalan. Ketuk Mulai untuk menghidupkan",
                    svcRunning ? null : "Mulai", new Runnable() {
                        @Override
                        public void run() {
                            try {
                                ContextCompat.startForegroundService(DiagnosticsActivity.this,
                                        new Intent(DiagnosticsActivity.this, CoolingService.class));
                                Toast.makeText(DiagnosticsActivity.this, "Layanan dimulai", Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {
                                Toast.makeText(DiagnosticsActivity.this, "Gagal: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            }
                            render();
                        }
                    });
        }

        // 8. Thermal API
        int ts = ThermalInfo.status(this);
        addRow(INFO, "Thermal API sistem",
                ts >= 0 ? "Didukung — status sekarang: " + ThermalInfo.statusName(ts)
                        + ". Auto Cooling memakainya untuk mendinginkan sebelum throttling."
                        : "Butuh Android 10+. Memakai suhu baterai saja.", null, null);

        // 9. Sensor suhu CPU
        float ct = ThermalReader.cpuTemp();
        addRow(INFO, "Sensor suhu CPU",
                ct > 0 ? String.format(Locale.US, "Terbaca: %.0f°C", ct)
                        : "Dibatasi sistem di perangkat ini — memakai suhu baterai & Thermal API", null, null);

        // 10. Tile Quick Settings
        addRow(INFO, "Tile Quick Settings",
                "Boost satu ketukan dari panel notifikasi",
                "Tambahkan", new Runnable() {
                    @Override
                    public void run() {
                        addTile();
                    }
                });
    }

    private void addTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                StatusBarManager sbm = (StatusBarManager) getSystemService(Context.STATUS_BAR_SERVICE);
                sbm.requestAddTileService(new ComponentName(this, BoosterTileService.class), "Game Boost",
                        Icon.createWithResource(this, R.drawable.ic_notif), ContextCompat.getMainExecutor(this),
                        result -> Toast.makeText(DiagnosticsActivity.this,
                                result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED
                                        || result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
                                        ? "Tile siap di Quick Settings" : "Tile tidak ditambahkan",
                                Toast.LENGTH_SHORT).show());
                return;
            } catch (Exception ignored) {
            }
        }
        Toast.makeText(this, "Tarik panel Quick Settings → ikon pensil → tambahkan \"Game Boost\"",
                Toast.LENGTH_LONG).show();
    }

    private void open(Intent i) {
        try {
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Tidak bisa membuka pengaturan ini", Toast.LENGTH_SHORT).show();
        }
    }

    private void openNotifSettings() {
        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        open(i);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIF) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (!granted) openNotifSettings();
            render();
        }
    }

    private void addRow(int state, String title, String desc, String actionLabel, final Runnable action) {
        View v = getLayoutInflater().inflate(R.layout.item_diag, rows, false);
        ((TextView) v.findViewById(R.id.dgStatus)).setText(state == OK ? "✅" : (state == WARN ? "⚠️" : "ℹ️"));
        ((TextView) v.findViewById(R.id.dgTitle)).setText(title);
        ((TextView) v.findViewById(R.id.dgDesc)).setText(desc);
        TextView btn = v.findViewById(R.id.dgAction);
        if (actionLabel == null || action == null) {
            btn.setVisibility(View.GONE);
        } else {
            btn.setText(actionLabel);
            btn.setOnClickListener(x -> action.run());
            Anim.press(btn);
        }
        rows.addView(v);
    }
}
