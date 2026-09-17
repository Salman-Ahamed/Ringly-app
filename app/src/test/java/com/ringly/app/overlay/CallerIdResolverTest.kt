package com.ringly.app.overlay

import com.ringly.app.data.models.LookupMatch
import com.ringly.app.sync.SyncEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class CallerIdResolverTest {

    private val number = "+8801710000001"

    @Test
    fun `own phone entry wins with its name and photo`() {
        val own = SyncEntry(number, "Vaiya", photoUrl = "https://example.com/v.jpg")
        val pool = LookupMatch("Rahim", photoUrl = "https://example.com/a.jpg", ownerName = "Salman")

        val r = CallerIdResolver.resolve(number, own, pool)

        assertEquals(number, r.number)
        assertEquals("Vaiya", r.name)
        assertEquals("https://example.com/v.jpg", r.photoUrl)
        assertEquals(CallerIdSource.OWN_PHONE, r.source)
        assertEquals(null, r.ownerName)
    }

    @Test
    fun `pool match used when not own`() {
        val pool = LookupMatch("Karim Ali", photoUrl = null, ownerName = "Salman")

        val r = CallerIdResolver.resolve(number, null, pool)

        assertEquals("Karim Ali", r.name)
        assertEquals(CallerIdSource.POOL, r.source)
        assertEquals("Salman", r.ownerName)
        assertEquals(number, r.number)
    }

    @Test
    fun `unknown when no own entry and no pool match`() {
        val r = CallerIdResolver.resolve(number, null, null)

        assertEquals(number, r.name)
        assertEquals(CallerIdSource.UNKNOWN, r.source)
        assertEquals(null, r.ownerName)
        assertEquals(null, r.photoUrl)
    }

    @Test
    fun `blank own name falls back to number`() {
        val own = SyncEntry(number, "")
        val r = CallerIdResolver.resolve(number, own, null)

        assertEquals(number, r.name)
        assertEquals(CallerIdSource.OWN_PHONE, r.source)
    }

    @Test
    fun `blank pool name falls back to number`() {
        val pool = LookupMatch("", photoUrl = null, ownerName = "Salman")
        val r = CallerIdResolver.resolve(number, null, pool)

        assertEquals(number, r.name)
        assertEquals(CallerIdSource.POOL, r.source)
    }
}