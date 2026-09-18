package com.ringly.app.dialer

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build

object DialerRole {

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
                ?.isRoleHeld(RoleManager.ROLE_DIALER)
                ?: false
        }.getOrDefault(false)
    }

    fun requestIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            context.getSystemService(RoleManager::class.java)
                ?.createRequestRoleIntent(RoleManager.ROLE_DIALER)
        }.getOrNull()
    }
}