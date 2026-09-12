package com.ringly.app

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.ringly.app.data.session.SessionManager
import com.ringly.app.data.session.SharedPreferencesUserSessionStorage
import com.ringly.app.sync.ContactChangeObserver
import com.ringly.app.sync.ContactSyncWorker
import com.ringly.app.sync.SyncScheduler
import com.ringly.app.util.PermissionHelper

class RinglyApp : Application(), Configuration.Provider {

    val sessionManager: SessionManager by lazy {
        SessionManager(SharedPreferencesUserSessionStorage(this))
    }

    private val syncScheduler: SyncScheduler by lazy { SyncScheduler(this) }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private val workerFactory: WorkerFactory by lazy {
        object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker? {
                return when (workerClassName) {
                    ContactSyncWorker::class.java.name -> ContactSyncWorker(
                        appContext,
                        workerParameters,
                        ContactSyncWorker.defaultEngine(appContext, sessionManager)
                    )
                    else -> null
                }
            }
        }
    }

    private val contactObserver: ContactChangeObserver by lazy {
        ContactChangeObserver(onContactsChanged = { syncScheduler.scheduleOneShot() })
    }

    override fun onCreate() {
        super.onCreate()
        observeLifecycle()
        observeContactChanges()
        syncScheduler.schedulePeriodic()
        syncScheduler.scheduleOneShot()
    }

    private fun observeLifecycle() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                syncScheduler.scheduleOneShot()
                observeContactChanges()
            }
        })
    }

    private fun observeContactChanges() {
        if (!PermissionHelper.hasPermission(this, Manifest.permission.READ_CONTACTS)) return
        try {
            contactObserver.register(this)
        } catch (e: Exception) {
            // content provider unavailable (e.g. permission revoked mid-run) — retry next onStart
        }
    }
}