/*
 * ZeePlay — Zeekr adaptation. GPLv3 (see LICENSE).
 *
 * Keeps the saved car hotspot details in step with the car on the Zeekr edition: when hotspot import is
 * on (the default there), the car's own hotspot is read directly before connecting and whenever the app
 * comes back, so the driver never types the name or password. Read-only; nothing is written to the car.
 * Without the platform signature the read fails quietly and the saved (hand-entered) details are used.
 */
package com.shilapi.xcertplay

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.shilapi.xcertplay.host.BuildConfig
import com.shilapi.xcertplay.network.CarHotspotConfigRead
import com.shilapi.xcertplay.network.CarHotspotSettings

internal object ZeekrHotspotAutoRead {
    private const val TAG = "DiPlay-Hotspot"
    private const val MIN_INTERVAL_MS = 30_000L
    @Volatile private var lastRunMs = 0L
    @Volatile private var lastNote: String? = null

    /** True when the saved manual hotspot details changed. At most one read per 30 s. */
    fun refresh(context: Context, nowMs: Long = SystemClock.elapsedRealtime()): Boolean {
        if (!BuildConfig.IS_ZEEKR || !CarHotspotSettings.configWrite(context)) return false
        if (lastRunMs != 0L && nowMs - lastRunMs < MIN_INTERVAL_MS) return false
        lastRunMs = nowMs
        val app = context.applicationContext
        val read = runCatching { CarHotspotConfigRead.readLocalOnly(app, log = { Log.i(TAG, it) }) }.getOrNull()
        val car = read?.credentials
        if (car == null) {
            note("car hotspot auto-read: credentials=false reason=${read?.reason}")
            return false
        }
        val changed = car.ssid != AirPlayPersistence.loadManualHotspotSsid(app) ||
            car.passphrase != AirPlayPersistence.loadManualHotspotPassphrase(app) ||
            car.security != AirPlayPersistence.loadManualHotspotSecurity(app)
        if (changed) {
            AirPlayPersistence.saveManualHotspotSsid(app, car.ssid)
            AirPlayPersistence.saveManualHotspotPassphrase(app, car.passphrase)
            AirPlayPersistence.saveManualHotspotSecurity(app, car.security)
        }
        // Never the name or password: only whether they were read and changed.
        note("car hotspot auto-read: credentials=true changed=$changed")
        return changed
    }

    private fun note(message: String) {
        if (message == lastNote) return
        lastNote = message
        WheelKeyLog.note(message)
    }
}
