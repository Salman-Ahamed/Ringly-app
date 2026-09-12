package com.ringly.app.call

import android.os.Handler
import android.os.Looper

enum class IncomingCallPhase { RINGING, ACTIVE, DISCONNECTED }

data class IncomingCallEvent(
    val phase: IncomingCallPhase,
    val number: String?
)

fun interface IncomingCallListener {
    fun onCallEvent(event: IncomingCallEvent)
}

object IncomingCallNotifier {

    @Volatile
    var listener: IncomingCallListener? = null

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private fun isMainThread(): Boolean =
        runCatching { Looper.myLooper() == Looper.getMainLooper() }.getOrDefault(true)

    fun notify(event: IncomingCallEvent) {
        if (listener == null) return
        if (isMainThread()) {
            runCatching { listener?.onCallEvent(event) }
        } else {
            mainHandler.post { runCatching { listener?.onCallEvent(event) } }
        }
    }
}