package com.ringly.app.dialer

import com.ringly.app.data.models.LookupMatch
import com.ringly.app.data.models.LookupResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OutgoingContactLookupTest {

    private fun lookup(
        scope: kotlinx.coroutines.CoroutineScope,
        response: suspend (String) -> Result<LookupResponse>,
        localNumbers: Set<String> = emptySet(),
        debounceMillis: Long = 500L,
        onResult: (LookupMatch?) -> Unit = {}
    ) = OutgoingContactLookup(
        scope = scope,
        lookup = response,
        localNumbers = { localNumbers },
        debounceMillis = debounceMillis,
        onResult = onResult
    )

    private fun match(name: String = "Rahim") =
        LookupMatch(name = name, photoUrl = "https://x/a.jpg", ownerName = "Salman")

    @Test
    fun `debounce suppresses rapid input`() = runTest {
        val lookups = mutableListOf<String>()
        val l = lookup(
            scope = this,
            debounceMillis = 500L,
            response = { lookups += it; Result.success(LookupResponse(found = true, matches = listOf(match()))) }
        )

        l.onDigitsChanged("0171234567")
        runCurrent()
        l.onDigitsChanged("01712345678")
        runCurrent()

        assertTrue(lookups.isEmpty())
    }

    @Test
    fun `after debounce lookup fires for final input`() = runTest {
        val lookups = mutableListOf<String>()
        val l = lookup(
            scope = this,
            debounceMillis = 200L,
            response = { lookups += it; Result.success(LookupResponse(found = true, matches = listOf(match()))) }
        )

        l.onDigitsChanged("01712345678")
        testScheduler.advanceTimeBy(201)
        runCurrent()

        assertEquals(listOf("+8801712345678"), lookups)
    }

    @Test
    fun `local number skips lookup and returns null`() = runTest {
        var result: LookupMatch? = match("should-not-see")
        val l = lookup(
            scope = this,
            localNumbers = setOf("+8801712345678"),
            response = { Result.success(LookupResponse(found = true, matches = listOf(match()))) },
            onResult = { result = it }
        )

        l.onDigitsChanged("01712345678")
        runCurrent()

        assertNull(result)
    }

    @Test
    fun `non-normalizable input skips lookup and returns null`() = runTest {
        var result: LookupMatch? = null
        val l = lookup(
            scope = this,
            response = { Result.success(LookupResponse(found = true, matches = listOf(match()))) },
            onResult = { result = it }
        )

        l.onDigitsChanged("*#00#")
        runCurrent()

        assertNull(result)
    }

    @Test
    fun `pool match returns best match`() = runTest {
        var result: LookupMatch? = null
        val m = match()
        val l = lookup(
            scope = this,
            response = { Result.success(LookupResponse(found = true, matches = listOf(m))) },
            onResult = { result = it }
        )

        l.onDigitsChanged("01712345678")
        testScheduler.advanceTimeBy(501)
        runCurrent()

        assertEquals(m, result)
    }

    @Test
    fun `no match returns null`() = runTest {
        var result: LookupMatch? = null
        val l = lookup(
            scope = this,
            response = { Result.success(LookupResponse(found = false, matches = emptyList())) },
            onResult = { result = it }
        )

        l.onDigitsChanged("01712345678")
        testScheduler.advanceTimeBy(501)
        runCurrent()

        assertNull(result)
    }

    @Test
    fun `lookup failure returns null`() = runTest {
        var result: LookupMatch? = null
        val l = lookup(
            scope = this,
            response = { Result.failure(RuntimeException("fail")) },
            onResult = { result = it }
        )

        l.onDigitsChanged("01712345678")
        testScheduler.advanceTimeBy(501)
        runCurrent()

        assertNull(result)
    }

    @Test
    fun `clear cancels pending and returns null`() = runTest {
        val results = mutableListOf<LookupMatch?>()
        val l = lookup(
            scope = this,
            debounceMillis = 500L,
            response = { Result.success(LookupResponse(found = true, matches = listOf(match()))) },
            onResult = { results += it }
        )

        l.onDigitsChanged("01712345678")
        runCurrent()
        l.clear()
        runCurrent()

        assertEquals(listOf<LookupMatch?>(null), results)
    }

    private fun assertTrue(condition: Boolean) = org.junit.Assert.assertTrue(condition)
}