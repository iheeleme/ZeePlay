/*
 * DiPlay — Zeekr adaptation. GPLv3 (see LICENSE).
 *
 * Persisted toggles for the Zeekr windshield HUD (CarPlay card/map). Defaults:
 * the live map on, the glass turn card off, 100% size, marker centred — matching
 * the engine's out-of-the-box behaviour once a HUD display is present.
 */
package com.shilapi.xcertplay

import android.content.Context

object ZeekrHudSettings {
    private const val PREFS = "zeekr_hud"
    private const val KEY_MAP = "hud_map_enabled"
    private const val KEY_CARD = "hud_card_enabled"
    private const val KEY_SIZE = "hud_map_size_percent"
    private const val KEY_MARKER_H = "hud_map_marker_h"
    private const val KEY_MARKER_V = "hud_map_marker_v"
    private const val KEY_LAYOUT_SIZE = "hud_layout_size_percent"
    private const val KEY_LAYOUT_X = "hud_layout_x_percent"
    private const val KEY_LAYOUT_Y = "hud_layout_y_percent"
    private const val KEY_DARK = "hud_dark_enabled"
    private const val KEY_MODE = "hud_mode"

    /** CarPlay map stream (drawn by the iPhone). */
    const val MODE_MAP = 0
    /** CarPlay turn card stream (drawn by the iPhone). */
    const val MODE_CARD = 1
    /** ZeePlay's own compact arrow tile; no CarPlay HUD stream. */
    const val MODE_COMPACT = 2

    /** Stream size on the HUD panel, percent of the largest aspect-correct fit. */
    val layoutSizePercents = (20..150 step 5).toList()
    /** Stream centre on the HUD panel, percent of the panel; 50 = centre. */
    val layoutPositionPercents = (0..100 step 1).toList()

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The HUD content; installs from before the mode setting keep their turn-card choice. */
    fun mode(c: Context): Int {
        val p = prefs(c)
        if (p.contains(KEY_MODE)) return p.getInt(KEY_MODE, MODE_MAP).coerceIn(MODE_MAP, MODE_COMPACT)
        return if (p.getBoolean(KEY_CARD, false)) MODE_CARD else MODE_MAP
    }
    fun setMode(c: Context, mode: Int) = prefs(c).edit().putInt(KEY_MODE, mode.coerceIn(MODE_MAP, MODE_COMPACT)).apply()
    fun mapEnabled(c: Context): Boolean = mode(c) == MODE_MAP && prefs(c).getBoolean(KEY_MAP, true)
    fun cardEnabled(c: Context): Boolean = mode(c) == MODE_CARD
    fun compactEnabled(c: Context): Boolean = mode(c) == MODE_COMPACT
    fun sizePercent(c: Context): Int = prefs(c).getInt(KEY_SIZE, 100)
    fun markerHorizontal(c: Context): Int = prefs(c).getInt(KEY_MARKER_H, 0)
    fun markerVertical(c: Context): Int = prefs(c).getInt(KEY_MARKER_V, 0)
    // Placement is kept per HUD mode: the streams have different shapes, so each remembers its own
    // size and position. The map and the compact tile default to the free square on the right.
    private fun suffix(mode: Int) = when (mode) { MODE_CARD -> "_card"; MODE_COMPACT -> "_compact"; else -> "_map" }
    fun layoutSizePercent(c: Context, mode: Int): Int =
        prefs(c).getInt(KEY_LAYOUT_SIZE + suffix(mode), if (mode == MODE_CARD) 100 else 40)
    fun layoutXPercent(c: Context, mode: Int): Int =
        prefs(c).getInt(KEY_LAYOUT_X + suffix(mode), if (mode == MODE_CARD) 50 else 80)
    fun layoutYPercent(c: Context, mode: Int): Int = prefs(c).getInt(KEY_LAYOUT_Y + suffix(mode), 50)

    /**
     * Pixel size advertised to the iPhone for the HUD map: the size it is shown at on the 1024x576
     * panel, so CarPlay lays the map out for that size and it is shown 1:1 instead of shrunk.
     * Rounded to 16; applied at the next connection.
     */
    fun mapStreamSize(c: Context): Pair<Int, Int> {
        val pct = layoutSizePercent(c, MODE_MAP).coerceIn(38, 150)
        fun r16(v: Int) = ((v + 8) / 16) * 16
        return r16(640 * pct / 100).coerceAtLeast(240) to r16(576 * pct / 100).coerceAtLeast(208)
    }
    fun darkEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_DARK, true)

    fun setMapEnabled(c: Context, on: Boolean) = prefs(c).edit().putBoolean(KEY_MAP, on).apply()
    fun setCardEnabled(c: Context, on: Boolean) = setMode(c, if (on) MODE_CARD else MODE_MAP)
    fun setSizePercent(c: Context, v: Int) = prefs(c).edit().putInt(KEY_SIZE, v).apply()
    fun setMarkerHorizontal(c: Context, v: Int) = prefs(c).edit().putInt(KEY_MARKER_H, v).apply()
    fun setMarkerVertical(c: Context, v: Int) = prefs(c).edit().putInt(KEY_MARKER_V, v).apply()
    fun setLayoutSizePercent(c: Context, mode: Int, v: Int) = prefs(c).edit().putInt(KEY_LAYOUT_SIZE + suffix(mode), v).apply()
    fun setLayoutXPercent(c: Context, mode: Int, v: Int) = prefs(c).edit().putInt(KEY_LAYOUT_X + suffix(mode), v).apply()
    fun setLayoutYPercent(c: Context, mode: Int, v: Int) = prefs(c).edit().putInt(KEY_LAYOUT_Y + suffix(mode), v).apply()
    fun setDarkEnabled(c: Context, on: Boolean) = prefs(c).edit().putBoolean(KEY_DARK, on).apply()
}
