package com.ringly.app.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncSnapshotCodecTest {

    @Test
    fun `round trip preserves entries and timestamp`() {
        val snapshot = SyncSnapshot(
            mapOf(
                "+8801712345678" to SyncEntry(
                    number = "+8801712345678",
                    name = "Rahim",
                    photoHash = "hash-a",
                    photoUrl = "https://cdn/a.jpg",
                    photoPublicId = "ringly/a",
                    serverContactId = "s1"
                ),
                "+8801812345678" to SyncEntry(
                    number = "+8801812345678",
                    name = "Karim",
                    photoHash = null,
                    photoUrl = null,
                    photoPublicId = null,
                    serverContactId = "s2"
                )
            ),
            12345L
        )

        val decoded = SyncSnapshotCodec.deserialize(SyncSnapshotCodec.serialize(snapshot))

        assertEquals(snapshot, decoded)
    }

    @Test
    fun `empty snapshot round trips`() {
        val decoded = SyncSnapshotCodec.deserialize(SyncSnapshotCodec.serialize(SyncSnapshot.EMPTY))

        assertEquals(SyncSnapshot.EMPTY, decoded)
    }

    @Test
    fun `garbage input yields empty snapshot`() {
        val decoded = SyncSnapshotCodec.deserialize("definitely not json")

        assertTrue(decoded.entries.isEmpty())
    }

    @Test
    fun `null input yields empty snapshot`() {
        val decoded = SyncSnapshotCodec.deserialize("null")

        assertEquals(SyncSnapshot.EMPTY, decoded)
    }

    @Test
    fun `entry missing name is dropped`() {
        val raw = """{"entries":{"+8801712345678":{"number":"+8801712345678"},"other":null},"lastSyncAt":7}"""

        val decoded = SyncSnapshotCodec.deserialize(raw)

        assertTrue(decoded.entries.isEmpty())
        assertEquals(7L, decoded.lastSyncAt)
    }
}