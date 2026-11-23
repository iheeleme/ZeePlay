package com.shilapi.xcertplay

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.shilapi.xcertplay.hud.CarPlayCallCard
import com.shilapi.xcertplay.hud.CarPlayCallEvents
import java.lang.reflect.Proxy

/**
 * CarPlay calls on the Zeekr 001 instrument cluster (zeekr edition only).
 *
 * The cluster's call card belongs to the ECARX DimInteraction API, the same one the car's Bluetooth
 * phone uses: DimInteraction.create(context) -> getPhoneCallInteraction(), then
 * updateCallInfo(ICallInfo) on every change of the call and registerPhoneCallback(...) for the
 * cluster's own buttons. The card counts the call time itself from getCallDuration(), so a call is
 * sent only when it changes. Call status, as the car's phone sends it: 12 incoming, 6 outgoing,
 * 16 connected, 17 on hold, 0 no call.
 *
 * ICallInfo and the callback are satisfied with reflection [Proxy]s; no vendor code is linked
 * (clean-room). The cluster's answer and hang-up buttons reach the iPhone through the same CarPlay
 * telephony keys as the steering wheel. They act only while a CarPlay call is shown, so a call on
 * the car's own phone is left alone, and an idle card is sent only to clear one ZeePlay showed.
 */
internal object ZeekrClusterCall {
    private const val TAG = "Zeekr-ClusterCall"
    private const val PKG = "com.ecarx.xui.adaptapi.diminteraction"
    private const val STATUS_NONE = 0
    private const val STATUS_OUTGOING = 6
    private const val STATUS_INCOMING = 12
    private const val STATUS_CONNECTED = 16
    private const val CALL_ID = 1

    private val thread by lazy { HandlerThread("zeekr-cluster-call").apply { start() } }
    private val handler by lazy { Handler(thread.looper) }

    @Volatile private var installed = false
    private var app: Context? = null // handler thread
    private var phone: Any? = null // IPhoneCallInteraction; handler thread
    private var infoCls: Class<*>? = null
    private var updateCallInfo: java.lang.reflect.Method? = null
    private var callback: Any? = null
    private var card: CarPlayCallCard? = null // handler thread
    private var shown = false // handler thread: the cluster shows a ZeePlay call
    private var failureNoted = false

    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            val appContext = context.applicationContext
            handler.post { app = appContext }
            CarPlayCallEvents.setListener { next -> handler.post { onCall(next) } }
            installed = true
        }
    }

    private fun onCall(next: CarPlayCallCard?) {
        card = next
        if (next == null && !shown) return
        send()
    }

    private fun send() {
        val target = bind() ?: return
        val current = card
        val status = statusOf(current)
        val info = info(current, status)
        runCatching {
            updateCallInfo!!.invoke(target, info)
        }.onSuccess {
            shown = status != STATUS_NONE
            WheelKeyLog.note("Cluster call: status=$status")
        }.onFailure {
            Log.w(TAG, "updateCallInfo failed", it)
            WheelKeyLog.note("Cluster call: updateCallInfo failed ${rootName(it)}")
        }
    }

    private fun bind(): Any? {
        phone?.let { return it }
        val context = app ?: return null
        return runCatching {
            val dimCls = Class.forName("$PKG.DimInteraction")
            val dim = dimCls.getMethod("create", Context::class.java).invoke(null, context)
                ?: error("DimInteraction.create returned null")
            val target = dimCls.getMethod("getPhoneCallInteraction").invoke(dim)
                ?: error("no phone call interaction")
            val phoneCls = Class.forName("$PKG.IPhoneCallInteraction")
            infoCls = Class.forName("$PKG.IPhoneCallInteraction\$ICallInfo")
            val callbackCls = Class.forName("$PKG.IPhoneCallInteraction\$IPhoneCallInteractionCallback")
            val listener = Proxy.newProxyInstance(callbackCls.classLoader, arrayOf(callbackCls)) { proxy, method, args ->
                when (method.name) {
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.getOrNull(0)
                    "toString" -> "ZeePlayClusterCallCallback"
                    else -> { handler.post { onClusterButton(method.name) }; null }
                }
            }
            updateCallInfo = phoneCls.getMethod("updateCallInfo", infoCls)
            phoneCls.getMethod("registerPhoneCallback", callbackCls).invoke(target, listener)
            callback = listener
            phone = target
            WheelKeyLog.note("Cluster call: connected")
            target
        }.onFailure {
            Log.w(TAG, "cluster call init failed", it)
            if (!failureNoted) {
                failureNoted = true
                WheelKeyLog.note("Cluster call: init failed ${rootName(it)}")
            }
        }.getOrNull()
    }

    private fun onClusterButton(name: String) {
        val current = card
        WheelKeyLog.note("Cluster call: button $name call=${current?.phase}")
        if (name == "onCallInfoUpdateRequired") {
            if (shown || current != null) send()
            return
        }
        if (current == null) return
        val controller = CarPlayBackgroundSession.snapshot()?.controller ?: return
        val sent = when (name) {
            "onAnswerCall", "onAnswerAndHoldCall", "onAnswerAndEndCall" ->
                if (current.phase == CarPlayCallCard.Phase.RINGING) controller.answerCall() else null
            "onEndCall", "onIgnoreCall" -> controller.endCall()
            else -> null
        } ?: return
        Log.i(TAG, "cluster $name sent=$sent")
    }

    private fun statusOf(card: CarPlayCallCard?): Int = when (card?.phase) {
        null -> STATUS_NONE
        CarPlayCallCard.Phase.RINGING -> STATUS_INCOMING
        CarPlayCallCard.Phase.DIALING -> STATUS_OUTGOING
        CarPlayCallCard.Phase.ACTIVE -> STATUS_CONNECTED
    }

    /** ICallInfo via a reflection Proxy: the vendor impl reads it through getters only. */
    private fun info(card: CarPlayCallCard?, status: Int): Any {
        val cls = infoCls!!
        val name = card?.name.orEmpty()
        val duration = card?.activeSinceMillis?.let { (System.currentTimeMillis() - it).coerceAtLeast(0L) } ?: 0L
        return Proxy.newProxyInstance(cls.classLoader, arrayOf(cls)) { proxy, method, args ->
            when (method.name) {
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.getOrNull(0)
                "toString" -> "ZeePlayCallInfo(status=$status)"
                "getCallStatus" -> status
                "getCallId", "getActiveCallId" -> if (status == STATUS_NONE) 0 else CALL_ID
                "getContactName", "getContactNumber" -> name
                "getCallDuration" -> duration
                "isHandFree" -> true
                "getCallCount" -> if (status == STATUS_NONE) 0 else 1
                else -> defaultFor(method.returnType)
            }
        }
    }

    private fun defaultFor(t: Class<*>): Any? = when (t) {
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE, java.lang.Short.TYPE, java.lang.Byte.TYPE -> 0
        Character.TYPE -> '\u0000'
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0f
        java.lang.Double.TYPE -> 0.0
        String::class.java -> ""
        else -> null
    }

    private fun rootName(t: Throwable): String {
        var cause: Throwable = t
        while (cause is java.lang.reflect.InvocationTargetException && cause.cause != null) cause = cause.cause!!
        return "${cause.javaClass.simpleName}: ${cause.message}"
    }
}
