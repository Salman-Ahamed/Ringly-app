package com.ringly.app.overlay

import com.ringly.app.call.IncomingCallEvent
import com.ringly.app.call.IncomingCallNotifier
import com.ringly.app.call.IncomingCallPhase
import com.ringly.app.data.models.LookupMatch
import com.ringly.app.data.models.LookupResponse
import com.ringly.app.sync.SyncEntry
import com.ringly.app.sync.SyncSnapshot
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
        val shown = mutableListOf<CallerIdCard>()
        var dismissCount = 0
        private var visible = false

        override fun show(card: CallerIdCard) {
            shown += card
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
        val shown = mutableListOf<CallerIdCard>()
        private var active = false
        private var hiddenWhileActive = false

        override fun show(card: CallerIdCard) {
            shown += card
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
        ownContacts: () -> SyncSnapshot = { SyncSnapshot.EMPTY },
        canShowOverlay: () -> Boolean = { true },
        overlayTimeoutMillis: Long = 60_000L,
        isPresentationManagedElsewhere: () -> Boolean = { false },
        log: (String) -> Unit = {}
    ) = CallerIdOverlayController(
        scope = scope,
        lookup = lookup,
        ownContacts = ownContacts,
        canShowOverlay = canShowOverlay,
        sourceLabel = { source, owner -> "$source:${owner ?: "null"}" },
        renderer = renderer,
        fallback = fallback,
        overlayTimeoutMillis = overlayTimeoutMillis,
        isPresentationManagedElsewhere = isPresentationManagedElsewhere,
        log = log
    )

    @Test
    fun `own contact shows card with own label without network lookup`() = runTest {
        val lookupCalls = mutableListOf<String>()
        val renderer = FakeRenderer()
        val fallback = FakeFallback()
        val ownEntry = SyncEntry("+8801710000001", "Vaiya", photoUrl = "https://example.com/v.jpg")
        val c = controller(
            scope = this,
            renderer = renderer,
            fallback = fallback,
            lookup = { lookupCalls += it; Result.success(LookupResponse(found = true, matches = listOf(photoMatch()))) },
            ownContacts = { SyncSnapshot(entries = mapOf("+8801710000001" to ownEntry)) }
        )

        c.onCallEvent(IncomingCallEvent(IncomingCallPhase.RINGING, "+8801710000001"))
        runCurrent()

        assertTrue(lookupCalls.isEmpty())
        assertEquals(1, renderer.shown.size)
        val card = renderer.shown.first()
        assertEquals("Vaiya", card.name)
        assertEquals("OWN_PHONE:null", card.sourceLabel)
        assertEquals("+8801710000001", card.number)
        assertEquals("https://example.com/v.jpg", card.photoUrl)
        assertTrue(fallback.shown.isEmpty())
    }

    @Test
    fun `own entry wins over pool when number in both`() = runTest {
        val lookupCalls = mutableListOf<String>()
        val renderer = FakeRenderer()
        val fallback = FakeFallback()
        val ownEntry = SyncEntry("+8801710000001", "Vaiya", photoUrl = null)
        val c = controller(
            scope = this,
            renderer = renderer,
            fallback = fallback,
            lookup = { lookupCalls += it; Result.success(LookupResponse(found = true, matches = listOf(photoMatch()))) },
            ownContacts = { SyncSnapshot(entries = mapOf("+8801710000001" to ownEntry)) }
        )

        c.onCallEvent(IncomingCallEvent(IncomingCallPhase.RINGING, "+8801710000001"))
        runCurrent()

        assertTrue(lookupCalls.isEmpty())
        assertEquals(1, renderer.shown.size)
        assertEquals("Vaiya", renderer.shown.first().name)
        assertEquals("OWN_PHONE:null", renderer.shown.first().sourceLabel)
    }

    @Test
    fun `overlay permission denied still resolves but routes match card to fallback`() = runTest {
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
        assertEquals(1, fallback.shown.size)
        assertEquals("Rahim", fallback.shown.first().name)
        assertEquals("POOL:Salman", fallback.shown.first().sourceLabel)
    }

    @Test
    fun `not own with pool match shows best photo match via overlay`() = runTest {
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

        assertEquals(1, renderer.shown.size)
        val card = renderer.shown.first()
        assertEquals("Rahim", card.name)
        assertEquals("https://example.com/a.jpg", card.photoUrl)
        assertEquals("POOL:Salman", card.sourceLabel)
        assertTrue(fallback.shown.isEmpty())
    }

    @Test
    fun `ring not found shows unknown overlay card`() = runTest {
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

        assertEquals(1, renderer.shown.size)
        val card = renderer.shown.first()
        assertEquals("+8801710000001", card.name)
        assertEquals("UNKNOWN:null", card.sourceLabel)
        assertEquals("+8801710000001", card.number)
        assertTrue(fallback.shown.isEmpty())
    }

    @Test
    fun `lookup failure shows unknown overlay card`() = runTest {
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

        assertEquals(1, renderer.shown.size)
        assertEquals("UNKNOWN:null", renderer.shown.first().sourceLabel)
    }

    @Test
    fun `overlay denied and unknown routes unknown card to fallback and hides on end`() = runTest {
        val renderer = FakeRenderer()
        val fallback = FakeFallback()
        val c = controller(
            scope = this,
            renderer = renderer,
            fallback = fallback,
            lookup = { Result.success(LookupResponse(found = false, matches = emptyList())) },
            canShowOverlay = { false }
        )

        c.onCallEvent(IncomingCallEvent(IncomingCallPhase.RINGING, "+8801710000001"))
        runCurrent()
        assertEquals(1, fallback.shown.size)
        assertEquals("UNKNOWN:null", fallback.shown.first().sourceLabel)

        c.onCallEvent(IncomingCallEvent(IncomingCallPhase.DISCONNECTED, null))
        assertTrue(fallback.isHiddenAfterShow())
        assertTrue(renderer.shown.isEmpty())
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
        assertTrue(fallback.shown.isEmpty())
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
        assertEquals(1, renderer.shown.size)
        assertEquals("Second", renderer.shown.first().name)
        assertTrue(fallback.shown.isEmpty())
    }

    @Test
    fun `cancelled lookup does not show card`() = runTest {
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

        // CancellationException re-throws; renderer never invoked
        assertTrue(renderer.shown.isEmpty())
        assertTrue(fallback.shown.isEmpty())
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
    fun `presentation managed elsewhere skips renderer and fallback`() = runTest {
        val lookupCalls = mutableListOf<String>()
        val renderer = FakeRenderer()
        val fallback = FakeFallback()
        val c = controller(
            scope = this,
            renderer = renderer,
            fallback = fallback,
            lookup = { number -> lookupCalls += number; Result.success(LookupResponse(found = true, matches = listOf(photoMatch()))) },
            canShowOverlay = { false },
            isPresentationManagedElsewhere = { true }
        )

        c.onCallEvent(IncomingCallEvent(IncomingCallPhase.RINGING, "+8801710000001"))
        runCurrent()

        assertEquals(listOf("+8801710000001"), lookupCalls)
        assertTrue(renderer.shown.isEmpty())
        assertTrue(fallback.shown.isEmpty())
    }

    @Test
    fun `presentation managed elsewhere does not auto dismiss stale overlay`() = runTest {
        val renderer = FakeRenderer()
        val fallback = FakeFallback()
        val c = controller(
            scope = this,
            renderer = renderer,
            fallback = fallback,
            lookup = { Result.success(LookupResponse(found = false, matches = emptyList())) },
            isPresentationManagedElsewhere = { true },
            overlayTimeoutMillis = 1_000L
        )

        c.onCallEvent(IncomingCallEvent(IncomingCallPhase.RINGING, "+8801710000001"))
        advanceTimeBy(2_000)
        runCurrent()

        assertEquals(0, renderer.dismissCount)
        assertTrue(renderer.shown.isEmpty())
        assertTrue(fallback.shown.isEmpty())
    }

    private fun photoMatch() = LookupMatch("Rahim", photoUrl = "https://example.com/rahim.jpg", ownerName = "Salman")
}