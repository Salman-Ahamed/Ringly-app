package com.ringly.app.onboarding

import android.Manifest

enum class PermissionAction {
    ALLOW_RUNTIME,
    OPEN_SETTINGS
}

enum class PermissionKind(
    val runtimePermission: String?,
    val action: PermissionAction
) {
    CONTACTS(Manifest.permission.READ_CONTACTS, PermissionAction.ALLOW_RUNTIME),
    PHONE_STATE(Manifest.permission.READ_PHONE_STATE, PermissionAction.ALLOW_RUNTIME),
    NOTIFICATIONS(Manifest.permission.POST_NOTIFICATIONS, PermissionAction.ALLOW_RUNTIME),
    OVERLAY(null, PermissionAction.OPEN_SETTINGS)
}

data class PermissionStep(
    val kind: PermissionKind,
    val isGranted: Boolean,
    val isPermanentlyDenied: Boolean = false
)

object PermissionChecklist {

    fun build(
        apiLevel: Int,
        hasContacts: Boolean,
        hasPhoneState: Boolean,
        hasNotifications: Boolean,
        canDrawOverlays: Boolean,
        permanentlyDenied: (PermissionKind) -> Boolean = { false }
    ): List<PermissionStep> {
        val steps = mutableListOf<PermissionStep>()
        steps += PermissionStep(
            PermissionKind.CONTACTS,
            hasContacts,
            !hasContacts && permanentlyDenied(PermissionKind.CONTACTS)
        )
        steps += PermissionStep(
            PermissionKind.PHONE_STATE,
            hasPhoneState,
            !hasPhoneState && permanentlyDenied(PermissionKind.PHONE_STATE)
        )
        if (apiLevel >= 33) {
            steps += PermissionStep(
                PermissionKind.NOTIFICATIONS,
                hasNotifications,
                !hasNotifications && permanentlyDenied(PermissionKind.NOTIFICATIONS)
            )
        }
        steps += PermissionStep(
            PermissionKind.OVERLAY,
            canDrawOverlays,
            isPermanentlyDenied = false
        )
        return steps
    }
}