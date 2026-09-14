package com.ringly.app.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

class CallReceiver : BroadcastReceiver() {

    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING ->
                IncomingCallHandler.onRing(
                    intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER),
                    SOURCE_BROADCAST
                )
            TelephonyManager.EXTRA_STATE_OFFHOOK ->
                IncomingCallHandler.onStateChange(IncomingCallPhase.ACTIVE, null, SOURCE_BROADCAST)
            TelephonyManager.EXTRA_STATE_IDLE ->
                IncomingCallHandler.onStateChange(IncomingCallPhase.DISCONNECTED, null, SOURCE_BROADCAST)
        }
    }

    private companion object {
        const val SOURCE_BROADCAST = "BROADCAST"
    }
}