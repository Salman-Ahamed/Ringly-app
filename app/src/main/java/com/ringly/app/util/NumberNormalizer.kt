package com.ringly.app.util

object NumberNormalizer {

    private const val DEFAULT_COUNTRY_CODE = "+880"
    private val E164_REGEX = Regex("^\\+[1-9]\\d{1,14}$")

    fun normalize(raw: String?): String? {
        if (raw.isNullOrEmpty()) return null

        val cleaned = raw.filter { it.isDigit() || it == '+' }
        if (cleaned.isEmpty()) return null

        if (cleaned.startsWith("+")) {
            return if (E164_REGEX.matches(cleaned)) cleaned else null
        }

        if (cleaned.startsWith("880")) {
            val withPrefix = "+$cleaned"
            return if (E164_REGEX.matches(withPrefix)) withPrefix else null
        }

        if (cleaned.startsWith("0")) {
            val rest = cleaned.drop(1)
            return if (rest.length in 1..14 && rest.all { it.isDigit() }) {
                DEFAULT_COUNTRY_CODE + rest
            } else {
                null
            }
        }

        return if (cleaned.length >= 7) DEFAULT_COUNTRY_CODE + cleaned else null
    }
}