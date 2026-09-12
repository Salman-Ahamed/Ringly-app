package com.ringly.app.sync

import android.content.Context

interface SyncSnapshotStorage {
    fun load(): SyncSnapshot
    fun save(snapshot: SyncSnapshot)
    fun clear()
}

class SharedPreferencesSyncSnapshotStorage(context: Context) : SyncSnapshotStorage {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun load(): SyncSnapshot {
        val raw = prefs.getString(KEY_SNAPSHOT, null) ?: return SyncSnapshot.EMPTY
        return try {
            SyncSnapshotCodec.deserialize(raw)
        } catch (e: Exception) {
            SyncSnapshot.EMPTY
        }
    }

    override fun save(snapshot: SyncSnapshot) {
        prefs.edit().putString(KEY_SNAPSHOT, SyncSnapshotCodec.serialize(snapshot)).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY_SNAPSHOT).apply()
    }

    private companion object {
        const val PREFS = "ringly_prefs"
        const val KEY_SNAPSHOT = "sync_snapshot"
    }
}