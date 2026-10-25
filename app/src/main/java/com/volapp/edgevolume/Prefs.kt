package com.volapp.edgevolume

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("edge_volume", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) = sp.edit().putBoolean("enabled", v).apply()
    var left: Boolean
        get() = sp.getBoolean("left", true)
        set(v) = sp.edit().putBoolean("left", v).apply()
    var right: Boolean
        get() = sp.getBoolean("right", true)
        set(v) = sp.edit().putBoolean("right", v).apply()
    /** strip width in dp */
    var widthDp: Int
        get() = sp.getInt("widthDp", 24)
        set(v) = sp.edit().putInt("widthDp", v).apply()
    /** strip height as % of screen */
    var heightPct: Int
        get() = sp.getInt("heightPct", 90)
        set(v) = sp.edit().putInt("heightPct", v).apply()
    /** % of strip height needed to sweep the full volume range */
    var sensitivityPct: Int
        get() = sp.getInt("sensitivityPct", 70)
        set(v) = sp.edit().putInt("sensitivityPct", v).apply()
    var showStrip: Boolean
        get() = sp.getBoolean("showStrip", false)
        set(v) = sp.edit().putBoolean("showStrip", v).apply()
}
