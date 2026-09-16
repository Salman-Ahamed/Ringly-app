package com.ringly.app.sync

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncStatusLabelTest {

    private val zone = ZoneId.systemDefault()

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute, second).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `never synced returns null`() {
        assertNull(SyncStatusLabel.describe(0L, at(2026, 9, 15, 10, 0)))
    }

    @Test
    fun `synced seconds ago returns just now`() {
        assertEquals("just now", SyncStatusLabel.describe(at(2026, 9, 15, 9, 59, 50), at(2026, 9, 15, 10, 0)))
    }

    @Test
    fun `synced five minutes ago returns minutes ago`() {
        assertEquals("5 min ago", SyncStatusLabel.describe(at(2026, 9, 15, 9, 55), at(2026, 9, 15, 10, 0)))
    }

    @Test
    fun `synced earlier today labels today`() {
        val label = SyncStatusLabel.describe(at(2026, 9, 15, 7, 30), at(2026, 9, 15, 18, 0))!!
        assertEquals("today at 07:30", label)
    }

    @Test
    fun `synced previous day labels date and time`() {
        val label = SyncStatusLabel.describe(at(2026, 9, 13, 22, 5), at(2026, 9, 15, 10, 0))!!
        assertEquals("13 Sep 22:05", label)
    }
}