package com.ringly.app.call

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.material.button.MaterialButton
import com.ringly.app.R
import com.ringly.app.sync.SyncLog

class InCallActivity : AppCompatActivity() {

    private lateinit var titleText: TextView
    private lateinit var photoImage: ImageView
    private lateinit var initialText: TextView
    private lateinit var nameText: TextView
    private lateinit var numberText: TextView
    private lateinit var sourceText: TextView
    private lateinit var answerButton: View
    private lateinit var declineButton: View
    private lateinit var endButton: View
    private lateinit var muteButton: MaterialButton
    private lateinit var speakerButton: MaterialButton

    private var muted = false
    private var speaker = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val finishRunnable = Runnable { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        setContentView(R.layout.activity_in_call)

        titleText = findViewById(R.id.call_title_text)
        photoImage = findViewById(R.id.incall_photo)
        initialText = findViewById(R.id.incall_initial)
        nameText = findViewById(R.id.caller_name_text)
        numberText = findViewById(R.id.caller_number_text)
        sourceText = findViewById(R.id.caller_source_text)
        answerButton = findViewById(R.id.answer_button)
        declineButton = findViewById(R.id.decline_button)
        endButton = findViewById(R.id.end_button)
        muteButton = findViewById(R.id.mute_button)
        speakerButton = findViewById(R.id.speaker_button)

        answerButton.setOnClickListener { RinglyInCallService.activeActions.answer() }
        declineButton.setOnClickListener { RinglyInCallService.activeActions.reject() }
        endButton.setOnClickListener { RinglyInCallService.activeActions.disconnect() }
        muteButton.setOnClickListener {
            muted = !muted
            RinglyInCallService.activeActions.setMute(muted)
            renderMute()
        }
        speakerButton.setOnClickListener {
            speaker = !speaker
            RinglyInCallService.activeActions.setSpeaker(speaker)
            renderSpeaker()
        }

        render(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render(intent)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(finishRunnable)
        super.onDestroy()
    }

    private fun render(intent: Intent) {
        mainHandler.removeCallbacks(finishRunnable)
        val phase = intent.getStringExtra(EXTRA_PHASE)
            ?.let { runCatching { InCallPhase.valueOf(it) }.getOrNull() }
            ?: return
        val number = intent.getStringExtra(EXTRA_NUMBER)
        val callerName = intent.getStringExtra(EXTRA_NAME).orEmpty()
        val photoUrl = intent.getStringExtra(EXTRA_PHOTO)
        val sourceLabel = intent.getStringExtra(EXTRA_LABEL)

        titleText.text = getString(
            when (phase) {
                InCallPhase.RINGING -> R.string.incall_title_incoming
                InCallPhase.DIALING -> R.string.incall_title_calling
                InCallPhase.ACTIVE -> R.string.incall_title_active
                InCallPhase.ENDED -> R.string.incall_title_ended
            }
        )

        nameText.text = callerName.ifBlank { number ?: "" }
        if (number.isNullOrBlank() || nameText.text.toString() == number) {
            numberText.visibility = View.GONE
        } else {
            numberText.visibility = View.VISIBLE
            numberText.text = number
        }
        if (sourceLabel.isNullOrBlank()) {
            sourceText.visibility = View.GONE
        } else {
            sourceText.visibility = View.VISIBLE
            sourceText.text = sourceLabel
        }

        initialText.text = callerName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        if (photoUrl.isNullOrBlank()) {
            showInitialOnly()
        } else {
            photoImage.load(photoUrl) {
                transformations(CircleCropTransformation())
                crossfade(true)
                listener(
                    onStart = { photoImage.visibility = View.GONE },
                    onSuccess = { _, _ ->
                        photoImage.visibility = View.VISIBLE
                        initialText.visibility = View.GONE
                    },
                    onError = { _, _ -> showInitialOnly() }
                )
            }
        }

        answerButton.visibility = viewIf(phase == InCallPhase.RINGING)
        declineButton.visibility = viewIf(phase == InCallPhase.RINGING)
        endButton.visibility = viewIf(phase == InCallPhase.ACTIVE || phase == InCallPhase.DIALING)
        muteButton.visibility = viewIf(phase == InCallPhase.ACTIVE)
        speakerButton.visibility = viewIf(phase == InCallPhase.ACTIVE)
        renderMute()
        renderSpeaker()

        if (phase == InCallPhase.ENDED) {
            mainHandler.postDelayed(finishRunnable, ENDED_DISMISS_MILLIS)
        }
    }

    private fun showInitialOnly() {
        photoImage.visibility = View.GONE
        initialText.visibility = View.VISIBLE
    }

    private fun viewIf(visible: Boolean): Int =
        if (visible) View.VISIBLE else View.GONE

    private fun renderMute() {
        muteButton.text = getString(if (muted) R.string.incall_unmute else R.string.incall_mute)
        muteButton.isSelected = muted
    }

    private fun renderSpeaker() {
        speakerButton.text =
            getString(if (speaker) R.string.incall_speaker_off else R.string.incall_speaker)
        speakerButton.isSelected = speaker
    }

    companion object {
        private const val TAG = "RinglyInCall"
        const val EXTRA_PHASE = "ringly_incall_phase"
        const val EXTRA_NUMBER = "ringly_incall_number"
        const val EXTRA_NAME = "ringly_incall_name"
        const val EXTRA_PHOTO = "ringly_incall_photo"
        const val EXTRA_LABEL = "ringly_incall_label"
        const val ENDED_DISMISS_MILLIS = 1200L
    }
}