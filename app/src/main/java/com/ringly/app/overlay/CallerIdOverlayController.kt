package com.ringly.app.overlay

import com.ringly.app.call.IncomingCallEvent
import com.ringly.app.call.IncomingCallListener
import com.ringly.app.call.IncomingCallNotifier
import com.ringly.app.call.IncomingCallPhase
import com.ringly.app.data.models.LookupMatch
import com.ringly.app.data.models.LookupResponse
import com.ringly.app.sync.SyncLog
import com.ringly.app.sync.SyncSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CallerIdOverlayController(
    private val scope: CoroutineScope,
    private val lookup: suspend (String) -> Result<LookupResponse>,
    private val ownContacts: () -> SyncSnapshot,
    private val canShowOverlay: () -> Boolean,
    private val sourceLabel: (CallerIdSource, String?) -> String,
    private val renderer: OverlayRenderer,
    private val fallback: CallerIdFallback = NoOpCallerIdFallback,
    private val selectBest: (List<LookupMatch>) -> LookupMatch? = BestMatchSelector::selectBest,
    private val overlayTimeoutMillis: Long = STALE_OVERLAY_TIMEOUT_MILLIS,
    private val isPresentationManagedElsewhere: () -> Boolean = { false },
    private val log: (String) -> Unit = { SyncLog.d(TAG, it) }
) : IncomingCallListener {

    private val resolverFlow = CallerIdResolverFlow(
        lookup = lookup,
        ownContacts = ownContacts,
        sourceLabel = sourceLabel,
        selectBest = selectBest
    )

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
        job?.cancel()
        endCall()
        job = scope.launch {
            val card = resolverFlow.resolveCard(number)
            if (isPresentationManagedElsewhere()) {
                log("presentation managed elsewhere for $number (in-call UI) -> skip")
                return@launch
            }
            if (canShowOverlay()) {
                log("showing overlay for $number")
                renderer.show(card)
            } else {
                log("overlay permission missing for $number -> notification fallback")
                fallback.show(card)
            }
            scheduleTimeout()
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