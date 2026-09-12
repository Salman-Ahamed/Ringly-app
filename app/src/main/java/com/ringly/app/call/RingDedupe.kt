package com.ringly.app.call

internal class RingDedupe(
    private val windowMillis: Long = 5_000L,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private var lastNumber: String? = null
    private var lastAtMillis: Long = Long.MIN_VALUE

    @Synchronized
    fun shouldDispatch(number: String): Boolean {
        val now = clock()
        val isDuplicate = number == lastNumber && now - lastAtMillis < windowMillis
        lastNumber = number
        lastAtMillis = now
        return !isDuplicate
    }
}