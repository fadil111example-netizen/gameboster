package com.gamebooster.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

/** Memulihkan Auto Cooling / Deteksi Game setelah HP restart atau aplikasi diperbarui. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String a = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) return;
        try {
            Prefs p = new Prefs(context);
            p.setSession(false);
            p.setAutoSession(false, "");
            if (p.cooling() || p.autoDetect()) {
                ContextCompat.startForegroundService(context, new Intent(context, CoolingService.class));
            }
        } catch (Exception ignored) {
        }
    }
}
