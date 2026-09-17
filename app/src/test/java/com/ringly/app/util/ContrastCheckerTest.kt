package com.ringly.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastCheckerTest {

    private val white = "#FFFFFF"
    private val cardDark = "#1D1B20"

    @Test
    fun `black on white meets AA and AAA`() {
        assertTrue(ContrastChecker.ratio("#000000", white) >= 4.5)
        assertTrue(ContrastChecker.ratio("#000000", white) >= 7.0)
    }

    @Test
    fun `dark mode primary text on dark card meets AA`() {
        val ratio = ContrastChecker.ratio("#E8EDF5", cardDark)
        assertTrue("dark text_navy contrast was $ratio", ratio >= 4.5)
    }

    @Test
    fun `dark mode caption gray on dark card meets AA`() {
        val ratio = ContrastChecker.ratio("#B6BCCB", cardDark)
        assertTrue("dark text_caption_gray contrast was $ratio", ratio >= 4.5)
    }

    @Test
    fun `dark mode granted green on dark card meets AA`() {
        val ratio = ContrastChecker.ratio("#81C784", cardDark)
        assertTrue("dark granted_green contrast was $ratio", ratio >= 4.5)
    }

    @Test
    fun `dark mode denied amber on dark card meets AA`() {
        val ratio = ContrastChecker.ratio("#FFB74D", cardDark)
        assertTrue("dark denied_text contrast was $ratio", ratio >= 4.5)
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