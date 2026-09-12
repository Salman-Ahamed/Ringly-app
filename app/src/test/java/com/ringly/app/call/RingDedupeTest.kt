package com.ringly.app.call

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RingDedupeTest {

    @Test
    fun `same number within window is suppressed`() {
        val now = longArrayOf(0L)
        val dedupe = RingDedupe(windowMillis = 5_000L, clock = { now[0] })
        assertTrue(dedupe.shouldDispatch("+8801710987654"))
        assertFalse(dedupe.shouldDispatch("+8801710987654"))
    }

    @Test
    fun `same number after window is allowed`() {
        val now = longArrayOf(0L)
        val dedupe = RingDedupe(windowMillis = 5_000L, clock = { now[0] })
        dedupe.shouldDispatch("+8801710987654")
        now[0] = 5_000L
        assertTrue(dedupe.shouldDispatch("+8801710987654"))
    }

    @Test
    fun `different numbers are always allowed`() {
        val now = longArrayOf(0L)
        val dedupe = RingDedupe(windowMillis = 5_000L, clock = { now[0] })
        assertTrue(dedupe.shouldDispatch("+8801710987654"))
        assertTrue(dedupe.shouldDispatch("+88017111234567"))
        assertTrue(dedupe.shouldDispatch("+8801710987654"))
    }
}