package com.ringly.app.dialer

import com.ringly.app.data.models.PoolContact
import com.ringly.app.sync.SyncEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MergedContactListTest {

    private fun localEntry(number: String, name: String, photoUrl: String? = null) =
        SyncEntry(number = number, name = name, photoUrl = photoUrl, photoHash = null, photoPublicId = null, serverContactId = null)

    private fun poolContact(number: String, name: String, ownerName: String, photoUrl: String? = null) =
        PoolContact(number = number, name = name, photoUrl = photoUrl, ownerName = ownerName)

    @Test
    fun `local wins dedupe over pool for same number`() {
        val local = mapOf("+8801711111111" to localEntry("+8801711111111", "Local Name"))
        val pool = listOf(poolContact("+8801711111111", "Pool Name", "Salman"))

        val result = MergedContactList.build(local, pool)

        assertEquals(1, result.size)
        assertEquals("Local Name", result[0].name)
        assertEquals(ContactSource.LOCAL, result[0].source)
        assertNull(result[0].ownerName)
    }

    @Test
    fun `pool-only entries are tagged POOL with ownerName`() {
        val local = emptyMap<String, SyncEntry>()
        val pool = listOf(poolContact("+8801711111111", "Rahim", "Salman"))

        val result = MergedContactList.build(local, pool)

        assertEquals(1, result.size)
        assertEquals(ContactSource.POOL, result[0].source)
        assertEquals("Salman", result[0].ownerName)
    }

    @Test
    fun `local-only entries are tagged LOCAL`() {
        val local = mapOf("+8801711111111" to localEntry("+8801711111111", "Rahim"))
        val pool = emptyList<PoolContact>()

        val result = MergedContactList.build(local, pool)

        assertEquals(1, result.size)
        assertEquals(ContactSource.LOCAL, result[0].source)
    }

    @Test
    fun `sorted by name case-insensitive`() {
        val local = mapOf(
            "+8801710000002" to localEntry("+8801710000002", "Salman"),
            "+8801710000001" to localEntry("+8801710000001", "amina"),
            "+8801710000003" to localEntry("+8801710000003", "Bassel")
        )

        val result = MergedContactList.build(local, emptyList())

        assertEquals("amina", result[0].name)
        assertEquals("Bassel", result[1].name)
        assertEquals("Salman", result[2].name)
    }

    @Test
    fun `filter by name`() {
        val contacts = listOf(
            MergedContact("+8801711111111", "Rahim", null, ContactSource.LOCAL, null),
            MergedContact("+8801712222222", "Karim", null, ContactSource.LOCAL, null)
        )

        val result = MergedContactList.filter(contacts, "Rahim", null)

        assertEquals(1, result.size)
        assertEquals("Rahim", result[0].name)
    }

    @Test
    fun `filter by number digits`() {
        val contacts = listOf(
            MergedContact("+8801711111111", "Rahim", null, ContactSource.LOCAL, null),
            MergedContact("+8801722222222", "Karim", null, ContactSource.LOCAL, null)
        )

        val result = MergedContactList.filter(contacts, "171", null)

        assertEquals(1, result.size)
        assertEquals("+8801711111111", result[0].number)
    }

    @Test
    fun `filter by source LOCAL`() {
        val contacts = listOf(
            MergedContact("+8801711111111", "Rahim", null, ContactSource.LOCAL, null),
            MergedContact("+8801712222222", "Karim", "https://x/a.jpg", ContactSource.POOL, "Salman")
        )

        val result = MergedContactList.filter(contacts, "", ContactSource.LOCAL)

        assertEquals(1, result.size)
        assertEquals("Rahim", result[0].name)
    }

    @Test
    fun `filter by source POOL`() {
        val contacts = listOf(
            MergedContact("+8801711111111", "Rahim", null, ContactSource.LOCAL, null),
            MergedContact("+8801712222222", "Karim", "https://x/a.jpg", ContactSource.POOL, "Salman")
        )

        val result = MergedContactList.filter(contacts, "", ContactSource.POOL)

        assertEquals(1, result.size)
        assertEquals("Karim", result[0].name)
    }

    @Test
    fun `empty inputs produce empty list`() {
        val result = MergedContactList.build(emptyMap(), emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `empty query returns all matching source`() {
        val contacts = listOf(
            MergedContact("+8801711111111", "Rahim", null, ContactSource.LOCAL, null),
            MergedContact("+8801712222222", "Karim", "https://x/a.jpg", ContactSource.POOL, "Salman")
        )

        val result = MergedContactList.filter(contacts, "", null)

        assertEquals(2, result.size)
    }
}