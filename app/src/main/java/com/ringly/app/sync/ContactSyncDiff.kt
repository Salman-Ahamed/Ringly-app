package com.ringly.app.sync

data class SyncPlan(
    val uploads: List<ContactReadCandidate>,
    val sync: List<SyncEntryPlan>,
    val deletes: List<String>
)

data class SyncEntryPlan(
    val number: String,
    val name: String,
    val photoHash: String?,
    val needsPhotoUpload: Boolean
)

object ContactSyncDiff {

    fun computeDiff(device: List<ContactReadCandidate>, snapshot: SyncSnapshot): SyncPlan {
        val deviceByNumber = LinkedHashMap<String, ContactReadCandidate>()
        for (candidate in device) {
            deviceByNumber.putIfAbsent(candidate.number, candidate)
        }

        val uploads = mutableListOf<ContactReadCandidate>()
        val sync = mutableListOf<SyncEntryPlan>()

        for ((number, candidate) in deviceByNumber) {
            val prev = snapshot.entries[number]
            val photoChanged = prev?.photoHash != candidate.photoHash
            val nameChanged = prev?.name != candidate.name

            if (prev == null || nameChanged || photoChanged) {
                val needsUpload = candidate.photoHash != null && (prev == null || photoChanged)
                if (needsUpload) uploads.add(candidate)
                sync.add(SyncEntryPlan(number, candidate.name, candidate.photoHash, needsUpload))
            }
        }

        val deviceNumbers = deviceByNumber.keys
        val deletes = snapshot.entries.values
            .filter { !deviceNumbers.contains(it.number) }
            .mapNotNull { it.serverContactId }

        return SyncPlan(uploads, sync, deletes)
    }
}