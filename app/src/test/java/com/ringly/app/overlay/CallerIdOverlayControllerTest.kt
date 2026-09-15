package com.ringly.app.overlay

import com.ringly.app.call.IncomingCallEvent
import com.ringly.app.call.IncomingCallNotifier
import com.ringly.app.call.IncomingCallPhase
import com.ringly.app.data.models.LookupMatch
import com.ringly.app.data.models.LookupResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
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

    private class FakeFallback : CallerIdFallback {
        val unknownShown = mutableListOf<String>()
        val matchesShown = mutableListOf<LookupMatch>()
        private var active = false
        private var hiddenWhileActive = false

        override fun showUnknown(number: String) {
            unknownShown += number
            active = true
        }

        override fun showMatch(match: LookupMatch, number: String) {
            matchesShown += match
            active = true
        }

        override fun hide() {
            if (active) {
                active = false
                hiddenWhileActive = true
            }
        }

        fun isHiddenAfterShow(): Boolean = hiddenWhileActive
    }

    private fun controller(
        scope: CoroutineScope,
        renderer: OverlayRenderer,
        fallback: CallerIdFallback,
        lookup: suspend (String) -> Result<LookupResponse>,
        localNumbers: () -> Set<String> = { emptySet() },
        canShowOverlay: () -> Boolean = { true },
        overlayTimeoutMillis: Long = 60_000L,
        log: (String) -> Unit = {}
    ) = CallerIdOverlayController(
        scope = scope,
        lookup = lookup,
        localNumbers = localNumbers,
        canShowOverlay = canShowOverlay,
        renderer = renderer,
        fallback = fallback,
        overlayTimeoutMillis = overlayTimeoutMillis,
        log = log
    )

    @Test
    fun `ring with overlay permission denied still looks up and notifies match`() = runTest {
        val lookupCalls = mutableListOf<String>()
        val renderer = FakeRenderer()
        val fallback = FakeFallback()
        val c = controller(
            scope = this,
            renderer = renderer,
            fallback = fallback,
            lookup = { lookupCalls += it; Result.success(LookupResponse(found = true, matches = listOf(photoMatch()))) },
            canShowOverlay = { false }
        )

        val number = "+8801710000001"
        c.onCallEvent(IncomingCallEvent(IncomingCallPhase.RINGING, number))
        runCurrent()

        assertEquals(listOf(number), lookupCalls)
        assertTrue(renderer.shown.isEmpty())
        assertEquals(listOf(photoMatch()), fallback.matchesShown)
        assertTrue(fallback.unknownShown.isEmpty())
    }

    @Test
    fun `ring with number in local contacts skips lookup`() = runTest {
        val lookupCalls = mutableListOf<String>()
        val renderer = FakeRenderer()
        val fallback = FakeFallback()
        val c = controller(
            scope = this,
            renderer = renderer,
            fallback = fallback,
            lookup = { lookupCalls += it; Result.success(LookupResponse(found = true, matches = listOf(photoMatch()))) },
            localNumbers = { setOf("+8801710000001") }
        )

        c.onCallEvent(IncomingCallEvent(IncomingCallPhase.RINGING, "+8801710000001"))
        runCurrent()

        assertTrue(lookupCalls.isEmpty())
        assertTrue(renderer.shown.isEmpty())
        assertTrue(fallback.unknownShown.isEmpty())
        assertTrue(fallback.matchesShown.isEmpty())
    }

    @Test
    fun `ring not local with pool match shows best photo match via overlay`() = runTest {
        val noPhoto = LookupMatch("Rahim Uddin", photoUrl = null, ownerName = "Karim")
        val withPhoto = LookupMatch("Rahim", photoUrl = "https://example.com/a.jpg", ownerName = "Salman")
        val renderer = FakeRenderer()
        val fallback = FakeFallback()
        val c = controller(
            scope = this,
            renderer = renderer,
            fallback = fallback,
            lookup = { Result.success(LookupResponse(found = true, matches = listOf(noPhoto, withPhoto))) },
            canShowOverlay = { true }
        )

        c.onCallEvent(IncomingCallEvent(IncomingCallPhase.RINGING, "+8801710000001"))
        runCurrent()

        assertEquals(listOf(withPhoto), renderer.shown)
        assertTrue(fallback.matchesShown.isEmpty())
    }

    @Test
    fun `ring not found falls back to unknown notification`() = runTest {
        val renderer = FakeRenderer()
        val fallback = FakeFallback()
        val c = controller(
            scope = this,
            renderer = renderer,
            fallback = fallback,
            lookup = { Result.success(LookupResponse(found = false, matches = emptyList())) }
        )

        c.onCallEvent(IncomingCallEvent(IncomingCallPhase.RINGING, "+8801710000001"))
        runCurrent()

        assertTrue(renderer.shown.isEmpty())
        assertEquals(listOf("+8801710000001"), fallback.unknownShown)
    }

    @Test
    fun `lookup failure falls back to unknown notification`() = runTest {
        val renderer = FakeRenderer()
        val fallback = FakeFallback()
        val c = controller(
            scope = this,
            renderer = renderer,
            fallback = fallback,
            lookup = { Result.failure(RuntimeException("network down")) }
        )

        c.onCallEvent(IncomingCallEvent(IncomingCallPhase.RINGING, "+8801710000001"))
        runCurrent()

        assertTrue(renderer.shown.isEmpty())
        assertEquals(listOf("+8801710000001"), fallback.unknownShown)
    }

    @Test
    fun `disconnect dismisses shown overlay`() = runTest {
        val renderer = FakeRenderer()
        val fallback = FakeFallback()
        val c = controller(
            scope = this,
            renderer = renderer,
            fallback = fallback,
            lookup = { Result.success(LookupResponse(found = true, matches = listOf(photoMatch()))) }
        )

        val number = "+8801710000001"
        c.onCallEvent(IncomingCallEvent(IncomingCallPhase.RINGING, number))
        runCurrent()
        assertEquals(1, renderer.shown.size)

        c.onCallEvent(IncomingCallEvent(IncomingCallPhase.DISCONNECTED, null))
        assertEquals(1, renderer.dismissCount)
    }

    @Test
    fun `active phase dismisses shown overlay`() = runTest {
        val renderer = FakeRenderer()
        val fallback = FakeFallback()
        val c = controller(
            scope = this,
            renderer = renderer,
            fallback = fallback,
            lookup = { Result.success(LookupResponse(found = true, matches = listOf(photoMatch()))) }
        )

        val number = "+8801710000001"
        c.onCallEvent(IncomingCallEvent(IncomingCallPhase.RINGING, number))
        runCurrent()
        assertEquals(1, renderer.shown.size)

        c.onCallEvent(IncomingCallEvent(IncomingCallPhase.ACTIVE, null))
        assertEquals(1, renderer.dismissCount)
    }

    @Test
    fun `start wires notifier listener and stop unwires it`() = runTest {
        val renderer = FakeRenderer()
        val fallback = FakeFallback()
        val c = controller(
            scope = this,
            renderer = renderer,
            fallback = fallback,
            lookup = { Result.success(LookupResponse(found = true, matches = listOf(photoMatch()))) }
        )

        try {
            c.start()
            assertTrue(IncomingCallNotifier.listener === c)

            IncomingCallNotifier.notify(IncomingCallEvent(IncomingCallPhase.RINGING, "+8801710000001"))
            runCurrent()
            assertEquals(1, renderer.shown.size)

            c.stop()
            assertTrue(IncomingCallNotifier.listener == null)

            IncomingCallNotifier.notify(IncomingCallEvent(IncomingCallPhase.RINGING, "+8801710000002"))
            runCurrent()
            assertEquals(1, renderer.shown.size)
        } finally {
            IncomingCallNotifier.listener = null
        }
    }

    @Test
    fun `disconnect while lookup pending cancels show`() = runTest {
        val deferred = CompletableDeferred<Result<LookupResponse>>()
        var lookupStarted = false
        val renderer = FakeRenderer()
        val fallback = FakeFallback()
        val c = controller(
            scope = this,
            renderer = renderer,
            fallback = fallback,
            lookup = {
                lookupStarted = true
                deferred.await()
            }
        )

        val number = "+8801710000001"
        c.onCallEvent(IncomingCallEvent(IncomingCallPhase.RINGING, number))
        runCurrent()
        c.onCallEvent(IncomingCallEvent(IncomingCallPhase.DISCONNECTED, null))
        deferred.complete(Result.success(LookupResponse(found = true, matches = listOf(photoMatch()))))
        runCurrent()

        assertTrue(lookupStarted)
        assertTrue(renderer.shown.isEmpty())
        assertEquals(0, renderer.dismissCount)
        assertTrue(fallback.unknownShown.isEmpty())
        assertTrue(!fallback.isHiddenAfterShow())
    }

    @Test
    fun `new ring while previous lookup pending replaces it`() = runTest {
        val firstDeferred = CompletableDeferred<Result<LookupResponse>>()
        var firstLookupStarted = false
        val secondMatch = LookupMatch("Second", photoUrl = null, ownerName = "Salman")
        val firstLookupCalls = mutableListOf<String>()
        val renderer = FakeRenderer()
        val fallback = FakeFallback()
        val c = controller(
            scope = this,
            renderer = renderer,
            fallback = fallback,
            lookup = { number ->
                if (number == "+8801710000001") {
                    firstLookupCalls += number
                    firstLookupStarted = true
                    firstDeferred.await()
                } else {
                    Result.success(LookupResponse(found = true, matches = listOf(secondMatch)))
                }
            }
        )

        c.onCallEvent(IncomingCallEvent(IncomingCallPhase.RINGING, "+8801710000001"))
        runCurrent()
        c.onCallEvent(IncomingCallEvent(IncomingCallPhase.RINGING, "+8801710000002"))
        runCurrent()

        firstDeferred.complete(Result.success(LookupResponse(found = true, matches = listOf(photoMatch()))))
        runCurrent()

        assertTrue(firstLookupStarted)
        assertEquals(1, firstLookupCalls.size)
        assertEquals(listOf(secondMatch), renderer.shown)
        assertTrue(fallback.matchesShown.isEmpty())
        assertTrue(fallback.unknownShown.isEmpty())
    }

    @Test
    fun `cancelled lookup does not show unknown notification`() = runTest {
        val deferred = CompletableDeferred<Result<LookupResponse>>()
        val renderer = FakeRenderer()
        val fallback = FakeFallback()
        val c = controller(
            scope = this,
            renderer = renderer,
            fallback = fallback,
            lookup = { deferred.await() }
        )

        c.onCallEvent(IncomingCallEvent(IncomingCallPhase.RINGING, "+8801710000001"))
        runCurrent()
        c.onCallEvent(IncomingCallEvent(IncomingCallPhase.DISCONNECTED, null))
        runCurrent()

        // CancellationException re-throws; fallback never invoked
        assertTrue(fallback.unknownShown.isEmpty())
        assertTrue(renderer.shown.isEmpty())
    }

    @Test
    fun `stale overlay auto-dismisses after timeout even without end event`() = runTest {
        val renderer = FakeRenderer()
        val fallback = FakeFallback()
        val c = controller(
            scope = this,
            renderer = renderer,
            fallback = fallback,
            lookup = { Result.success(LookupResponse(found = true, matches = listOf(photoMatch()))) },
            overlayTimeoutMillis = 1_000L
        )

        c.onCallEvent(IncomingCallEvent(IncomingCallPhase.RINGING, "+8801710000001"))
        runCurrent()
        assertEquals(1, renderer.shown.size)

        advanceTimeBy(1_001)
        runCurrent()

        assertEquals(1, renderer.dismissCount)
    }

    @Test
    fun `notification fallback for unknown hides on end`() = runTest {
        val renderer = FakeRenderer()
        val fallback = FakeFallback()
        val c = controller(
            scope = this,
            renderer = renderer,
            fallback = fallback,
            lookup = { Result.success(LookupResponse(found = false, matches = emptyList())) }
        )

        c.onCallEvent(IncomingCallEvent(IncomingCallPhase.RINGING, "+8801710000001"))
        runCurrent()
        assertEquals(listOf("+8801710000001"), fallback.unknownShown)

        c.onCallEvent(IncomingCallEvent(IncomingCallPhase.DISCONNECTED, null))
        assertTrue(fallback.isHiddenAfterShow())
        assertTrue(renderer.shown.isEmpty())
    }

    private fun photoMatch() = LookupMatch("Rahim", photoUrl = "https://example.com/rahim.jpg", ownerName = "Salman")
}
