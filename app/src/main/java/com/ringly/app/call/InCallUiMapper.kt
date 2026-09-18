package com.ringly.app.call

enum class InCallPhase { RINGING, DIALING, ACTIVE, ENDED }

data class InCallScreen(
    val phase: InCallPhase,
    val number: String?,
    val callerName: String,
    val photoUrl: String?,
    val sourceLabel: String?,
    val title: String,
    val showAnswer: Boolean,
    val showDecline: Boolean,
    val showEnd: Boolean,
    val showMute: Boolean,
    val showSpeaker: Boolean
)

object InCallUiMapper {

    fun screen(
        phase: InCallPhase,
        number: String?,
        title: String,
        callerName: String? = null,
        photoUrl: String? = null,
        sourceLabel: String? = null
    ): InCallScreen {
        val name = callerName?.takeIf { it.isNotBlank() } ?: (number ?: "")
        return InCallScreen(
            phase = phase,
            number = number,
            callerName = name,
            photoUrl = photoUrl,
            sourceLabel = sourceLabel,
            title = title,
            showAnswer = phase == InCallPhase.RINGING,
            showDecline = phase == InCallPhase.RINGING,
            showEnd = phase == InCallPhase.ACTIVE || phase == InCallPhase.DIALING,
            showMute = phase == InCallPhase.ACTIVE,
            showSpeaker = phase == InCallPhase.ACTIVE
        )
    }

    fun phaseFor(state: Int): InCallPhase = when (state) {
        android.telecom.Call.STATE_RINGING -> InCallPhase.RINGING
        android.telecom.Call.STATE_DIALING -> InCallPhase.DIALING
        android.telecom.Call.STATE_ACTIVE -> InCallPhase.ACTIVE
        else -> InCallPhase.ENDED
    }
}