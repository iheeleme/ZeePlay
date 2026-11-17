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
    /**
     * Where the stock Zeekr navigation (com.zeekr.automap) draws its HUD map on the 1024x576 panel: a
     * 753x188 band at (135, 193), i.e. centred (49.95% / 49.83%). Only this band is projected.
     */
    const val STOCK_REGION_LEFT = 135
    const val STOCK_REGION_TOP = 193
    const val STOCK_REGION_WIDTH = 753
    const val STOCK_REGION_HEIGHT = 188

    // Placement is kept per HUD mode: the streams have different shapes, so each remembers its own
    // size and position. Defaults put each mode wholly inside the stock band, centred: the turn card
    // (624x272) at 40% is 410x179, the map (240x224 stream) and the compact tile at 30% are 185x173
    // and 173x173.
    private fun suffix(mode: Int) = when (mode) { MODE_CARD -> "_card"; MODE_COMPACT -> "_compact"; else -> "_map" }
    private fun defaultSizePercent(mode: Int) = if (mode == MODE_CARD) 40 else 30
    private const val DEFAULT_X_PERCENT = 50
    private const val DEFAULT_Y_PERCENT = 50
    fun layoutSizePercent(c: Context, mode: Int): Int =
        prefs(c).getInt(KEY_LAYOUT_SIZE + suffix(mode), defaultSizePercent(mode))
    fun layoutXPercent(c: Context, mode: Int): Int =
        prefs(c).getInt(KEY_LAYOUT_X + suffix(mode), DEFAULT_X_PERCENT)
    fun layoutYPercent(c: Context, mode: Int): Int = prefs(c).getInt(KEY_LAYOUT_Y + suffix(mode), DEFAULT_Y_PERCENT)

    /** Back to the stock-navigation band for every mode (drops the driver's saved size/position). */
    fun resetLayoutToStock(c: Context) {
        val edit = prefs(c).edit()
        for (mode in MODE_MAP..MODE_COMPACT) {
            edit.remove(KEY_LAYOUT_SIZE + suffix(mode))
            edit.remove(KEY_LAYOUT_X + suffix(mode))
            edit.remove(KEY_LAYOUT_Y + suffix(mode))
        }
        edit.apply()
    }

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
