package com.gamebooster.app;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

/** Ping latensi jaringan (waktu koneksi TCP ke DNS publik). Tanpa DNS lookup, ringan. */
public final class PingMonitor {
    private PingMonitor() {}

    private static final AtomicBoolean busy = new AtomicBoolean(false);
    private static volatile int last = -1;
    private static final String[] HOSTS = {"1.1.1.1", "8.8.8.8"};
    private static final int[] PORTS = {443, 53};

    public static int last() {
        return last;
    }

    /** Mulai satu pengukuran di thread latar jika belum ada yang berjalan. */
    public static void poke() {
        if (!busy.compareAndSet(false, true)) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    last = measure();
                } finally {
                    busy.set(false);
                }
            }
        }).start();
    }

    private static int measure() {
        for (int i = 0; i < HOSTS.length; i++) {
            Socket s = new Socket();
            try {
                long t0 = System.nanoTime();
                s.connect(new InetSocketAddress(HOSTS[i], PORTS[i]), 1500);
                return (int) ((System.nanoTime() - t0) / 1000000L);
            } catch (Exception ignored) {
            } finally {
                try {
                    s.close();
                } catch (Exception ignored) {
                }
            }
        }
        return -1;
    }

    @SuppressWarnings("deprecation")
    public static String networkType(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            if (ni == null || !ni.isConnected()) return "Offline";
            if (ni.getType() == ConnectivityManager.TYPE_WIFI) return "Wi-Fi";
            if (ni.getType() == ConnectivityManager.TYPE_MOBILE) return "Seluler";
            return "Jaringan";
        } catch (Exception e) {
            return "--";
        }
    }
}
