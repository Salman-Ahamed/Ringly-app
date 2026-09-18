package com.ringly.app.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InCallUiMapperTest {

    @Test
    fun `ringing shows answer and decline only`() {
        val s = InCallUiMapper.screen(InCallPhase.RINGING, number = "+8801710000001", title = "Incoming call")

        assertTrue(s.showAnswer)
        assertTrue(s.showDecline)
        assertFalse(s.showEnd)
        assertFalse(s.showMute)
        assertFalse(s.showSpeaker)
    }

    @Test
    fun `dialing shows end only`() {
        val s = InCallUiMapper.screen(InCallPhase.DIALING, number = "+8801710000001", title = "Calling")

        assertFalse(s.showAnswer)
        assertFalse(s.showDecline)
        assertTrue(s.showEnd)
        assertFalse(s.showMute)
        assertFalse(s.showSpeaker)
    }

    @Test
    fun `active shows end mute speaker`() {
        val s = InCallUiMapper.screen(InCallPhase.ACTIVE, number = "+8801710000001", title = "In call")

        assertFalse(s.showAnswer)
        assertFalse(s.showDecline)
        assertTrue(s.showEnd)
        assertTrue(s.showMute)
        assertTrue(s.showSpeaker)
    }

    @Test
    fun `ended shows nothing actionable`() {
        val s = InCallUiMapper.screen(InCallPhase.ENDED, number = "+8801710000001", title = "Call ended")

        assertFalse(s.showAnswer)
        assertFalse(s.showDecline)
        assertFalse(s.showEnd)
        assertFalse(s.showMute)
        assertFalse(s.showSpeaker)
    }

    @Test
    fun `blank caller name falls back to number`() {
        val s = InCallUiMapper.screen(InCallPhase.RINGING, number = "+8801710000001", title = "Incoming call", callerName = "")

        assertEquals("+8801710000001", s.callerName)
    }

    @Test
    fun `named caller kept`() {
        val s = InCallUiMapper.screen(InCallPhase.ACTIVE, number = "+8801710000001", title = "In call", callerName = "Vaiya", sourceLabel = "Your contacts")

        assertEquals("Vaiya", s.callerName)
        assertEquals("Your contacts", s.sourceLabel)
        assertEquals("+8801710000001", s.number)
    }
}