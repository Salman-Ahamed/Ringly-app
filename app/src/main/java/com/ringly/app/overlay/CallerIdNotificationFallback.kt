package com.ringly.app.overlay

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ringly.app.MainActivity
import com.ringly.app.R
import com.ringly.app.data.models.LookupMatch
import com.ringly.app.sync.SyncLog

class CallerIdNotificationFallback(private val context: Context) : CallerIdFallback {

    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private var channelInitialized = false

    override fun showUnknown(number: String) {
        show(
            title = number,
            text = context.getString(R.string.overlay_incoming_call)
        )
    }

    override fun showMatch(match: LookupMatch, number: String) {
        val name = match.name.ifBlank { number }
        val owner = context.getString(R.string.overlay_saved_by_format, match.ownerName)
        show(title = name, text = "$number  •  $owner")
    }

    override fun hide() {
        runCatching { notificationManager.cancel(NOTIFICATION_ID) }
    }

    private fun show(title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            SyncLog.d(TAG, "notification fallback skipped: POST_NOTIFICATIONS not granted")
            return
        }
        ensureChannel()
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
        runCatching { notificationManager.notify(NOTIFICATION_ID, notification) }
            .onFailure { SyncLog.w(TAG, "notification failed: ${it.message}") }
    }

    private fun ensureChannel() {
        if (channelInitialized) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.caller_id_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.caller_id_channel_desc)
            setShowBadge(false)
        }
        runCatching { notificationManager.createNotificationChannel(channel) }
            .onFailure { SyncLog.w(TAG, "createNotificationChannel failed: ${it.message}") }
        channelInitialized = true
    }

    companion object {
        private const val TAG = "RinglyOverlay"
        const val CHANNEL_ID = "caller_id"
        const val NOTIFICATION_ID = 2
    }
}