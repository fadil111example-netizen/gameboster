package com.gamebooster.app;

import android.graphics.drawable.Drawable;

public class AppEntry {
    public final String label;
    public final String pkg;
    public final Drawable icon;
    public final boolean isGame;

    public AppEntry(String label, String pkg, Drawable icon, boolean isGame) {
        this.label = label;
        this.pkg = pkg;
        this.icon = icon;
        this.isGame = isGame;
    }
}
