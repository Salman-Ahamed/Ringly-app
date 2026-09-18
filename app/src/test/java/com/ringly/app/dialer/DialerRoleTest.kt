package com.ringly.app.dialer

import org.junit.Assert.assertEquals
import org.junit.Test

class DialerRoleTest {

    @Test
    fun `unsupported below android q`() {
        assertEquals(DialerRole.Status.UNAVAILABLE, DialerRole.status(28, roleHeld = false))
        assertEquals(DialerRole.Status.UNAVAILABLE, DialerRole.status(28, roleHeld = true))
        assertEquals(false, DialerRole.isSupported(28))
    }

    @Test
    fun `android q without role needs setup`() {
        assertEquals(DialerRole.Status.NEEDED, DialerRole.status(29, roleHeld = false))
    }

    @Test
    fun `android q with role granted`() {
        assertEquals(DialerRole.Status.GRANTED, DialerRole.status(29, roleHeld = true))
        assertEquals(DialerRole.Status.GRANTED, DialerRole.status(35, roleHeld = true))
        assertEquals(true, DialerRole.isSupported(29))
    }

    @Test
    fun `newer versions mirror call screening semantics`() {
        assertEquals(DialerRole.Status.NEEDED, DialerRole.status(34, roleHeld = false))
        assertEquals(DialerRole.Status.GRANTED, DialerRole.status(34, roleHeld = true))
    }
}