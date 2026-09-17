package com.ringly.app.call

import org.junit.Assert.assertEquals
import org.junit.Test

class CallScreeningRoleTest {

    @Test
    fun isSupported_boundary() {
        assertEquals(false, CallScreeningRole.isSupported(26))
        assertEquals(false, CallScreeningRole.isSupported(28))
        assertEquals(true, CallScreeningRole.isSupported(29))
        assertEquals(true, CallScreeningRole.isSupported(30))
        assertEquals(true, CallScreeningRole.isSupported(36))
    }

    @Test
    fun status_unavailable_on_old_apis_regardless_of_held() {
        assertEquals(CallScreeningRole.Status.UNAVAILABLE, CallScreeningRole.status(28, false))
        assertEquals(CallScreeningRole.Status.UNAVAILABLE, CallScreeningRole.status(28, true))
    }

    @Test
    fun status_needed_when_role_not_held_on_supported_api() {
        assertEquals(CallScreeningRole.Status.NEEDED, CallScreeningRole.status(29, false))
        assertEquals(CallScreeningRole.Status.NEEDED, CallScreeningRole.status(36, false))
    }

    @Test
    fun status_granted_when_role_held_on_supported_api() {
        assertEquals(CallScreeningRole.Status.GRANTED, CallScreeningRole.status(29, true))
        assertEquals(CallScreeningRole.Status.GRANTED, CallScreeningRole.status(36, true))
    }
}