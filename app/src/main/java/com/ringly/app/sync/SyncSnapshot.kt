package com.ringly.app.sync

data class SyncSnapshot(
    val entries: Map<String, SyncEntry> = emptyMap(),
    val lastSyncAt: Long = 0L
) {
    fun entryFor(number: String): SyncEntry? = entries[number]

    companion object {
        val EMPTY = SyncSnapshot()
    }
}

data class SyncEntry(
    val number: String,
    val name: String,
    val photoHash: String? = null,
    val photoUrl: String? = null,
    val photoPublicId: String? = null,
    val serverContactId: String? = null
)