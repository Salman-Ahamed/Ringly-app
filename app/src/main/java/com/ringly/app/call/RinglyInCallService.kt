package com.ringly.app.call

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import com.ringly.app.R
import com.ringly.app.RinglyApp
import com.ringly.app.overlay.CallerIdCard
import com.ringly.app.sync.SyncLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class RinglyInCallService : InCallService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val callbacks = HashMap<String, InCallCallback>()

    private var lastNumber: String? = null
    private var lastName: String = ""
    private var lastPhoto: String? = null
    private var lastLabel: String? = null

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        activeCall = call
        activeService = this
        val cb = InCallCallback(::onCallUpdated)
        callbacks[idOf(call)] = cb
        runCatching { call.registerCallback(cb) }
            .onFailure { SyncLog.w(TAG, "callback registration failed: ${it.message}") }
        present(call, resolve = true)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        callbacks.remove(idOf(call))?.let { cb ->
            runCatching { call.unregisterCallback(cb) }
        }
        if (call === activeCall) {
            activeCall = null
            pushEnded()
        }
    }

    override fun onDestroy() {
        activeCall = null
        activeService = null
        callbacks.clear()
        scope.cancel()
        super.onDestroy()
    }

    private fun onCallUpdated(call: Call) {
        if (call.state == Call.STATE_DISCONNECTED) {
            pushEnded()
        } else {
            present(call, resolve = false)
        }
    }

    private fun present(call: Call, resolve: Boolean) {
        val phase = InCallUiMapper.phaseFor(call.state)
        if (phase == InCallPhase.ENDED) return pushEnded()
        val number = call.details.handle?.schemeSpecificPart
        if (resolve) {
            scope.launch {
                val card = resolveCard(number)
                lastNumber = number
                lastName = card.name
                lastPhoto = card.photoUrl
                lastLabel = card.sourceLabel
                pushScreen(phase, number)
            }
        } else {
            pushScreen(phase, number)
        }
    }

    private fun pushScreen(phase: InCallPhase, number: String?) {
        val isSame = number != null && lastNumber == number
        val screen = InCallUiMapper.screen(
            phase = phase,
            number = number,
            title = getString(titleRes(phase)),
            callerName = if (isSame) lastName.ifBlank { number } else (number ?: ""),
            photoUrl = if (isSame) lastPhoto else null,
            sourceLabel = if (isSame) lastLabel else null
        )
        showScreen(screen)
    }

    private fun pushEnded() {
        lastNumber = null
        lastName = ""
        lastPhoto = null
        lastLabel = null
        val screen = InCallUiMapper.screen(
            phase = InCallPhase.ENDED,
            number = activeCall?.details?.handle?.schemeSpecificPart,
            title = getString(titleRes(InCallPhase.ENDED)),
            callerName = ""
        )
        showScreen(screen)
    }

    private fun showScreen(screen: InCallScreen) {
        SyncLog.d(TAG, "in-call UI: ${screen.phase} ${screen.number ?: ""}")
        val intent = Intent(this, InCallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(InCallActivity.EXTRA_PHASE, screen.phase.name)
            .putExtra(InCallActivity.EXTRA_NUMBER, screen.number)
            .putExtra(InCallActivity.EXTRA_NAME, screen.callerName)
            .putExtra(InCallActivity.EXTRA_PHOTO, screen.photoUrl)
            .putExtra(InCallActivity.EXTRA_LABEL, screen.sourceLabel)
        runCatching { startActivity(intent) }
            .onFailure { SyncLog.w(TAG, "in-call UI launch failed: ${it.message}") }
    }

    private suspend fun resolveCard(number: String?): CallerIdCard {
        val n = number?.takeIf { it.isNotBlank() }
            ?: return CallerIdCard(number = "", name = "", sourceLabel = "")
        return (applicationContext as? RinglyApp)?.callerIdCardProvider?.invoke(n)
            ?: CallerIdCard(number = n, name = n, sourceLabel = "")
    }

    private fun titleRes(phase: InCallPhase): Int = when (phase) {
        InCallPhase.RINGING -> R.string.incall_title_incoming
        InCallPhase.DIALING -> R.string.incall_title_calling
        InCallPhase.ACTIVE -> R.string.incall_title_active
        InCallPhase.ENDED -> R.string.incall_title_ended
    }

    private fun idOf(call: Call): String = "call:${System.identityHashCode(call)}"

    companion object {
        private const val TAG = "RinglyInCall"

        @Volatile
        var activeCall: Call? = null
            private set

        @Volatile
        var activeService: RinglyInCallService? = null
            private set

        val activeActions: InCallActions
            get() {
                val call = activeCall ?: return NoOpInCallActions
                return TelecomCallActions(call, activeService)
            }
    }
}