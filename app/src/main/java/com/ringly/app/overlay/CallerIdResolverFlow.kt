package com.ringly.app.overlay

import com.ringly.app.data.models.LookupMatch
import com.ringly.app.data.models.LookupResponse
import com.ringly.app.sync.SyncSnapshot
import kotlinx.coroutines.CancellationException

class CallerIdResolverFlow(
    private val lookup: suspend (String) -> Result<LookupResponse>,
    private val ownContacts: () -> SyncSnapshot,
    private val sourceLabel: (CallerIdSource, String?) -> String,
    private val selectBest: (List<LookupMatch>) -> LookupMatch? = BestMatchSelector::selectBest
) {

    suspend fun resolveCard(number: String): CallerIdCard {
        val ownEntry = ownContacts().entryFor(number)
        val poolMatch = if (ownEntry == null) lookupPoolMatch(number) else null
        val resolution = CallerIdResolver.resolve(number, ownEntry, poolMatch)
        return CallerIdCard(
            number = resolution.number,
            name = resolution.name,
            sourceLabel = sourceLabel(resolution.source, resolution.ownerName),
            photoUrl = resolution.photoUrl
        )
    }

    private suspend fun lookupPoolMatch(number: String): LookupMatch? {
        val response = try {
            lookup(number)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }.getOrNull()
        return response
            ?.takeIf { it.found }
            ?.matches
            ?.let(selectBest)
    }
}