package com.ringly.app.sms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.ringly.app.R
import com.ringly.app.sync.SyncLog

class SmsNotification(private val context: Context) {

    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private var channelInitialized = false

    fun notifyNewMessage(address: String?, body: String) {
        ensureChannel()
        runCatching {
            val contentIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, SmsActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_sms)
                .setContentTitle(address?.takeIf { it.isNotBlank() } ?: context.getString(R.string.sms_unknown_sender))
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(contentIntent)
                .build()
            notificationManager.notify(notificationId(), notification)
        }.onFailure {
            SyncLog.w(TAG, "sms notification failed: ${it.message}")
        }
    }

    private fun ensureChannel() {
        if (channelInitialized) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.sms_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.sms_channel_desc)
            setShowBadge(true)
        }
        runCatching { notificationManager.createNotificationChannel(channel) }
            .onFailure { SyncLog.w(TAG, "createNotificationChannel failed: ${it.message}") }
        channelInitialized = true
    }

    private var nextNotificationId = NOTIFICATION_ID_BASE
    private fun notificationId(): Int = nextNotificationId++

    companion object {
        private const val TAG = "RinglySms"
        const val CHANNEL_ID = "sms_conversation"
        const val NOTIFICATION_ID_BASE = 100
    }
}