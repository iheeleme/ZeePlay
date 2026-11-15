/*
 * DiPlay — Zeekr adaptation. GPLv3 (see LICENSE).
 *
 * Host-side orchestrator for the Zeekr windshield HUD, mirroring the engine's
 * ZeekrHudBridge: shows the CarPlay HUD card/map stream on the HUD display,
 * keeps CarPlayNavigationState in step with the phone's guidance (opening the
 * wanted screen while the phone is actively guiding), and drives the AR-HUD
 * route feed over fdbus. It owns its own pump, so the host only needs start()
 * when a session's sink is ready and stop() when it ends.
 */
package com.shilapi.xcertplay

import android.app.Activity
import android.util.Log
import android.view.Surface
import com.shilapi.xcertplay.airplay.AirPlayInfoPlist
import com.shilapi.xcertplay.airplay.CarPlayNavigationState
import com.shilapi.xcertplay.airplay.HudScreen
import com.shilapi.xcertplay.hud.zeekr.ZeekrDisplayRouter
import com.shilapi.xcertplay.hud.zeekr.arhud.ZeekrArHudOutput
import com.shilapi.xcertplay.hud.zeekr.arhud.ZeekrArHudRoute
import com.shilapi.xcertplay.media.AndroidMediaSink
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class ZeekrHudHost(private val activity: Activity, private val sink: AndroidMediaSink) {
    private val arHud = ZeekrArHudOutput { tag, msg -> Log.i(TAG, "$tag: $msg") }
    private var presentation: ZeekrHudPresentation? = null
    @Volatile private var surface: Surface? = null
    private var ticker: ScheduledExecutorService? = null

    /** Show the HUD presentation and start the pump; no-op without a HUD display. */
    fun start() {
        val display = ZeekrDisplayRouter.hudDisplay(activity) ?: run {
            Log.i(TAG, "no HUD display; HUD host idle")
            return
        }
        activity.runOnUiThread {
            if (presentation == null) {
                val p = ZeekrHudPresentation(activity, display) { s -> activity.runOnUiThread { onSurface(s) } }
                p.setOnDismissListener { if (presentation === p) { presentation = null; onSurface(null) } }
                runCatching { p.show(); presentation = p; lastLayout = null; lastGuidance = null }
                    .onFailure { Log.w(TAG, "HUD presentation failed: ${it.message}") }
            }
        }
        if (ticker == null) {
            ticker = Executors.newSingleThreadScheduledExecutor { r ->
                Thread(r, "zeekr-hud").apply { isDaemon = true }
            }.also { it.scheduleWithFixedDelay({ runCatching { tick() } }, 500, 700, TimeUnit.MILLISECONDS) }
        }
    }

    /** Supply the current CarPlay route geometry for the AR-HUD (null clears it). */
    fun setArRoute(route: ZeekrArHudRoute?) = arHud.setRoute(route)

    fun stop() {
        ticker?.shutdownNow()
        ticker = null
        runCatching { arHud.clear() }
        activity.runOnUiThread {
            onSurface(null)
            runCatching { presentation?.dismiss() }
            presentation = null
        }
        CarPlayNavigationState.reset()
    }

    private fun tick() {
        // Polled here so the HUD stays current while the CarPlay screen is in the background.
        CarPlayNavigationState.setTurnGuidance(com.shilapi.xcertplay.hud.BydNavigationOutputs.turnGuidanceNow())
        arHud.tick(CarPlayNavigationState.guiding)
        apply()
        CarPlayNavigationState.setHudNightMode(if (ZeekrHudSettings.darkEnabled(activity)) true else null)
        applyLayout()
        pushGuidance()
    }

    private var lastGuidance: com.shilapi.xcertplay.hud.ClusterTurnGuidance? = null

    /** Compact mode: hand the latest turn to ZeePlay's own tile (null blanks it). */
    private fun pushGuidance() {
        val next = if (ZeekrHudSettings.compactEnabled(activity)) CarPlayNavigationState.turnGuidance else null
        if (next == lastGuidance) return
        lastGuidance = next
        activity.runOnUiThread { presentation?.setGuidance(next) }
    }

    private var lastLayout: ZeekrHudPresentation.Layout? = null

    /** Live placement from the settings sliders; no reconnect needed. */
    private fun applyLayout() {
        val mode = ZeekrHudSettings.mode(activity)
        val stream = when (mode) {
            ZeekrHudSettings.MODE_CARD -> AirPlayInfoPlist.CARD_WIDTH to AirPlayInfoPlist.CARD_HEIGHT
            ZeekrHudSettings.MODE_COMPACT -> 1 to 1 // square tile
            else -> ZeekrHudSettings.mapStreamSize(activity)
        }
        val next = ZeekrHudPresentation.Layout(
            stream.first, stream.second,
            ZeekrHudSettings.layoutSizePercent(activity, mode),
            ZeekrHudSettings.layoutXPercent(activity, mode),
            ZeekrHudSettings.layoutYPercent(activity, mode),
        )
        if (next == lastLayout) return
        lastLayout = next
        val name = when (mode) { ZeekrHudSettings.MODE_CARD -> "card"; ZeekrHudSettings.MODE_COMPACT -> "compact"; else -> "map" }
        WheelKeyLog.note("HUD host: layout $name ${next.streamWidth}x${next.streamHeight} size=${next.sizePercent}% x=${next.xPercent}% y=${next.yPercent}%")
        val compact = mode == ZeekrHudSettings.MODE_COMPACT
        activity.runOnUiThread {
            presentation?.setCompact(compact)
            presentation?.applyLayout(next)
        }
    }

    private var lastState: String? = null

    private fun apply() {
        val guiding = CarPlayNavigationState.guiding
        // One HUD surface and one mode: CarPlay map, CarPlay turn card, or ZeePlay's compact tile
        // (the tile needs no CarPlay stream).
        val card = ZeekrHudSettings.cardEnabled(activity)
        val map = !card && ZeekrHudSettings.mapEnabled(activity)
        CarPlayNavigationState.setWanted(HudScreen.CARD, guiding && card)
        CarPlayNavigationState.setWanted(HudScreen.MAP, guiding && map)
        val state = "guiding=$guiding owns=${CarPlayNavigationState.phoneOwnsGuidance} " +
            "route=${CarPlayNavigationState.routeGuidanceActive} card=$card map=$map compact=${ZeekrHudSettings.compactEnabled(activity)} " +
            "sessions(card=${CarPlayNavigationState.hasSession(HudScreen.CARD)} " +
            "map=${CarPlayNavigationState.hasSession(HudScreen.MAP)}) surface=${surface != null}"
        if (state != lastState) { lastState = state; WheelKeyLog.note("HUD host: $state") }
    }

    private fun onSurface(s: Surface?) {
        val prev = surface
        surface = s
        for (type in screenTypes()) {
            if (s != null) sink.setSurface(type, s) else if (prev != null) sink.clearSurface(type, prev)
        }
    }

    private fun screenTypes(): List<Int> = buildList {
        // Mutually exclusive on the single HUD surface; the turn card has priority over the map.
        if (ZeekrHudSettings.cardEnabled(activity)) add(AirPlayInfoPlist.STREAM_TYPE_CARD_SCREEN)
        else if (ZeekrHudSettings.mapEnabled(activity)) add(AirPlayInfoPlist.STREAM_TYPE_MAP_SCREEN)
    }

    companion object { private const val TAG = "Zeekr-HUD" }
}
