package com.gamebooster.app;

import android.app.ActivityManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {

    private static final int LIVE_MAX = 90;
    private static final int CHART_MAX_POINTS = 240;

    private LineChartView chartTemp, chartRam, chartPing;
    private TextView tvSource, tvSummary, tvNo;
    private LinearLayout container;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<Float> liveT = new ArrayList<>();
    private final ArrayList<Float> liveR = new ArrayList<>();
    private final ArrayList<Float> liveP = new ArrayList<>();
    private boolean liveMode = true;
    private List<SessionStore.Session> sessions = new ArrayList<>();

    private final Runnable liveTick = new Runnable() {
        @Override
        public void run() {
            sampleLive();
            if (liveMode) showLive(false);
            handler.postDelayed(this, 2000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        chartTemp = findViewById(R.id.chartTemp);
        chartRam = findViewById(R.id.chartRam);
        chartPing = findViewById(R.id.chartPing);
        tvSource = findViewById(R.id.tvSource);
        tvSummary = findViewById(R.id.tvSummary);
        tvNo = findViewById(R.id.tvNoSessions);
        container = findViewById(R.id.sessionsContainer);

        View back = findViewById(R.id.btnBack);
        View clear = findViewById(R.id.btnClearHistory);
        View share = findViewById(R.id.btnShare);
        View live = findViewById(R.id.btnLive);
        back.setOnClickListener(v -> finish());
        clear.setOnClickListener(v -> confirmClear());
        share.setOnClickListener(v -> shareCurrent());
        live.setOnClickListener(v -> {
            liveMode = true;
            showLive(true);
        });
        Anim.press(back, clear, live, share);

        playIntro();
        PingMonitor.poke();
        sampleLive();
        showLive(true);
        loadSessions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(liveTick);
        handler.postDelayed(liveTick, 2000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(liveTick);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void playIntro() {
        LinearLayout content = findViewById(R.id.histContent);
        for (int i = 0; i < content.getChildCount(); i++) {
            View v = content.getChildAt(i);
            v.setAlpha(0f);
            v.setTranslationY(dp(30));
            v.animate().alpha(1f).translationY(0f).setStartDelay(60L * i).setDuration(450)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    // ------------------------------------------------------------ live

    private void sampleLive() {
        float t = Booster.batteryTemp(this);
        if (t > 0) add(liveT, t);
        ActivityManager.MemoryInfo mi = Booster.mem(this);
        add(liveR, (mi.totalMem - mi.availMem) * 100f / mi.totalMem);
        int ping = PingMonitor.last();
        if (ping >= 0) add(liveP, (float) ping);
        PingMonitor.poke();
    }

    private void add(ArrayList<Float> list, float v) {
        list.add(v);
        while (list.size() > LIVE_MAX) list.remove(0);
    }

    private float[] toArray(List<Float> l) {
        float[] a = new float[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = l.get(i);
        return a;
    }

    private void showLive(boolean animate) {
        float[] t = toArray(liveT);
        float[] r = toArray(liveR);
        float[] p = toArray(liveP);
        tvSource.setText("🔴 Live — diperbarui tiap 2 detik");
        String left = "-" + (Math.max(t.length, r.length) * 2) + " dtk";
        chartTemp.setData(t, "🌡 Suhu baterai", "°C", 0xFFFB923C, Float.NaN, Float.NaN, left, "sekarang", animate);
        chartRam.setData(r, "💾 RAM terpakai", "%", 0xFF22D3EE, 0f, 100f, left, "sekarang", animate);
        chartPing.setData(p, "📶 Ping (" + PingMonitor.networkType(this) + ")", " ms", 0xFF4ADE80,
                Float.NaN, Float.NaN, left, "sekarang", animate);
        if (t.length > 0 && r.length > 0) {
            String ping = p.length > 0 ? String.format(Locale.US, " • ping %.0f ms", p[p.length - 1]) : "";
            tvSummary.setText(String.format(Locale.US, "Terakhir: %.1f°C • RAM %.0f%%%s • %d sampel",
                    t[t.length - 1], r[r.length - 1], ping, r.length));
        } else {
            tvSummary.setText("Mengumpulkan data…");
        }
    }

    // ------------------------------------------------------------ sesi

    private void loadSessions() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<SessionStore.Session> list = SessionStore.list(getApplicationContext());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || isDestroyed()) return;
                        sessions = list;
                        renderSessions();
                    }
                });
            }
        }).start();
    }

    private void renderSessions() {
        container.removeAllViews();
        SimpleDateFormat df = new SimpleDateFormat("dd MMM HH:mm", Locale.getDefault());
        int idx = 0;
        for (final SessionStore.Session s : sessions) {
            View v = getLayoutInflater().inflate(R.layout.item_session, container, false);
            GameMode gm = GameMode.of(s.mode);
            ((TextView) v.findViewById(R.id.sesTitle)).setText("🎮 " + s.label);
            ((TextView) v.findViewById(R.id.sesMeta)).setText(df.format(new Date(s.start)) + " • "
                    + gm.emoji + " " + gm.name);
            float maxT = 0f;
            for (SessionStore.Sample sm : s.samples) if (sm.temp > maxT) maxT = sm.temp;
            ((TextView) v.findViewById(R.id.sesStats)).setText(fmtDur(s.durationSec()) + "\n"
                    + (maxT > 0 ? String.format(Locale.US, "maks %.1f°C", maxT) : "--"));
            v.setOnClickListener(x -> showSession(s));
            Anim.press(v);
            v.setAlpha(0f);
            v.animate().alpha(1f).setStartDelay(50L * idx).setDuration(350).start();
            container.addView(v);
            idx++;
        }
        tvNo.setVisibility(sessions.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private String fmtDur(long sec) {
        if (sec >= 3600) return String.format(Locale.US, "%d j %d mnt", sec / 3600, (sec % 3600) / 60);
        return String.format(Locale.US, "%d:%02d", sec / 60, sec % 60);
    }

    private float[] downsample(List<Float> src) {
        int n = src.size();
        if (n <= CHART_MAX_POINTS) return toArray(src);
        float[] out = new float[CHART_MAX_POINTS];
        for (int i = 0; i < CHART_MAX_POINTS; i++) {
            int idx = Math.round(i * (n - 1f) / (CHART_MAX_POINTS - 1f));
            out[i] = src.get(idx);
        }
        return out;
    }

    private void showSession(SessionStore.Session s) {
        liveMode = false;
        ArrayList<Float> temps = new ArrayList<>();
        ArrayList<Float> rams = new ArrayList<>();
        ArrayList<Float> pings = new ArrayList<>();
        float maxT = 0f, sumT = 0f, maxR = 0f, sumR = 0f, sumP = 0f, maxP = 0f;
        float battFirst = -1f, battLast = -1f;
        for (SessionStore.Sample sm : s.samples) {
            if (sm.temp > 0) {
                temps.add(sm.temp);
                sumT += sm.temp;
                if (sm.temp > maxT) maxT = sm.temp;
            }
            rams.add(sm.ram);
            sumR += sm.ram;
            if (sm.ram > maxR) maxR = sm.ram;
            if (sm.ping >= 0) {
                pings.add((float) sm.ping);
                sumP += sm.ping;
                if (sm.ping > maxP) maxP = sm.ping;
            }
            if (sm.batt >= 0) {
                if (battFirst < 0) battFirst = sm.batt;
                battLast = sm.batt;
            }
        }
        SimpleDateFormat df = new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault());
        tvSource.setText("🎮 " + s.label + " • " + df.format(new Date(s.start)));

        String right = fmtDur(s.durationSec());
        chartTemp.setData(downsample(temps), "🌡 Suhu baterai", "°C", 0xFFFB923C, Float.NaN, Float.NaN, "0:00", right, true);
        chartRam.setData(downsample(rams), "💾 RAM terpakai", "%", 0xFF22D3EE, 0f, 100f, "0:00", right, true);
        chartPing.setData(downsample(pings), "📶 Ping", " ms", 0xFF4ADE80, Float.NaN, Float.NaN, "0:00", right, true);

        if (rams.isEmpty()) {
            tvSummary.setText("Sesi terlalu singkat, belum ada sampel. Durasi " + right);
            return;
        }
        StringBuilder sb = new StringBuilder();
        if (temps.isEmpty()) sb.append("Suhu: tidak tersedia");
        else sb.append(String.format(Locale.US, "Suhu maks %.1f°C • rata-rata %.1f°C", maxT, sumT / temps.size()));
        sb.append(String.format(Locale.US, "\nRAM puncak %.0f%% • rata-rata %.0f%%", maxR, sumR / rams.size()));
        if (!pings.isEmpty()) {
            sb.append(String.format(Locale.US, "\nPing rata-rata %.0f ms • tertinggi %.0f ms", sumP / pings.size(), maxP));
        }
        if (battFirst >= 0 && battLast >= 0) {
            float drop = battFirst - battLast;
            long dur = s.durationSec();
            if (drop > 0 && dur >= 300) {
                sb.append(String.format(Locale.US, "\nBaterai %.0f%% → %.0f%% (−%.0f%%, ≈%.0f%%/jam)",
                        battFirst, battLast, drop, drop * 3600f / dur));
            } else {
                sb.append(String.format(Locale.US, "\nBaterai %.0f%% → %.0f%%", battFirst, battLast));
            }
        }
        sb.append(String.format(Locale.US, "\nDurasi %s • %d sampel", right, rams.size()));
        tvSummary.setText(sb.toString());
    }

    private void shareCurrent() {
        String text = "🎮 Game Booster\n" + tvSource.getText() + "\n" + tvSummary.getText();
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, text);
        try {
            startActivity(Intent.createChooser(i, "Bagikan statistik"));
        } catch (Exception ignored) {
        }
    }

    private void confirmClear() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Hapus semua riwayat?")
                .setMessage("Riwayat sesi permainan akan dihapus permanen.")
                .setPositiveButton("Hapus", (d, w) -> {
                    SessionStore.clear(getApplicationContext());
                    sessions = new ArrayList<>();
                    renderSessions();
                    liveMode = true;
                    showLive(true);
                })
                .setNegativeButton("Batal", null)
                .show();
    }
}
