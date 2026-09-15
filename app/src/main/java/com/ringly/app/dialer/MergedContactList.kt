package com.ringly.app.dialer

import com.ringly.app.data.models.PoolContact
import com.ringly.app.sync.SyncEntry

enum class ContactSource { LOCAL, POOL }

data class MergedContact(
    val number: String,
    val name: String,
    val photoUrl: String?,
    val source: ContactSource,
    val ownerName: String?
)

object MergedContactList {

    fun build(
        local: Map<String, SyncEntry>,
        pool: List<PoolContact>
    ): List<MergedContact> {
        val result = mutableListOf<MergedContact>()
        val seen = HashSet<String>()

        for (entry in local.values) {
            if (seen.add(entry.number)) {
                result.add(
                    MergedContact(
                        number = entry.number,
                        name = entry.name,
                        photoUrl = entry.photoUrl,
                        source = ContactSource.LOCAL,
                        ownerName = null
                    )
                )
            }
        }

        for (contact in pool) {
            if (seen.add(contact.number)) {
                result.add(
                    MergedContact(
                        number = contact.number,
                        name = contact.name,
                        photoUrl = contact.photoUrl,
                        source = ContactSource.POOL,
                        ownerName = contact.ownerName
                    )
                )
            }
        }

        return result.sortedBy { it.name.lowercase() }
    }

    fun filter(
        contacts: List<MergedContact>,
        query: String,
        source: ContactSource?
    ): List<MergedContact> {
        val digitQuery = query.filter { it.isDigit() }
        val nameQuery = query.trim().lowercase()

        return contacts.filter { contact ->
            val matchesSource = source == null || contact.source == source
            val matchesQuery = (nameQuery.isEmpty() && digitQuery.isEmpty()) ||
                (nameQuery.isNotEmpty() && contact.name.lowercase().contains(nameQuery)) ||
                (digitQuery.isNotEmpty() && contact.number.contains(digitQuery))
            matchesSource && matchesQuery
        }
    }
}