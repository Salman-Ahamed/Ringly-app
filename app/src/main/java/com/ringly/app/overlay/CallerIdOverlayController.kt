package com.ringly.app.overlay

import com.ringly.app.call.IncomingCallEvent
import com.ringly.app.call.IncomingCallListener
import com.ringly.app.call.IncomingCallNotifier
import com.ringly.app.call.IncomingCallPhase
import com.ringly.app.data.models.LookupMatch
import com.ringly.app.data.models.LookupResponse
import com.ringly.app.sync.SyncLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CallerIdOverlayController(
    private val scope: CoroutineScope,
    private val lookup: suspend (String) -> Result<LookupResponse>,
    private val localNumbers: () -> Set<String>,
    private val canShowOverlay: () -> Boolean,
    private val renderer: OverlayRenderer,
    private val fallback: CallerIdFallback = NoOpCallerIdFallback,
    private val selectBest: (List<LookupMatch>) -> LookupMatch? = BestMatchSelector::selectBest,
    private val overlayTimeoutMillis: Long = STALE_OVERLAY_TIMEOUT_MILLIS,
    private val log: (String) -> Unit = { SyncLog.d(TAG, it) }
) : IncomingCallListener {

    private var job: Job? = null

    fun start() {
        IncomingCallNotifier.listener = this
    }

    fun stop() {
        endCall()
        if (IncomingCallNotifier.listener === this) IncomingCallNotifier.listener = null
    }

    override fun onCallEvent(event: IncomingCallEvent) {
        when (event.phase) {
            IncomingCallPhase.RINGING -> event.number?.let { handleRing(it) }
            IncomingCallPhase.ACTIVE, IncomingCallPhase.DISCONNECTED -> endCall()
        }
    }

    private fun handleRing(number: String) {
        if (localNumbers().contains(number)) {
            log("skip: $number is a local contact")
            return
        }
        job?.cancel()
        endCall()
        job = scope.launch {
            val response = try {
                lookup(number)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }.getOrNull()
            val match = response
                ?.takeIf { it.found }
                ?.matches
                ?.let(selectBest)
            when {
                match == null -> {
                    log("no pool match for $number -> notification fallback")
                    fallback.showUnknown(number)
                    scheduleTimeout()
                }
                canShowOverlay() -> {
                    log("pool match for $number -> ${match.name} (owner ${match.ownerName})")
                    renderer.show(match, number)
                    scheduleTimeout()
                }
                else -> {
                    log("pool match for $number but overlay permission missing -> notification fallback")
                    fallback.showMatch(match, number)
                    scheduleTimeout()
                }
            }
        }
    }

    private fun scheduleTimeout() {
        job = scope.launch {
            delay(overlayTimeoutMillis)
            endCall()
        }
    }

    private fun endCall() {
        job?.cancel()
        job = null
        renderer.dismiss()
        fallback.hide()
    }

    companion object {
        private const val TAG = "RinglyOverlay"
        const val STALE_OVERLAY_TIMEOUT_MILLIS = 5 * 60 * 1000L
    }
}