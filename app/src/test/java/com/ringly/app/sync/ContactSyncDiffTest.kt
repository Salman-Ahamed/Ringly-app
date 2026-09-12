package com.ringly.app.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactSyncDiffTest {

    private fun candidate(
        number: String,
        name: String = "Name",
        photoHash: String? = null
    ) = ContactReadCandidate(number = number, contactId = "c-$number", name = name, photoHash = photoHash)

    private fun entry(
        number: String,
        name: String = "Name",
        photoHash: String? = null,
        serverId: String? = null
    ) = SyncEntry(number, name, photoHash, serverContactId = serverId)

    private fun snapshot(vararg entries: Pair<String, SyncEntry>) = SyncSnapshot(entries.toMap(), 1L)

    @Test
    fun `new contact without photo is synced without upload`() {
        val plan = ContactSyncDiff.computeDiff(
            listOf(candidate("+8801712345678", "Rahim")),
            SyncSnapshot.EMPTY
        )

        assertTrue(plan.uploads.isEmpty())
        assertEquals(listOf("+8801712345678"), plan.sync.map { it.number })
        assertFalse(plan.sync.single().needsPhotoUpload)
        assertTrue(plan.deletes.isEmpty())
    }

    @Test
    fun `unchanged contacts produce no changes`() {
        val snap = snapshot("+8801712345678" to entry("+8801712345678", "Rahim", "h1", "s1"))

        val plan = ContactSyncDiff.computeDiff(
            listOf(candidate("+8801712345678", "Rahim", "h1")),
            snap
        )

        assertTrue(plan.uploads.isEmpty())
        assertTrue(plan.sync.isEmpty())
        assertTrue(plan.deletes.isEmpty())
    }

    @Test
    fun `name change syncs without reupload`() {
        val snap = snapshot("+8801712345678" to entry("+8801712345678", "Rahim", "h1", "s1"))

        val plan = ContactSyncDiff.computeDiff(
            listOf(candidate("+8801712345678", "Rahim Uddin", "h1")),
            snap
        )

        assertTrue(plan.uploads.isEmpty())
        assertEquals(1, plan.sync.size)
        assertFalse(plan.sync.single().needsPhotoUpload)
    }

    @Test
    fun `photo change triggers upload and sync`() {
        val snap = snapshot("+8801712345678" to entry("+8801712345678", "Rahim", "old-hash", "s1"))

        val plan = ContactSyncDiff.computeDiff(
            listOf(candidate("+8801712345678", "Rahim", "new-hash")),
            snap
        )

        assertEquals(listOf("+8801712345678"), plan.uploads.map { it.number })
        assertTrue(plan.sync.single().needsPhotoUpload)
    }

    @Test
    fun `new contact with photo triggers upload and sync`() {
        val plan = ContactSyncDiff.computeDiff(
            listOf(candidate("+8801812345678", "Karim", "h1")),
            SyncSnapshot.EMPTY
        )

        assertEquals(listOf("+8801812345678"), plan.uploads.map { it.number })
        assertTrue(plan.sync.single().needsPhotoUpload)
    }

    @Test
    fun `removed contact is queued for deletion`() {
        val snap = snapshot(
            "+8801712345678" to entry("+8801712345678", "Rahim", "h1", "s1"),
            "+8801812345678" to entry("+8801812345678", "Karim", "h2", "s2")
        )

        val plan = ContactSyncDiff.computeDiff(
            listOf(candidate("+8801712345678", "Rahim", "h1")),
            snap
        )

        assertEquals(listOf("s2"), plan.deletes)
        assertFalse(plan.sync.any { it.number == "+8801812345678" })
    }

    @Test
    fun `duplicate numbers in device list are deduplicated`() {
        val device = listOf(
            candidate("+8801712345678", "Rahim", "h1"),
            candidate("+8801712345678", "Rahim HQ", "h2")
        )

        val plan = ContactSyncDiff.computeDiff(device, SyncSnapshot.EMPTY)

        assertEquals(1, plan.sync.size)
        assertEquals("Rahim", plan.sync.single().name)
        assertEquals(listOf("+8801712345678"), plan.uploads.map { it.number })
    }

    @Test
    fun `contact without server id is not queued for deletion`() {
        val snap = snapshot("+8801712345678" to entry("+8801712345678", "Rahim", "h1", null))

        val plan = ContactSyncDiff.computeDiff(emptyList(), snap)

        assertTrue(plan.deletes.isEmpty())
    }
}