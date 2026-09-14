package com.ringly.app.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.concurrent.thread

class IncomingCallHandlerTest {

    @Before
    fun resetHandler() {
        IncomingCallHandler.reset()
        IncomingCallHandler.clock = { System.currentTimeMillis() }
    }

    private fun capture(): MutableList<IncomingCallEvent> {
        val events = mutableListOf<IncomingCallEvent>()
        IncomingCallNotifier.listener = IncomingCallListener { events += it }
        return events
    }

    @Test
    fun `ring local number is normalized and dispatched once`() {
        val events = capture()
        val first = IncomingCallHandler.onRing("01710987654")
        val duplicate = IncomingCallHandler.onRing("01710987654")
        assertEquals("+8801710987654", first)
        assertNull(duplicate)
        assertEquals(listOf(IncomingCallEvent(IncomingCallPhase.RINGING, "+8801710987654")), events)
    }

    @Test
    fun `ring already E164 number is preserved`() {
        assertEquals("+8801710987654", IncomingCallHandler.onRing("+8801710987654"))
    }

    @Test
    fun `ring number with separators is normalized`() {
        assertEquals("+8801710987654", IncomingCallHandler.onRing("01710 987-654"))
    }

    @Test
    fun `different numbers within window both dispatch`() {
        val events = capture()
        IncomingCallHandler.onRing("01711234567")
        IncomingCallHandler.onRing("01719876543")
        assertEquals(2, events.count { it.phase == IncomingCallPhase.RINGING })
    }

    @Test
    fun `null empty and blank rings are ignored`() {
        val events = capture()
        assertNull(IncomingCallHandler.onRing(null))
        assertNull(IncomingCallHandler.onRing(""))
        assertNull(IncomingCallHandler.onRing("   "))
        assertEquals(0, events.size)
    }

    @Test
    fun `garbage and too short rings are ignored`() {
        val events = capture()
        assertNull(IncomingCallHandler.onRing("abc-def!!"))
        assertNull(IncomingCallHandler.onRing("12345"))
        assertEquals(0, events.size)
    }

    @Test
    fun `state changes without a ring are ignored`() {
        val events = capture()
        IncomingCallHandler.onStateChange(IncomingCallPhase.ACTIVE, null)
        IncomingCallHandler.onStateChange(IncomingCallPhase.DISCONNECTED, null)
        assertEquals(0, events.size)
    }

    @Test
    fun `ring then state changes dispatch in order`() {
        val events = capture()
        IncomingCallHandler.onRing("01710987654")
        IncomingCallHandler.onStateChange(IncomingCallPhase.ACTIVE, null)
        IncomingCallHandler.onStateChange(IncomingCallPhase.DISCONNECTED, null)
        assertEquals(
            listOf(
                IncomingCallEvent(IncomingCallPhase.RINGING, "+8801710987654"),
                IncomingCallEvent(IncomingCallPhase.ACTIVE, null),
                IncomingCallEvent(IncomingCallPhase.DISCONNECTED, null)
            ),
            events
        )
    }

    @Test
    fun `state change with number is normalized when session active`() {
        val events = capture()
        IncomingCallHandler.onRing("01710987654")
        IncomingCallHandler.onStateChange(IncomingCallPhase.ACTIVE, "01710987654")
        assertEquals(
            IncomingCallEvent(IncomingCallPhase.ACTIVE, "+8801710987654"),
            events.last()
        )
    }

    @Test
    fun `state change with mismatched number is ignored`() {
        val events = capture()
        IncomingCallHandler.onRing("01710987654")
        IncomingCallHandler.onStateChange(IncomingCallPhase.DISCONNECTED, "+88017114222222")
        assertEquals(1, events.size)
    }

    @Test
    fun `disconnected closes the session and suppresses later state ticks`() {
        val events = capture()
        IncomingCallHandler.onRing("01710987654")
        IncomingCallHandler.onStateChange(IncomingCallPhase.DISCONNECTED, null)
        IncomingCallHandler.onStateChange(IncomingCallPhase.DISCONNECTED, null)
        assertEquals(2, events.size)
    }

    @Test
    fun `stale session suppresses state change`() {
        val now = longArrayOf(0L)
        IncomingCallHandler.clock = { now[0] }
        val events = capture()
        IncomingCallHandler.onRing("01710987654")
        now[0] = 10 * 60 * 1000 + 1
        IncomingCallHandler.onStateChange(IncomingCallPhase.DISCONNECTED, null)
        assertEquals(1, events.size)
    }

    @Test(timeout = 10_000)
    fun `concurrent same-number rings dispatch exactly once`() {
        val events = capture()
        val threads = (1..200).map {
            thread { IncomingCallHandler.onRing("01710987654") }
        }
        threads.forEach { it.join() }
        assertEquals(1, events.count { it.phase == IncomingCallPhase.RINGING })
        assertTrue(events.none { it.phase != IncomingCallPhase.RINGING })
    }

    @Test
    fun `redial within dedupe window after disconnect dispatches`() {
        val events = capture()
        IncomingCallHandler.onRing("01710987654")
        IncomingCallHandler.onStateChange(IncomingCallPhase.DISCONNECTED, null)
        IncomingCallHandler.onRing("01710987654")
        assertEquals(2, events.count { it.phase == IncomingCallPhase.RINGING })
    }

    @Test
    fun `listener exception does not break dispatch`() {
        IncomingCallNotifier.listener = IncomingCallListener { throw IllegalStateException("boom") }
        IncomingCallHandler.onRing("01710987654")
        IncomingCallHandler.onRing("01711234567")
        IncomingCallHandler.onStateChange(IncomingCallPhase.DISCONNECTED, null)
    }

    @Test
    fun `same number ring after dedupe window with stale session dispatches as new call`() {
        var now = 0L
        IncomingCallHandler.ringDedupe = RingDedupe(clock = { now })
        val events = capture()
        IncomingCallHandler.onRing("01710987654")
        now = 10_000
        IncomingCallHandler.onRing("01710987654")
        assertEquals(2, events.count { it.phase == IncomingCallPhase.RINGING })
    }

    @Test
    fun `ringing source is surfaced on dispatch`() {
        val events = capture()
        IncomingCallHandler.onRing("01710987654", "SCREENING")
        assertEquals(IncomingCallEvent(IncomingCallPhase.RINGING, "+8801710987654"), events.single())
    }
}