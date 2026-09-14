package com.ringly.app.overlay

import com.ringly.app.call.IncomingCallEvent
import com.ringly.app.call.IncomingCallListener
import com.ringly.app.call.IncomingCallNotifier
import com.ringly.app.call.IncomingCallPhase
import com.ringly.app.data.models.LookupMatch
import com.ringly.app.data.models.LookupResponse
import com.ringly.app.sync.SyncLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class CallerIdOverlayController(
    private val scope: CoroutineScope,
    private val lookup: suspend (String) -> Result<LookupResponse>,
    private val localNumbers: () -> Set<String>,
    private val canShowOverlay: () -> Boolean,
    private val renderer: OverlayRenderer,
    private val selectBest: (List<LookupMatch>) -> LookupMatch? = BestMatchSelector::selectBest,
    private val log: (String) -> Unit = { SyncLog.d(TAG, it) }
) : IncomingCallListener {

    private var lookupJob: Job? = null

    fun start() {
        IncomingCallNotifier.listener = this
    }

    fun stop() {
        lookupJob?.cancel()
        lookupJob = null
        if (IncomingCallNotifier.listener === this) IncomingCallNotifier.listener = null
    }

    override fun onCallEvent(event: IncomingCallEvent) {
        when (event.phase) {
            IncomingCallPhase.RINGING -> event.number?.let { handleRing(it) }
            IncomingCallPhase.ACTIVE, IncomingCallPhase.DISCONNECTED -> handleEnd()
        }
    }

    private fun handleRing(number: String) {
        if (!canShowOverlay()) {
            log("skip: overlay permission unavailable")
            return
        }
        if (localNumbers().contains(number)) {
            log("skip: $number is a local contact")
            return
        }
        lookupJob?.cancel()
        renderer.dismiss()
        lookupJob = scope.launch {
            val result = runCatching { lookup(number) }.getOrElse { Result.failure(it) }
            val response = result.getOrNull()
            val match = response
                ?.takeIf { it.found }
                ?.matches
                ?.let(selectBest)
            if (match == null) {
                log("skip: no pool match for $number")
                return@launch
            }
            log("pool match for $number -> ${match.name} (owner ${match.ownerName})")
            renderer.show(match, number)
        }
    }

    private fun handleEnd() {
        lookupJob?.cancel()
        lookupJob = null
        renderer.dismiss()
    }

    companion object {
        private const val TAG = "RinglyOverlay"
    }
}