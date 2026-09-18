package com.ringly.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SmsBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // No pending sends are tracked in this phase — nothing to restore on boot.
    }
}