package com.shilapi.xcertplay.hud

/**
 * The iPhone's current call as [CarPlayCallState] tracks it, for car outputs other than BYD's
 * (the Zeekr instrument cluster). Called from the iAP2 thread; listeners must hand off quickly.
 */
object CarPlayCallEvents {
    @Volatile private var listener: ((CarPlayCallCard?) -> Unit)? = null

    fun setListener(next: ((CarPlayCallCard?) -> Unit)?) {
        listener = next
    }

    internal fun publish(card: CarPlayCallCard?) {
        runCatching { listener?.invoke(card) }
    }
}
