package com.ringly.app.call

import com.ringly.app.sync.SyncLog
import com.ringly.app.util.NumberNormalizer

object IncomingCallHandler {

    private const val TAG = "RingCall"
    private const val SESSION_TTL_MILLIS = 10 * 60 * 1000L
    const val UNKNOWN_SOURCE = "UNKNOWN"

    internal var ringDedupe: RingDedupe = RingDedupe()
    internal var clock: () -> Long = { System.currentTimeMillis() }

    private var sessionNumber: String? = null
    private var sessionOpenedAt: Long = Long.MIN_VALUE

    internal fun reset() {
        ringDedupe = RingDedupe()
        sessionNumber = null
        sessionOpenedAt = Long.MIN_VALUE
    }

    @Synchronized
    fun onRing(raw: String?, source: String = UNKNOWN_SOURCE): String? {
        val normalized = NumberNormalizer.normalize(raw) ?: run {
            SyncLog.w(TAG, "Incoming call [$source] ignored: non-normalizable number")
            return null
        }
        if (!ringDedupe.shouldDispatch(normalized)) return null
        if (isSessionActive() && sessionNumber == normalized) {
            SyncLog.d(TAG, "Incoming call [$source] re-ring for $normalized treated as new call (stale session)")
        } else {
            SyncLog.d(TAG, "Incoming call [$source] ringing: $normalized")
        }
        openSession(normalized)
        IncomingCallNotifier.notify(IncomingCallEvent(IncomingCallPhase.RINGING, normalized))
        return normalized
    }

    @Synchronized
    fun onStateChange(phase: IncomingCallPhase, raw: String?, source: String = UNKNOWN_SOURCE) {
        if (phase == IncomingCallPhase.RINGING) return
        if (!isSessionActive()) {
            SyncLog.d(TAG, "Call state [$source] $phase ignored: no active ring session")
            return
        }
        val normalized = NumberNormalizer.normalize(raw)
        if (normalized != null && sessionNumber != null && normalized != sessionNumber) {
            SyncLog.d(TAG, "Call state [$source] $phase ignored: number mismatch with ring session")
            return
        }
        if (phase == IncomingCallPhase.DISCONNECTED) closeSession()
        SyncLog.d(TAG, "Call state [$source] $phase${normalized?.let { " ($it)" } ?: ""}")
        IncomingCallNotifier.notify(IncomingCallEvent(phase, normalized))
    }

    private fun openSession(number: String) {
        sessionNumber = number
        sessionOpenedAt = clock()
    }

    private fun closeSession() {
        sessionNumber = null
        sessionOpenedAt = Long.MIN_VALUE
        ringDedupe = RingDedupe()
    }

    private fun isSessionActive(): Boolean {
        if (sessionNumber == null) return false
        if (clock() - sessionOpenedAt > SESSION_TTL_MILLIS) {
            closeSession()
            return false
        }
        return true
    }
}