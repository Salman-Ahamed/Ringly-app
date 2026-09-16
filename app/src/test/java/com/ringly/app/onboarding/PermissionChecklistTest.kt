package com.ringly.app.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionChecklistTest {

    @Test
    fun `all granted returns every step granted`() {
        val steps = PermissionChecklist.build(
            apiLevel = 35,
            hasContacts = true,
            hasPhoneState = true,
            hasNotifications = true,
            canDrawOverlays = true
        )
        assertEquals(
            listOf(PermissionKind.CONTACTS, PermissionKind.PHONE_STATE, PermissionKind.NOTIFICATIONS, PermissionKind.OVERLAY),
            steps.map { it.kind }
        )
        assertTrue(steps.all { it.isGranted })
    }

    @Test
    fun `none granted returns every step denied`() {
        val steps = PermissionChecklist.build(
            apiLevel = 35,
            hasContacts = false,
            hasPhoneState = false,
            hasNotifications = false,
            canDrawOverlays = false
        )
        assertEquals(4, steps.size)
        assertTrue(steps.none { it.isGranted })
    }

    @Test
    fun `api level below 33 omits notification step`() {
        val steps = PermissionChecklist.build(
            apiLevel = 29,
            hasContacts = false,
            hasPhoneState = false,
            hasNotifications = false,
            canDrawOverlays = false
        )
        assertEquals(
            listOf(PermissionKind.CONTACTS, PermissionKind.PHONE_STATE, PermissionKind.OVERLAY),
            steps.map { it.kind }
        )
    }

    @Test
    fun `api level 33 plus includes notification step`() {
        val steps = PermissionChecklist.build(
            apiLevel = 33,
            hasContacts = false,
            hasPhoneState = false,
            hasNotifications = false,
            canDrawOverlays = false
        )
        assertTrue(steps.any { it.kind == PermissionKind.NOTIFICATIONS })
    }

    @Test
    fun `partial grant reflects only granted permissions`() {
        val steps = PermissionChecklist.build(
            apiLevel = 34,
            hasContacts = true,
            hasPhoneState = false,
            hasNotifications = false,
            canDrawOverlays = true
        ).associateBy { it.kind }
        assertTrue(steps.getValue(PermissionKind.CONTACTS).isGranted)
        assertFalse(steps.getValue(PermissionKind.PHONE_STATE).isGranted)
        assertFalse(steps.getValue(PermissionKind.NOTIFICATIONS).isGranted)
        assertTrue(steps.getValue(PermissionKind.OVERLAY).isGranted)
    }

    @Test
    fun `overlay always uses open settings action`() {
        assertTrue(PermissionKind.OVERLAY.action == PermissionAction.OPEN_SETTINGS)
    }

    @Test
    fun `runtime permissions always use allow runtime action`() {
        listOf(
            PermissionKind.CONTACTS,
            PermissionKind.PHONE_STATE,
            PermissionKind.NOTIFICATIONS
        ).forEach {
            assertEquals(PermissionAction.ALLOW_RUNTIME, it.action)
            assertTrue(it.runtimePermission != null)
        }
    }

    @Test
    fun `step order is contacts phone notifications overlay`() {
        val steps = PermissionChecklist.build(
            apiLevel = 35,
            hasContacts = false,
            hasPhoneState = false,
            hasNotifications = false,
            canDrawOverlays = false
        )
        assertEquals(
            listOf(PermissionKind.CONTACTS, PermissionKind.PHONE_STATE, PermissionKind.NOTIFICATIONS, PermissionKind.OVERLAY),
            steps.map { it.kind }
        )
    }

    @Test
    fun `permanent predicate flags only matching denied steps`() {
        val steps = PermissionChecklist.build(
            apiLevel = 35,
            hasContacts = false,
            hasPhoneState = false,
            hasNotifications = false,
            canDrawOverlays = false,
            permanentlyDenied = { kind -> kind == PermissionKind.CONTACTS || kind == PermissionKind.NOTIFICATIONS }
        ).associateBy { it.kind }
        assertTrue(steps.getValue(PermissionKind.CONTACTS).isPermanentlyDenied)
        assertFalse(steps.getValue(PermissionKind.PHONE_STATE).isPermanentlyDenied)
        assertTrue(steps.getValue(PermissionKind.NOTIFICATIONS).isPermanentlyDenied)
        assertFalse(steps.getValue(PermissionKind.OVERLAY).isPermanentlyDenied)
    }

    @Test
    fun `granted steps are never permanent`() {
        val steps = PermissionChecklist.build(
            apiLevel = 35,
            hasContacts = true,
            hasPhoneState = true,
            hasNotifications = true,
            canDrawOverlays = true,
            permanentlyDenied = { true }
        )
        assertTrue(steps.none { it.isPermanentlyDenied })
    }
}