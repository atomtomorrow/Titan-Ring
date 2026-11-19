package com.titan.ringlight

import android.content.Context

object Prefs {
    const val OFF = 0 // «не светить»

    const val K_ENABLED = "enabled"
    const val K_DEFAULT = "default_color"
    const val K_THICK = "thick_dp"
    const val K_GAP = "gap_dp"
    const val K_ALPHA = "alpha_pct"
    const val K_PULSE = "pulse"
    const val K_SEGMENTS = "segments"
    const val K_DX = "dx"
    const val K_DY = "dy"
    const val K_DR = "dr"
    const val K_SCREEN_OFF = "screen_off"
    const val K_SCREEN_MIN = "screen_min"
    private const val K_SEEN = "seen"

    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("ring", Context.MODE_PRIVATE)

    fun int(c: Context, k: String, def: Int) = sp(c).getInt(k, def)
    fun setInt(c: Context, k: String, v: Int) = sp(c).edit().putInt(k, v).apply()
    fun bool(c: Context, k: String, def: Boolean) = sp(c).getBoolean(k, def)
    fun setBool(c: Context, k: String, v: Boolean) = sp(c).edit().putBoolean(k, v).apply()

    fun defaultColor(c: Context) = int(c, K_DEFAULT, 0xFFFFFFFF.toInt())

    /** null = не настроено (берётся цвет по умолчанию), OFF = не светить */
    fun appColor(c: Context, pkg: String): Int? {
        val s = sp(c)
        return if (s.contains("c_$pkg")) s.getInt("c_$pkg", OFF) else null
    }

    fun setAppColor(c: Context, pkg: String, color: Int?) {
        val e = sp(c).edit()
        if (color == null) e.remove("c_$pkg") else e.putInt("c_$pkg", color)
        e.apply()
    }

    fun colorFor(c: Context, pkg: String) = appColor(c, pkg) ?: defaultColor(c)

    fun configuredColors(c: Context): List<Int> =
        sp(c).all.filterKeys { it.startsWith("c_") }.values
            .mapNotNull { it as? Int }.filter { it != OFF }.distinct()

    fun seen(c: Context): Set<String> = HashSet(sp(c).getStringSet(K_SEEN, emptySet()) ?: emptySet())

    fun addSeen(c: Context, pkg: String) {
        val s = seen(c)
        if (s.add(pkg)) sp(c).edit().putStringSet(K_SEEN, s).apply()
    }
}
