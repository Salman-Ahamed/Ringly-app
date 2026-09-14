package com.ringly.app.overlay

import com.ringly.app.data.models.LookupMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BestMatchSelectorTest {

    @Test
    fun `empty list returns null`() {
        assertNull(BestMatchSelector.selectBest(emptyList()))
    }

    @Test
    fun `single match with photo is returned`() {
        val match = LookupMatch(name = "Rahim", photoUrl = "https://example.com/rahim.jpg", ownerName = "Salman")
        assertEquals(match, BestMatchSelector.selectBest(listOf(match)))
    }

    @Test
    fun `single match without photo is returned`() {
        val match = LookupMatch(name = "Rahim", photoUrl = null, ownerName = "Salman")
        assertEquals(match, BestMatchSelector.selectBest(listOf(match)))
    }

    @Test
    fun `match with photo preferred over match without photo`() {
        val withPhoto = LookupMatch(name = "Rahim", photoUrl = "https://example.com/rahim.jpg", ownerName = "Salman")
        val withoutPhoto = LookupMatch(name = "Rahim Uddin", photoUrl = null, ownerName = "Karim")
        assertEquals(withPhoto, BestMatchSelector.selectBest(listOf(withoutPhoto, withPhoto)))
    }

    @Test
    fun `first photo match is chosen when multiple have photos`() {
        val first = LookupMatch(name = "Rahim", photoUrl = "https://example.com/a.jpg", ownerName = "Salman")
        val second = LookupMatch(name = "Rahim", photoUrl = "https://example.com/b.jpg", ownerName = "Karim")
        assertEquals(first, BestMatchSelector.selectBest(listOf(first, second)))
    }

    @Test
    fun `first non-photo match is chosen when no photos exist`() {
        val first = LookupMatch(name = "Rahim", photoUrl = null, ownerName = "Salman")
        val second = LookupMatch(name = "Rahim Uddin", photoUrl = null, ownerName = "Karim")
        assertEquals(first, BestMatchSelector.selectBest(listOf(first, second)))
    }
}
