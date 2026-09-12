package com.ringly.app.sync

import com.google.gson.Gson

internal object SyncSnapshotCodec {

    private val gson = Gson()

    fun serialize(snapshot: SyncSnapshot): String {
        val doc = Doc(
            entries = snapshot.entries.mapValues { (_, entry) ->
                EntryDoc(
                    number = entry.number,
                    name = entry.name,
                    photoHash = entry.photoHash,
                    photoUrl = entry.photoUrl,
                    photoPublicId = entry.photoPublicId,
                    serverContactId = entry.serverContactId
                )
            },
            lastSyncAt = snapshot.lastSyncAt
        )
        return gson.toJson(doc)
    }

    fun deserialize(raw: String): SyncSnapshot {
        return try {
            val doc = gson.fromJson(raw, Doc::class.java) ?: return SyncSnapshot.EMPTY
            val entries = doc.entries
                ?.mapNotNull { (number, entry) ->
                    number to SyncEntry(
                        number = entry?.number ?: return@mapNotNull null,
                        name = entry.name ?: return@mapNotNull null,
                        photoHash = entry.photoHash,
                        photoUrl = entry.photoUrl,
                        photoPublicId = entry.photoPublicId,
                        serverContactId = entry.serverContactId
                    )
                }
                ?.toMap()
                ?: emptyMap()
            SyncSnapshot(entries, doc.lastSyncAt ?: 0L)
        } catch (e: Exception) {
            SyncSnapshot.EMPTY
        }
    }

    private data class Doc(
        val entries: Map<String, EntryDoc>? = null,
        val lastSyncAt: Long? = null
    )

    private data class EntryDoc(
        val number: String? = null,
        val name: String? = null,
        val photoHash: String? = null,
        val photoUrl: String? = null,
        val photoPublicId: String? = null,
        val serverContactId: String? = null
    )
}