package com.ringly.app.overlay

import com.ringly.app.data.models.LookupMatch
import com.ringly.app.sync.SyncEntry

enum class CallerIdSource { OWN_PHONE, POOL, UNKNOWN }

data class CallerIdResolution(
    val number: String,
    val name: String,
    val photoUrl: String?,
    val source: CallerIdSource,
    val ownerName: String? = null
)

object CallerIdResolver {

    fun resolve(number: String, ownEntry: SyncEntry?, poolMatch: LookupMatch?): CallerIdResolution = when {
        ownEntry != null ->
            CallerIdResolution(
                number = number,
                name = ownEntry.name.ifBlank { number },
                photoUrl = ownEntry.photoUrl,
                source = CallerIdSource.OWN_PHONE
            )
        poolMatch != null ->
            CallerIdResolution(
                number = number,
                name = poolMatch.name.ifBlank { number },
                photoUrl = poolMatch.photoUrl,
                source = CallerIdSource.POOL,
                ownerName = poolMatch.ownerName
            )
        else ->
            CallerIdResolution(
                number = number,
                name = number,
                photoUrl = null,
                source = CallerIdSource.UNKNOWN
            )
    }
}