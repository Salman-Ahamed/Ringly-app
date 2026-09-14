package com.ringly.app.call

import android.os.Build
import android.telecom.Call
import android.telecom.Call.Details
import android.telecom.CallScreeningService
import android.telecom.PhoneAccount
import com.ringly.app.sync.SyncLog

class CallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Details) {
        if (callDetails.handle?.scheme != PhoneAccount.SCHEME_TEL) {
            SyncLog.d(TAG, "Non-telephony call [$SOURCE_SCREENING] ignored: scheme=${callDetails.handle?.scheme}")
            runCatching { respondToCall(callDetails, CallResponse.Builder().build()) }
            return
        }
        if (Build.VERSION.SDK_INT >= 31) {
            when (callDetails.state) {
                Call.STATE_RINGING -> {
                    IncomingCallHandler.onRing(callDetails.handle?.schemeSpecificPart, SOURCE_SCREENING)
                    respondToCall(callDetails, CallResponse.Builder().build())
                }
                Call.STATE_ACTIVE ->
                    IncomingCallHandler.onStateChange(IncomingCallPhase.ACTIVE, null, SOURCE_SCREENING)
                Call.STATE_DISCONNECTED ->
                    IncomingCallHandler.onStateChange(IncomingCallPhase.DISCONNECTED, null, SOURCE_SCREENING)
            }
        } else {
            IncomingCallHandler.onRing(callDetails.handle?.schemeSpecificPart, SOURCE_SCREENING)
            runCatching { respondToCall(callDetails, CallResponse.Builder().build()) }
        }
    }

    private companion object {
        const val TAG = "RingCall"
        const val SOURCE_SCREENING = "SCREENING"
    }
}