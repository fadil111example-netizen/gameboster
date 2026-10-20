package com.gamebooster.app;

import android.app.ActivityManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.widget.RemoteViews;
import android.widget.Toast;

import java.util.Locale;

/** Widget layar utama: RAM realtime (saat aplikasi/service aktif) + tombol Boost 1 ketukan. */
public class BoosterWidget extends AppWidgetProvider {

    public static final String ACTION_BOOST = "com.gamebooster.app.WIDGET_BOOST";
    private static volatile boolean busy = false;

    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] ids) {
        RemoteViews rv = build(context);
        for (int id : ids) mgr.updateAppWidget(id, rv);
    }

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (intent != null && ACTION_BOOST.equals(intent.getAction())) {
            if (busy) return;
            busy = true;
            final PendingResult pr = goAsync();
            final Context app = context.getApplicationContext();
            updateAll(app);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String msg;
                    try {
                        long before = Booster.mem(app).availMem;
                        Booster.Result r = Booster.optimize(app, new Prefs(app).games(), true, null);
                        try {
                            Thread.sleep(700);
                        } catch (InterruptedException ignored) {
                        }
                        long freed = Math.max(0, Booster.mem(app).availMem - before);
                        msg = "🚀 " + r.apps + " aplikasi dibersihkan • +" + Booster.fmt(freed) + " RAM bebas";
                    } catch (Throwable t) {
                        msg = "Boost gagal, coba lagi";
                    }
                    busy = false;
                    updateAll(app);
                    final String toastMsg = msg;
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(app, toastMsg, Toast.LENGTH_LONG).show();
                        }
                    });
                    pr.finish();
                }
            }).start();
            return;
        }
        super.onReceive(context, intent);
    }

    /** Memperbarui semua widget yang terpasang (aman dipanggil kapan saja). */
    public static void updateAll(Context ctx) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(ctx);
            int[] ids = m.getAppWidgetIds(new ComponentName(ctx, BoosterWidget.class));
            if (ids == null || ids.length == 0) return;
            m.updateAppWidget(ids, build(ctx));
        } catch (Exception ignored) {
        }
    }

    private static RemoteViews build(Context ctx) {
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_booster);
        ActivityManager.MemoryInfo mi = Booster.mem(ctx);
        long used = mi.totalMem - mi.availMem;
        int pct = Math.round(used * 100f / mi.totalMem);

        rv.setImageViewBitmap(R.id.wGauge, gauge(pct, 160));
        rv.setTextViewText(R.id.wTitle, busy ? "Membersihkan…" : "RAM terpakai");
        rv.setTextViewText(R.id.wRam, Booster.fmt(used) + " / " + Booster.fmt(mi.totalMem));
        float t = Booster.batteryTemp(ctx);
        rv.setTextViewText(R.id.wTemp, "🌡 " + (t > 0 ? String.format(Locale.US, "%.1f°C", t) : "--")
                + "  •  bebas " + Booster.fmt(mi.availMem));
        rv.setTextViewText(R.id.wBoost, busy ? "⏳" : "🚀");

        Intent boost = new Intent(ctx, BoosterWidget.class);
        boost.setAction(ACTION_BOOST);
        rv.setOnClickPendingIntent(R.id.wBoost,
                PendingIntent.getBroadcast(ctx, 0, boost, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPi = PendingIntent.getActivity(ctx, 1, open, PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.wInfo, openPi);
        rv.setOnClickPendingIntent(R.id.wGauge, openPi);
        return rv;
    }

    private static Bitmap gauge(int pct, int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float stroke = size * 0.12f;
        RectF r = new RectF(stroke, stroke, size - stroke, size - stroke);

        Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(stroke);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setColor(0x33FFFFFF);

        Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(stroke);
        arc.setStrokeCap(Paint.Cap.ROUND);
        SweepGradient sg = new SweepGradient(size / 2f, size / 2f,
                new int[]{0xFF22D3EE, 0xFF8B5CF6, 0xFFEC4899, 0xFFEC4899},
                new float[]{0f, 0.4f, 0.75f, 1f});
        Matrix m = new Matrix();
        m.setRotate(135, size / 2f, size / 2f);
        sg.setLocalMatrix(m);
        arc.setShader(sg);

        c.drawArc(r, 135, 270, false, track);
        float sweep = 270f * Math.max(0, Math.min(100, pct)) / 100f;
        if (sweep > 0.5f) c.drawArc(r, 135, sweep, false, arc);

        Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(Color.WHITE);
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setTypeface(Typeface.DEFAULT_BOLD);
        tp.setTextSize(size * 0.28f);
        c.drawText(pct + "%", size / 2f, size / 2f + size * 0.10f, tp);
        return b;
    }
}
