package com.ringly.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.ringly.app.sync.SyncLog
import com.ringly.app.util.AppForeground

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val isDeliver = action == Telephony.Sms.Intents.SMS_DELIVER_ACTION
        val isReceived = action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION
        if (!isDeliver && !isReceived) return

        if (!SmsRole.isHeld(context.applicationContext)) {
            SyncLog.d(TAG, "incoming sms ignored: not the default SMS app")
            return
        }

        val messages = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }
            .getOrNull()
            ?: return
        if (messages.isEmpty()) return

        val repository = SmsRepository(context.applicationContext)
        val firstAddress = messages.firstOrNull()?.originatingAddress
        var inserted = true
        for (message in messages) {
            if (!repository.insertIncoming(
                    message.originatingAddress,
                    message.messageBody,
                    message.timestampMillis
                )
            ) {
                inserted = false
            }
        }
        if (inserted) {
            SyncLog.d(TAG, "incoming sms persisted (address=${firstAddress ?: "unknown"})")
        }

        if (!AppForeground.isForeground()) {
            val body = messages.joinToString("") { it.messageBody.orEmpty() }
            SmsNotification(context.applicationContext).notifyNewMessage(firstAddress, body)
        }
    }

    companion object {
        private const val TAG = "RinglySmsReceiver"
    }
}