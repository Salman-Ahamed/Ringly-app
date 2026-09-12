package com.ringly.app.call

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.telephony.PhoneStateListener
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.ringly.app.sync.SyncLog
import com.ringly.app.util.PermissionHelper

@Suppress("DEPRECATION")
class PhoneStateMonitor(private val context: Context) {

    private val callback = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, incomingNumber: String?) {
            onPhoneState(state, incomingNumber)
        }
    }

    private val registeredManagers = mutableListOf<TelephonyManager>()

    fun register() {
        if (registeredManagers.isNotEmpty()) return
        if (!PermissionHelper.hasPermission(context, Manifest.permission.READ_PHONE_STATE)) return
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return
        val managers = try {
            subscriptionManagers(telephony)
        } catch (e: Exception) {
            SyncLog.w(TAG, "Subscription list unavailable, using default subscription", e)
            listOf(telephony)
        }
        managers.forEach { manager ->
            try {
                manager.listen(callback, PhoneStateListener.LISTEN_CALL_STATE)
                registeredManagers += manager
            } catch (e: Exception) {
                SyncLog.w(TAG, "Phone state listener registration failed", e)
            }
        }
        if (registeredManagers.isNotEmpty()) {
            SyncLog.d(TAG, "Phone state listeners registered: ${registeredManagers.size}")
        }
    }

    fun unregister() {
        if (registeredManagers.isEmpty()) return
        registeredManagers.forEach { manager ->
            runCatching { manager.listen(callback, PhoneStateListener.LISTEN_NONE) }
        }
        registeredManagers.clear()
        SyncLog.d(TAG, "Phone state listeners unregistered")
    }

    private fun onPhoneState(state: Int, incomingNumber: String?) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING ->
                IncomingCallHandler.onRing(incomingNumber)
            TelephonyManager.CALL_STATE_OFFHOOK ->
                IncomingCallHandler.onStateChange(IncomingCallPhase.ACTIVE, null)
            TelephonyManager.CALL_STATE_IDLE ->
                IncomingCallHandler.onStateChange(IncomingCallPhase.DISCONNECTED, null)
        }
    }

    @SuppressLint("MissingPermission")
    private fun subscriptionManagers(default: TelephonyManager): List<TelephonyManager> {
        val subscriptionManager = context
            .getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            ?: return listOf(default)
        val subscriptionIds = subscriptionManager.activeSubscriptionInfoList
            ?.map { it.subscriptionId }
            .orEmpty()
        if (subscriptionIds.isEmpty()) return listOf(default)
        return subscriptionIds.map { default.createForSubscriptionId(it) }
    }

    private companion object {
        const val TAG = "RingCall"
    }
}