package com.ringly.app.util

object ContrastChecker {

    fun ratio(foregroundHex: String, backgroundHex: String): Double {
        val fg = luminance(foregroundHex)
        val bg = luminance(backgroundHex)
        val lighter = maxOf(fg, bg)
        val darker = minOf(fg, bg)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun luminance(hex: String): Double {
        val rgb = parseHex(hex)
        val r = channel(rgb shr 16 and 0xFF)
        val g = channel(rgb shr 8 and 0xFF)
        val b = channel(rgb and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun parseHex(hex: String): Int {
        val clean = hex.removePrefix("#")
        require(clean.length == 6) { "expected #RRGGBB, got $hex" }
        return clean.toInt(16)
    }

    private fun channel(value: Int): Double {
        val s = value / 255.0
        return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
    }
}