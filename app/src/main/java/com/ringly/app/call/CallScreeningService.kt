package com.ringly.app.call

import android.os.Build
import android.telecom.Call
import android.telecom.Call.Details
import android.telecom.CallScreeningService

class CallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Details) {
        if (Build.VERSION.SDK_INT >= 31) {
            when (callDetails.state) {
                Call.STATE_RINGING -> {
                    IncomingCallHandler.onRing(callDetails.handle?.schemeSpecificPart)
                    respondToCall(callDetails, CallResponse.Builder().build())
                }
                Call.STATE_ACTIVE ->
                    IncomingCallHandler.onStateChange(IncomingCallPhase.ACTIVE, null)
                Call.STATE_DISCONNECTED ->
                    IncomingCallHandler.onStateChange(IncomingCallPhase.DISCONNECTED, null)
            }
        } else {
            IncomingCallHandler.onRing(callDetails.handle?.schemeSpecificPart)
            respondToCall(callDetails, CallResponse.Builder().build())
        }
    }
}