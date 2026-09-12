package com.ringly.app.sync

import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ringly.app.data.repository.ContactRepository
import com.ringly.app.data.session.SessionManager
import com.ringly.app.data.session.SharedPreferencesUserSessionStorage
import com.ringly.app.util.DeviceIdManager
import com.ringly.app.util.PermissionHelper

class ContactSyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val engine: ContactSyncEngine
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = when (engine.run()) {
        SyncOutcome.SUCCESS -> Result.success()
        SyncOutcome.RETRY -> Result.retry()
        SyncOutcome.FAILURE -> Result.failure()
    }

    companion object {
        fun defaultEngine(context: Context, sessionManager: SessionManager): ContactSyncEngine {
            return ContactSyncEngine(
                snapshotStorage = SharedPreferencesSyncSnapshotStorage(context),
                reader = DefaultContactReader(context),
                repository = ContactRepository(),
                sessionManager = sessionManager,
                deviceIdProvider = { DeviceIdManager.getOrCreateDeviceId(context) },
                defaultName = Build.MODEL,
                hasPermission = { PermissionHelper.hasAll(context, PermissionHelper.SYNC_PERMISSIONS) }
            )
        }
    }
}