package com.ringly.app.overlay

import com.ringly.app.data.models.LookupMatch
import com.ringly.app.data.models.LookupResponse
import com.ringly.app.sync.SyncEntry
import com.ringly.app.sync.SyncSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallerIdResolverFlowTest {

    private val number = "+8801710000001"

    private fun flow(
        lookup: suspend (String) -> Result<LookupResponse> = { Result.success(LookupResponse(found = false, matches = emptyList())) },
        ownContacts: () -> SyncSnapshot = { SyncSnapshot.EMPTY },
        sourceLabel: (CallerIdSource, String?) -> String = { source, owner -> "$source:${owner ?: "null"}" }
    ) = CallerIdResolverFlow(
        lookup = lookup,
        ownContacts = ownContacts,
        sourceLabel = sourceLabel,
        selectBest = BestMatchSelector::selectBest
    )

    @Test
    fun `own entry resolves without network lookup`() = runTest {
        val lookupCalls = mutableListOf<String>()
        val f = flow(
            lookup = { n -> lookupCalls += n; Result.success(LookupResponse(found = true, matches = listOf(LookupMatch("Pool", photoUrl = null, ownerName = "Salman")))) },
            ownContacts = { SyncSnapshot(entries = mapOf(number to SyncEntry(number, "Vaiya", photoUrl = "https://example.com/v.jpg"))) }
        )

        val card = f.resolveCard(number)

        assertTrue(lookupCalls.isEmpty())
        assertEquals("Vaiya", card.name)
        assertEquals("OWN_PHONE:null", card.sourceLabel)
        assertEquals("https://example.com/v.jpg", card.photoUrl)
    }

    @Test
    fun `pool match resolves when not own`() = runTest {
        val f = flow(
            lookup = { Result.success(LookupResponse(found = true, matches = listOf(LookupMatch("Rahim", photoUrl = "https://example.com/a.jpg", ownerName = "Salman")))) }
        )

        val card = f.resolveCard(number)

        assertEquals("Rahim", card.name)
        assertEquals("POOL:Salman", card.sourceLabel)
        assertEquals("https://example.com/a.jpg", card.photoUrl)
    }

    @Test
    fun `not found resolves unknown`() = runTest {
        val f = flow(lookup = { Result.success(LookupResponse(found = false, matches = emptyList())) })

        val card = f.resolveCard(number)

        assertEquals(number, card.name)
        assertEquals("UNKNOWN:null", card.sourceLabel)
    }

    @Test
    fun `lookup failure resolves unknown`() = runTest {
        val f = flow(lookup = { Result.failure(RuntimeException("down")) })

        val card = f.resolveCard(number)

        assertEquals(number, card.name)
        assertEquals("UNKNOWN:null", card.sourceLabel)
    }

    @Test
    fun `own wins over pool without lookup`() = runTest {
        val lookupCalls = mutableListOf<String>()
        val f = flow(
            lookup = { n -> lookupCalls += n; Result.success(LookupResponse(found = true, matches = listOf(LookupMatch("Rahim", photoUrl = null, ownerName = "Salman")))) },
            ownContacts = { SyncSnapshot(entries = mapOf(number to SyncEntry(number, "Vaiya"))) }
        )

        val card = f.resolveCard(number)

        assertTrue(lookupCalls.isEmpty())
        assertEquals("Vaiya", card.name)
    }

    @Test
    fun `cancellation rethrows`() = runTest {
        val f = flow(lookup = { throw CancellationException("cancelled") })

        var result: CallerIdCard? = null
        var cancelled = false
        try {
            result = f.resolveCard(number)
        } catch (e: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
        assertEquals(null, result)
    }
}