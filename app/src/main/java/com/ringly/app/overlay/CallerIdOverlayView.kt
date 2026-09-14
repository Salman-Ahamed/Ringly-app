package com.ringly.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import coil.load
import coil.transform.CircleCropTransformation
import com.ringly.app.R
import com.ringly.app.data.models.LookupMatch
import com.ringly.app.sync.SyncLog

class CallerIdOverlayView(private val context: Context) : OverlayRenderer {

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var cardView: View? = null

    override fun show(match: LookupMatch, number: String) {
        cardView?.let { old ->
            old.animate().cancel()
            old.clearAnimation()
            removeView(old)
        }
        val view = buildView(match, number)
        addView(view)
    }

    override fun dismiss() {
        cardView?.let { view ->
            view.animate()
                .alpha(0f)
                .setDuration(FADE_MILLIS)
                .withEndAction { removeView(view) }
                .start()
        }
    }

    private fun buildView(match: LookupMatch, number: String): View {
        val view = LayoutInflater.from(context).inflate(R.layout.overlay_call, null)
        val name = match.name.ifBlank { number }
        view.findViewById<TextView>(R.id.overlay_caller_name).text = name
        view.findViewById<TextView>(R.id.overlay_caller_number).text = number
        view.findViewById<TextView>(R.id.overlay_owner_text).text =
            context.getString(R.string.overlay_saved_by_format, match.ownerName)
        view.findViewById<TextView>(R.id.overlay_hint_text).text =
            context.getString(R.string.overlay_incoming_call)

        val photoImage = view.findViewById<ImageView>(R.id.overlay_photo_image)
        val initialText = view.findViewById<TextView>(R.id.overlay_initial_text)
        initialText.text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

        val photoUrl = match.photoUrl
        if (photoUrl.isNullOrBlank()) {
            showInitialOnly(photoImage, initialText)
        } else {
            photoImage.load(photoUrl) {
                transformations(CircleCropTransformation())
                crossfade(true)
                listener(
                    onStart = { photoImage.visibility = View.GONE },
                    onSuccess = { _, _ -> photoImage.visibility = View.VISIBLE; initialText.visibility = View.GONE },
                    onError = { _, _ -> showInitialOnly(photoImage, initialText) }
                )
            }
        }
        view.alpha = 0f
        return view
    }

    private fun showInitialOnly(photoImage: ImageView, initialText: TextView) {
        photoImage.visibility = View.GONE
        initialText.visibility = View.VISIBLE
    }

    private fun addView(view: View) {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
        view.animate()
            .alpha(1f)
            .setDuration(FADE_MILLIS)
            .withStartAction { }
            .start()
        runCatching { windowManager.addView(view, params) }
            .onSuccess { cardView = view }
            .onFailure { SyncLog.w(TAG, "addView failed", it) }
    }

    private fun removeView(view: View) {
        if (view.parent == null) return
        runCatching { windowManager.removeView(view) }
            .onFailure { SyncLog.w(TAG, "removeView failed", it) }
        if (cardView === view) cardView = null
    }

    private companion object {
        const val TAG = "RinglyOverlay"
        const val FADE_MILLIS = 150L
    }
}