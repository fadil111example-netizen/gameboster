package com.gamebooster.app;

import android.app.ActivityManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

/** Tile "Game Boost" di panel Quick Settings: satu ketukan membersihkan RAM dari mana saja. */
public class BoosterTileService extends TileService {

    private static volatile boolean busy = false;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    public void onStartListening() {
        super.onStartListening();
        refresh();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (busy) return;
        busy = true;
        refresh();
        final android.content.Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                String msg;
                try {
                    long before = Booster.mem(app).availMem;
                    Booster.Result r = Booster.optimize(app, new Prefs(app).games(), true, null);
                    try {
                        Thread.sleep(600);
                    } catch (InterruptedException ignored) {
                    }
                    long freed = Math.max(0, Booster.mem(app).availMem - before);
                    msg = "🚀 " + r.apps + " aplikasi dibersihkan • +" + Booster.fmt(freed) + " RAM bebas";
                } catch (Throwable t) {
                    msg = "Boost gagal, coba lagi";
                }
                busy = false;
                final String toast = msg;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(app, toast, Toast.LENGTH_LONG).show();
                        refresh();
                    }
                });
            }
        }).start();
    }

    private void refresh() {
        try {
            Tile t = getQsTile();
            if (t == null) return;
            ActivityManager.MemoryInfo mi = Booster.mem(this);
            int pct = Math.round((mi.totalMem - mi.availMem) * 100f / mi.totalMem);
            t.setLabel(busy ? "Membersihkan…" : "Game Boost");
            if (Build.VERSION.SDK_INT >= 29) t.setSubtitle("RAM " + pct + "%");
            t.setState(busy ? Tile.STATE_UNAVAILABLE : Tile.STATE_INACTIVE);
            t.updateTile();
        } catch (Exception ignored) {
        }
    }
}
