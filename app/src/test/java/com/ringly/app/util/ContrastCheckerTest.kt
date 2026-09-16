package com.ringly.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastCheckerTest {

    private val white = "#FFFFFF"

    @Test
    fun `black on white meets AA and AAA`() {
        assertTrue(ContrastChecker.ratio("#000000", white) >= 4.5)
        assertTrue(ContrastChecker.ratio("#000000", white) >= 7.0)
    }

    @Test
    fun `granted green on white meets AA`() {
        assertTrue("granted_green contrast", ContrastChecker.ratio("#2E7D32", white) >= 4.5)
    }

    @Test
    fun `denied text amber on white meets AA`() {
        val ratio = ContrastChecker.ratio("#8A5A00", white)
        assertTrue("denied_text contrast was $ratio", ratio >= 4.5)
    }

    @Test
    fun `caption gray token on white meets AA`() {
        val ratio = ContrastChecker.ratio("#616161", white)
        assertTrue("text_caption_gray contrast was $ratio", ratio >= 4.5)
    }

    @Test
    fun `bright warning amber on white fails AA`() {
        val ratio = ContrastChecker.ratio("#F9A825", white)
        assertTrue("warning_amber should stay accent-only ($ratio)", ratio < 4.5)
    }

    @Test
    fun `same color has ratio one`() {
        assertEquals(1.0, ContrastChecker.ratio("#1565C0", "#1565C0"), 0.001)
    }
}