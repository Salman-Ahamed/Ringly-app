package com.ringly.app.sms

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsRoleTest {

    @Test
    fun `unsupported below android 10`() {
        assertEquals(SmsRole.Status.UNAVAILABLE, SmsRole.status(28, roleHeld = false))
        assertEquals(SmsRole.Status.UNAVAILABLE, SmsRole.status(28, roleHeld = true))
        assertEquals(false, SmsRole.isSupported(28))
    }

    @Test
    fun `android 10 without role needs setup`() {
        assertEquals(SmsRole.Status.NEEDED, SmsRole.status(29, roleHeld = false))
    }

    @Test
    fun `android 10 with role granted`() {
        assertEquals(SmsRole.Status.GRANTED, SmsRole.status(29, roleHeld = true))
        assertEquals(SmsRole.Status.GRANTED, SmsRole.status(36, roleHeld = true))
        assertEquals(true, SmsRole.isSupported(29))
    }

    @Test
    fun `newer versions mirror sms role semantics`() {
        assertEquals(SmsRole.Status.NEEDED, SmsRole.status(35, roleHeld = false))
        assertEquals(SmsRole.Status.GRANTED, SmsRole.status(35, roleHeld = true))
    }
}