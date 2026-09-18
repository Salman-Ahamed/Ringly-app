package com.ringly.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class WapPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // MMS is out of scope — declared to satisfy default-SMS-app qualification.
    }
}