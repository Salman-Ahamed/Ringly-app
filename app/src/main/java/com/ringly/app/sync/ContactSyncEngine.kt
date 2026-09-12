package com.ringly.app.sync

import com.ringly.app.data.models.ApiError
import com.ringly.app.data.models.ContactDto
import com.ringly.app.data.models.SyncContact
import com.ringly.app.data.models.UploadResponse
import com.ringly.app.data.repository.ContactRepository
import com.ringly.app.data.session.SessionManager

enum class SyncOutcome { SUCCESS, RETRY, FAILURE }

class ContactSyncEngine(
    private val snapshotStorage: SyncSnapshotStorage,
    private val reader: ContactReader,
    private val repository: ContactRepository,
    private val sessionManager: SessionManager,
    private val deviceIdProvider: () -> String,
    private val defaultName: String,
    private val hasPermission: () -> Boolean,
    private val nowProvider: () -> Long = System::currentTimeMillis
) {

    suspend fun run(): SyncOutcome {
        if (!hasPermission()) return SyncOutcome.SUCCESS

        val userId = sessionManager.userId
            ?: sessionManager.ensureRegistered(deviceIdProvider(), defaultName).getOrNull()
            ?: return SyncOutcome.SUCCESS

        return try {
            runSync(userId, snapshotStorage.load())
        } catch (e: ApiError) {
            val code = e.statusCode
            if (code != null && code in 400..499) SyncOutcome.FAILURE else SyncOutcome.RETRY
        } catch (e: Exception) {
            SyncLog.d(TAG, "Sync failed", e)
            SyncOutcome.RETRY
        }
    }

    private suspend fun runSync(userId: String, snapshot: SyncSnapshot): SyncOutcome {
        val refreshed = refreshSnapshotIfPossible(userId, snapshot)
        val device = reader.readAll()
        val plan = ContactSyncDiff.computeDiff(device, refreshed)

        val uploads = uploadPhotos(plan.uploads)
        val uploadFailed = plan.sync
            .filter { it.needsPhotoUpload && !uploads.containsKey(it.number) }
            .map { it.number }
            .toSet()
        val syncContacts = plan.sync.map { planEntry ->
            resolveSyncContact(planEntry, refreshed, uploads, uploadFailed)
        }

        val serverContacts = if (syncContacts.isNotEmpty()) {
            repository.sync(userId, syncContacts).getOrThrow().contacts
        } else {
            null
        }

        deleteServerContacts(userId, plan.deletes)
        snapshotStorage.save(buildSnapshot(refreshed, device, plan.sync, uploadFailed, serverContacts))
        return SyncOutcome.SUCCESS
    }

    private suspend fun refreshSnapshotIfPossible(userId: String, snapshot: SyncSnapshot): SyncSnapshot {
        val serverResult = repository.listMyContacts(userId)
        if (serverResult.isFailure) return snapshot

        val serverContacts = serverResult.getOrThrow().contacts
        val merged = LinkedHashMap(snapshot.entries)
        for (server in serverContacts) {
            if (!merged.containsKey(server.number)) {
                merged[server.number] = SyncEntry(
                    number = server.number,
                    name = server.name,
                    photoUrl = server.photoUrl,
                    photoPublicId = server.photoPublicId,
                    serverContactId = server.id
                )
            }
        }
        return SyncSnapshot(merged, snapshot.lastSyncAt)
    }

    private suspend fun uploadPhotos(candidates: List<ContactReadCandidate>): Map<String, UploadResponse> {
        val uploads = LinkedHashMap<String, UploadResponse>()
        val byDataUrl = HashMap<String, UploadResponse>()
        for (candidate in candidates) {
            val dataUrl = reader.readEncodedPhoto(candidate.contactId) ?: continue
            if (dataUrl.length > MAX_DATA_URL_LENGTH) {
                SyncLog.w(TAG, "Photo too large for ${candidate.number} (${dataUrl.length} chars)")
                continue
            }
            val existing = byDataUrl[dataUrl]
            if (existing != null) {
                uploads[candidate.number] = existing
                continue
            }
            try {
                val response = repository.uploadPhoto(dataUrl).getOrThrow()
                byDataUrl[dataUrl] = response
                uploads[candidate.number] = response
            } catch (e: ApiError) {
                SyncLog.w(TAG, "Photo upload rejected for ${candidate.number}", e)
            }
        }
        return uploads
    }

    private fun resolveSyncContact(
        planEntry: SyncEntryPlan,
        snapshot: SyncSnapshot,
        uploads: Map<String, UploadResponse>,
        uploadFailed: Set<String>
    ): SyncContact {
        val photo = when {
            planEntry.photoHash == null -> null to null
            planEntry.needsPhotoUpload && !uploadFailed.contains(planEntry.number) -> {
                val uploaded = uploads[planEntry.number]
                uploaded?.photoUrl to uploaded?.photoPublicId
            }
            else -> {
                val prev = snapshot.entries[planEntry.number]
                prev?.photoUrl to prev?.photoPublicId
            }
        }
        return SyncContact(planEntry.number, planEntry.name, photo.first, photo.second)
    }

    private suspend fun deleteServerContacts(userId: String, contactIds: List<String>) {
        for (id in contactIds) {
            try {
                repository.deleteContact(id, userId)
            } catch (e: Exception) {
                SyncLog.d(TAG, "Delete failed for $id", e)
            }
        }
    }

    private fun buildSnapshot(
        previous: SyncSnapshot,
        device: List<ContactReadCandidate>,
        syncPlans: List<SyncEntryPlan>,
        uploadFailed: Set<String>,
        serverContacts: List<ContactDto>?
    ): SyncSnapshot {
        val deviceByNumber = device.associateBy { it.number }
        val serverByNumber = serverContacts?.associateBy { it.number } ?: emptyMap()
        val syncedNumbers = syncPlans.map { it.number }.toSet()
        val entries = LinkedHashMap<String, SyncEntry>()

        for ((number, entry) in previous.entries) {
            if (!deviceByNumber.containsKey(number)) continue
            val candidate = deviceByNumber.getValue(number)
            val server = serverByNumber[number]
            entries[number] = SyncEntry(
                number = number,
                name = server?.name ?: entry.name,
                photoHash = if (number in syncedNumbers && number !in uploadFailed) {
                    candidate.photoHash
                } else {
                    entry.photoHash
                },
                photoUrl = server?.photoUrl ?: entry.photoUrl,
                photoPublicId = server?.photoPublicId ?: entry.photoPublicId,
                serverContactId = server?.id ?: entry.serverContactId
            )
        }

        for (plan in syncPlans) {
            val server = serverByNumber[plan.number] ?: continue
            entries[plan.number] = SyncEntry(
                number = plan.number,
                name = server.name,
                photoHash = if (plan.number in uploadFailed) {
                    previous.entries[plan.number]?.photoHash
                } else {
                    plan.photoHash
                },
                photoUrl = server.photoUrl,
                photoPublicId = server.photoPublicId,
                serverContactId = server.id
            )
        }

        return SyncSnapshot(entries, nowProvider())
    }

    companion object {
        private const val TAG = "ContactSync"
        private const val MAX_DATA_URL_LENGTH = 3_500_000
    }
}