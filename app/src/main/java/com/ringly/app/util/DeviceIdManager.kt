package com.ringly.app.util

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.util.UUID

object DeviceIdManager {

    private const val PREFS = "ringly_prefs"
    private const val KEY_DEVICE_ID = "device_id"
    private const val FALLBACK_ANDROID_ID = "9774d56d682e549c"

    @SuppressLint("HardwareIds")
    fun getOrCreateDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_DEVICE_ID, null)?.let { return it }

        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            null
        }

        val deviceId = if (!androidId.isNullOrBlank() && androidId != FALLBACK_ANDROID_ID) {
            "a-$androidId"
        } else {
            "u-${UUID.randomUUID()}"
        }

        prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        return deviceId
    }
}