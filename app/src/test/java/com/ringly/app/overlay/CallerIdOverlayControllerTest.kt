package com.ringly.app.overlay

import com.ringly.app.call.IncomingCallEvent
import com.ringly.app.call.IncomingCallPhase
import com.ringly.app.data.models.LookupMatch
import com.ringly.app.data.models.LookupResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CallerIdOverlayControllerTest {

    private class FakeRenderer : OverlayRenderer {
        val shown = mutableListOf<LookupMatch>()
        var dismissCount = 0
        private var visible = false

        override fun show(match: LookupMatch, number: String) {
            shown += match
            visible = true
        }

        override fun dismiss() {
            if (visible) {
                visible = false
                dismissCount += 1
            }
        }
    }

    @Test
    fun `ring with overlay permission denied skips lookup`() = runTest {
        var lookupCalls = 0
        val renderer = FakeRenderer()
        val controller = CallerIdOverlayController(
            scope = this,
            lookup = { lookupCalls += 1; Result.success(LookupResponse(found = true, matches = listOf(photoMatch()))) },
            localNumbers = { emptySet() },
            canShowOverlay = { false },
            renderer = renderer,
            log = {}
        )

        val number = "+8801710000001"
        controller.onCallEvent(IncomingCallEvent(IncomingCallPhase.RINGING, number))
        advanceUntilIdle()

        assertEquals(0, lookupCalls)
        assertTrue(renderer.shown.isEmpty())
    }

    @Test
    fun `ring with number in local contacts skips lookup`() = runTest {
        var lookupCalls = 0
        val renderer = FakeRenderer()
        val controller = CallerIdOverlayController(
            scope = this,
            lookup = { lookupCalls += 1; Result.success(LookupResponse(found = true, matches = listOf(photoMatch()))) },
            localNumbers = { setOf("+8801710000001") },
            canShowOverlay = { true },
            renderer = renderer,
            log = {}
        )

        val number = "+8801710000001"
        controller.onCallEvent(IncomingCallEvent(IncomingCallPhase.RINGING, number))
        advanceUntilIdle()

        assertEquals(0, lookupCalls)
        assertTrue(renderer.shown.isEmpty())
    }

    @Test
    fun `ring not local with pool match shows best photo match`() = runTest {
        val noPhoto = LookupMatch("Rahim Uddin", photoUrl = null, ownerName = "Karim")
        val withPhoto = LookupMatch("Rahim", photoUrl = "https://example.com/a.jpg", ownerName = "Salman")
        val renderer = FakeRenderer()
        val controller = CallerIdOverlayController(
            scope = this,
            lookup = { Result.success(LookupResponse(found = true, matches = listOf(noPhoto, withPhoto))) },
            localNumbers = { emptySet() },
            canShowOverlay = { true },
            renderer = renderer,
            log = {}
        )

        val number = "+8801710000001"
        controller.onCallEvent(IncomingCallEvent(IncomingCallPhase.RINGING, number))
        advanceUntilIdle()

        assertEquals(listOf(withPhoto), renderer.shown)
    }

    @Test
    fun `ring not found shows nothing`() = runTest {
        val renderer = FakeRenderer()
        val controller = CallerIdOverlayController(
            scope = this,
            lookup = { Result.success(LookupResponse(found = false, matches = emptyList())) },
            localNumbers = { emptySet() },
            canShowOverlay = { true },
            renderer = renderer,
            log = {}
        )

        controller.onCallEvent(IncomingCallEvent(IncomingCallPhase.RINGING, "+8801710000001"))
        advanceUntilIdle()

        assertTrue(renderer.shown.isEmpty())
    }

    @Test
    fun `lookup failure shows nothing`() = runTest {
        val renderer = FakeRenderer()
        val controller = CallerIdOverlayController(
            scope = this,
            lookup = { Result.failure(RuntimeException("network down")) },
            localNumbers = { emptySet() },
            canShowOverlay = { true },
            renderer = renderer,
            log = {}
        )

        controller.onCallEvent(IncomingCallEvent(IncomingCallPhase.RINGING, "+8801710000001"))
        advanceUntilIdle()

        assertTrue(renderer.shown.isEmpty())
    }

    @Test
    fun `disconnect dismisses shown overlay`() = runTest {
        val renderer = FakeRenderer()
        val controller = CallerIdOverlayController(
            scope = this,
            lookup = { Result.success(LookupResponse(found = true, matches = listOf(photoMatch()))) },
            localNumbers = { emptySet() },
            canShowOverlay = { true },
            renderer = renderer,
            log = {}
        )

        val number = "+8801710000001"
        controller.onCallEvent(IncomingCallEvent(IncomingCallPhase.RINGING, number))
        advanceUntilIdle()
        assertEquals(1, renderer.shown.size)

        controller.onCallEvent(IncomingCallEvent(IncomingCallPhase.DISCONNECTED, null))
        assertEquals(1, renderer.dismissCount)
    }

    @Test
    fun `disconnect while lookup pending cancels show`() = runTest {
        val deferred = CompletableDeferred<Result<LookupResponse>>()
        var lookupStarted = false
        val renderer = FakeRenderer()
        val controller = CallerIdOverlayController(
            scope = this,
            lookup = {
                lookupStarted = true
                deferred.await()
            },
            localNumbers = { emptySet() },
            canShowOverlay = { true },
            renderer = renderer,
            log = {}
        )

        val number = "+8801710000001"
        controller.onCallEvent(IncomingCallEvent(IncomingCallPhase.RINGING, number))
        runCurrent()
        controller.onCallEvent(IncomingCallEvent(IncomingCallPhase.DISCONNECTED, null))
        deferred.complete(Result.success(LookupResponse(found = true, matches = listOf(photoMatch()))))
        advanceUntilIdle()

        assertTrue(lookupStarted)
        assertTrue(renderer.shown.isEmpty())
        assertEquals(0, renderer.dismissCount)
    }

    @Test
    fun `new ring while previous lookup pending replaces it`() = runTest {
        val firstDeferred = CompletableDeferred<Result<LookupResponse>>()
        var firstLookupStarted = false
        val secondMatch = LookupMatch("Second", photoUrl = null, ownerName = "Salman")
        var firstLookupCalls = 0
        val renderer = FakeRenderer()
        val controller = CallerIdOverlayController(
            scope = this,
            lookup = { number ->
                if (number == "+8801710000001") {
                    firstLookupCalls += 1
                    firstLookupStarted = true
                    firstDeferred.await()
                } else {
                    Result.success(LookupResponse(found = true, matches = listOf(secondMatch)))
                }
            },
            localNumbers = { emptySet() },
            canShowOverlay = { true },
            renderer = renderer,
            log = {}
        )

        controller.onCallEvent(IncomingCallEvent(IncomingCallPhase.RINGING, "+8801710000001"))
        runCurrent()
        controller.onCallEvent(IncomingCallEvent(IncomingCallPhase.RINGING, "+8801710000002"))
        advanceUntilIdle()

        firstDeferred.complete(Result.success(LookupResponse(found = true, matches = listOf(photoMatch()))))
        advanceUntilIdle()

        assertTrue(firstLookupStarted)
        assertEquals(1, firstLookupCalls)
        assertEquals(listOf(secondMatch), renderer.shown)
    }

    private fun photoMatch() = LookupMatch("Rahim", photoUrl = "https://example.com/rahim.jpg", ownerName = "Salman")
}