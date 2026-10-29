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
    /** volume pill width in dp */
    var pillWidthDp: Int
        get() = sp.getInt("pillWidthDp", 72)
        set(v) = sp.edit().putInt("pillWidthDp", v).apply()
    /** volume pill height as % of screen */
    var pillHeightPct: Int
        get() = sp.getInt("pillHeightPct", 70)
        set(v) = sp.edit().putInt("pillHeightPct", v).apply()
    /** vertical position of the pill: 0 = top, 100 = bottom */
    var pillPosPct: Int
        get() = sp.getInt("pillPosPct", 50)
        set(v) = sp.edit().putInt("pillPosPct", v).apply()
    var showStrip: Boolean
        get() = sp.getBoolean("showStrip", false)
        set(v) = sp.edit().putBoolean("showStrip", v).apply()
}
