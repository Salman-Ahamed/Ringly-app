package com.ringly.app.sms

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

object SmsRole {

    enum class Status {
        GRANTED,
        NEEDED,
        UNAVAILABLE
    }

    fun isSupported(apiLevel: Int): Boolean = apiLevel >= 29

    fun status(apiLevel: Int, roleHeld: Boolean): Status = when {
        !isSupported(apiLevel) -> Status.UNAVAILABLE
        roleHeld -> Status.GRANTED
        else -> Status.NEEDED
    }

    fun isHeld(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return runCatching {
            context.getSystemService(RoleManager::class.java)
                ?.isRoleHeld(RoleManager.ROLE_SMS)
                ?: false
        }.getOrDefault(false)
    }

    fun requestIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            context.getSystemService(RoleManager::class.java)
                ?.createRequestRoleIntent(RoleManager.ROLE_SMS)
        }.getOrNull()
    }

    fun settingsIntent(): Intent {
        return Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}