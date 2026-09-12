package com.ringly.app.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class ContactReaderMappingTest {

    private fun row(contactId: String, name: String, number: String) =
        RawContactRow(contactId, name, number)

    @Test
    fun `local numbers are normalized to e164`() {
        val rows = listOf(
            row("1", "Rahim", "01712345678"),
            row("2", "Karim", "+8801812345678"),
            row("3", "Rafiq", "01812345678")
        )

        val candidates = DefaultContactReader.mapCandidates(rows) { null }

        assertEquals(
            listOf("+8801712345678", "+8801812345678", "+8801812345678"),
            candidates.map { it.number }
        )
    }

    @Test
    fun `contacts with invalid numbers are skipped`() {
        val rows = listOf(
            row("1", "Rahim", "12"),
            row("2", "Karim", "not-a-number"),
            row("3", "Rafiq", "+8801712345678")
        )

        val candidates = DefaultContactReader.mapCandidates(rows) { null }

        assertEquals(listOf("+8801712345678"), candidates.map { it.number })
    }

    @Test
    fun `blank name falls back to number`() {
        val candidates = DefaultContactReader.mapCandidates(listOf(row("1", "   ", "01712345678"))) { null }

        assertEquals("+8801712345678", candidates.single().name)
    }

    @Test
    fun `name is capped at 255 characters`() {
        val longName = "X".repeat(300)

        val candidates = DefaultContactReader.mapCandidates(listOf(row("1", longName, "+8801712345678"))) { null }

        assertEquals(255, candidates.single().name.length)
    }

    @Test
    fun `photo hash is resolved per contact`() {
        val called = mutableListOf<String>()

        val candidates = DefaultContactReader.mapCandidates(listOf(row("1", "Rahim", "01712345678"))) { id ->
            called.add(id)
            "hash-$id"
        }

        assertEquals("hash-1", candidates.single().photoHash)
        assertEquals(listOf("1"), called)
    }

    @Test
    fun `multiple numbers keep the contact photo hash`() {
        val rows = listOf(
            row("1", "Rahim", "01712345678"),
            row("1", "Rahim", "01812345678")
        )

        val candidates = DefaultContactReader.mapCandidates(rows) { "photo-1" }

        assertEquals(listOf("photo-1", "photo-1"), candidates.map { it.photoHash })
    }

    @Test
    fun `photo hash returns null when no photo`() {
        val candidates = DefaultContactReader.mapCandidates(listOf(row("1", "Rahim", "01712345678"))) { null }

        assertEquals(null, candidates.single().photoHash)
    }
}