/*
 * ZeePlay — Zeekr adaptation. GPLv3 (see LICENSE).
 *
 * Compact turn tile for the windshield HUD: a large maneuver arrow with the distance and the road
 * under it, drawn light on black (black emits no light on the projector) so it fits the small square
 * the HUD leaves free next to the car's own widgets. Fed by the same iAP2 guidance as the cluster
 * turn card; nothing is streamed from the iPhone for this mode.
 */
package com.shilapi.xcertplay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.content.ContextCompat
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.hud.ClusterTurnGuidance

internal class HudTurnTileView(context: Context) : View(context) {
    private var guidance: ClusterTurnGuidance? = null
    private var glyph: Drawable? = null
    private var glyphTag = -1

    private val distancePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    private val roadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(205, 228, 255)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(10, 132, 255) }
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }

    fun setGuidance(next: ClusterTurnGuidance?) {
        if (next == guidance) return
        guidance = next
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val next = guidance ?: return
        val side = minOf(width, height).toFloat()
        if (side <= 0f) return
        val left = (width - side) / 2f
        val top = (height - side) / 2f
        val centre = left + side / 2f

        if (next.icon != 0) {
            val g = side * 0.58f
            drawGlyph(canvas, next, centre - g / 2f, top + side * 0.02f, g)
        }
        distancePaint.textSize = side * 0.21f
        canvas.drawText(ellipsize(distanceLabel(next.distanceMeters), side * 0.98f, distancePaint),
            centre, top + side * 0.81f, distancePaint)
        val road = roadLabel(next)
        if (road.isNotEmpty()) {
            roadPaint.textSize = side * 0.115f
            canvas.drawText(ellipsize(road, side * 0.98f, roadPaint), centre, top + side * 0.97f, roadPaint)
        }
    }

    private fun drawGlyph(canvas: Canvas, next: ClusterTurnGuidance, left: Float, top: Float, side: Float) {
        val resId = glyphRes(next.icon)
        if (resId != glyphTag) {
            glyph = ContextCompat.getDrawable(context, resId)?.mutate()?.apply { setTint(Color.WHITE) }
            glyphTag = resId
        }
        glyph?.setBounds(left.toInt(), top.toInt(), (left + side).toInt(), (top + side).toInt())
        glyph?.draw(canvas)
        val exit = next.roundaboutExit.takeIf { it in 1..9 } ?: return
        val d = side * 0.36f
        val cx = left + side - d / 2f
        val cy = top + side - d / 2f
        canvas.drawCircle(cx, cy, d / 2f, badgePaint)
        badgeTextPaint.textSize = d * 0.62f
        canvas.drawText(exit.toString(), cx, cy - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f, badgeTextPaint)
    }

    // Same AMap NEW_ICON vocabulary and labels as ClusterTurnCardView; kept local so the cluster card
    // (which already works on the car) is not touched.
    private fun glyphRes(icon: Int): Int = when (icon) {
        2 -> R.drawable.ic_turn_card_turn_left
        3 -> R.drawable.ic_turn_card_turn_right
        4 -> R.drawable.ic_turn_card_turn_slight_left
        5 -> R.drawable.ic_turn_card_turn_slight_right
        6 -> R.drawable.ic_turn_card_turn_sharp_left
        7 -> R.drawable.ic_turn_card_turn_sharp_right
        8 -> R.drawable.ic_turn_card_u_turn_left
        19 -> R.drawable.ic_turn_card_u_turn_right
        11, 12, 17, 18 -> R.drawable.ic_turn_card_roundabout_right
        15 -> R.drawable.ic_turn_card_sports_score
        else -> R.drawable.ic_turn_card_straight
    }

    private fun distanceLabel(meters: Int): String = when {
        meters <= 20 -> context.getString(R.string.turn_card_now)
        meters < 1000 -> context.getString(R.string.turn_card_distance_m, meters)
        else -> context.getString(R.string.turn_card_distance_km, meters / 100 / 10f)
    }

    private fun roadLabel(next: ClusterTurnGuidance): String =
        if (next.roundaboutExit in 1..9) context.getString(R.string.turn_card_exit, next.roundaboutExit) else next.road

    private fun ellipsize(text: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        var end = text.length
        while (end > 0 && paint.measureText(text.take(end) + ellipsis) > maxWidth) end--
        return if (end == 0) ellipsis else text.take(end) + ellipsis
    }
}
