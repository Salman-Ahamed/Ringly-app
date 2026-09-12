package com.ringly.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberNormalizerTest {

    @Test
    fun `local format with leading zero`() {
        assertEquals("+8801712345678", NumberNormalizer.normalize("01712345678"))
    }

    @Test
    fun `full E164 with plus`() {
        assertEquals("+8801712345678", NumberNormalizer.normalize("+8801712345678"))
    }

    @Test
    fun `880 prefix without plus`() {
        assertEquals("+8801712345678", NumberNormalizer.normalize("8801712345678"))
    }

    @Test
    fun `strips separators`() {
        assertEquals("+8801712345678", NumberNormalizer.normalize("+880 1712-3456-78"))
        assertEquals("+8801712345678", NumberNormalizer.normalize("01712 345-678"))
    }

    @Test
    fun `plain 10 digit number gets country code`() {
        assertEquals("+8801712345678", NumberNormalizer.normalize("1712345678"))
    }

    @Test
    fun `foreign number stays as is`() {
        assertEquals("+15551234567", NumberNormalizer.normalize("+1 (555) 123-4567"))
    }

    @Test
    fun `too short plain number is invalid`() {
        assertNull(NumberNormalizer.normalize("123456"))
    }

    @Test
    fun `six digit number treated as invalid`() {
        assertNull(NumberNormalizer.normalize("123456"))
    }

    @Test
    fun `empty and blank inputs are invalid`() {
        assertNull(NumberNormalizer.normalize(""))
        assertNull(NumberNormalizer.normalize("   "))
        assertNull(NumberNormalizer.normalize(null))
    }

    @Test
    fun `non digit input is invalid`() {
        assertNull(NumberNormalizer.normalize("abc"))
        assertNull(NumberNormalizer.normalize("++8801712345678"))
        assertNull(NumberNormalizer.normalize("0"))
    }

    @Test
    fun `more than 15 digits is invalid`() {
        assertNull(NumberNormalizer.normalize("+88017123456789012"))
    }

    @Test
    fun `exactly 15 digits is accepted`() {
        assertEquals("+880171234567890", NumberNormalizer.normalize("+880171234567890"))
    }
}