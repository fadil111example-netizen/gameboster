package com.gameboost.app.core

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("gameboost", Context.MODE_PRIVATE)

    var boostOnLaunch: Boolean
        get() = sp.getBoolean("boost_on_launch", true)
        set(v) = sp.edit().putBoolean("boost_on_launch", v).apply()

    var dnd: Boolean
        get() = sp.getBoolean("dnd", true)
        set(v) = sp.edit().putBoolean("dnd", v).apply()

    var overlay: Boolean
        get() = sp.getBoolean("overlay", true)
        set(v) = sp.edit().putBoolean("overlay", v).apply()

    var manualGames: Set<String>
        get() = sp.getStringSet("manual_games", emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet("manual_games", v.toSet()).apply()

    var hiddenGames: Set<String>
        get() = sp.getStringSet("hidden_games", emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet("hidden_games", v.toSet()).apply()

    var sessionActive: Boolean
        get() = sp.getBoolean("session_active", false)
        set(v) = sp.edit().putBoolean("session_active", v).apply()

    /** Filter Jangan Ganggu sebelum sesi game dimulai; -1 = tidak ada yang perlu dipulihkan. */
    var prevFilter: Int
        get() = sp.getInt("prev_filter", -1)
        set(v) = sp.edit().putInt("prev_filter", v).apply()
}
