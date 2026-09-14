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
import com.ringly.app.call.PhoneStateMonitor
import com.ringly.app.data.repository.ContactRepository
import com.ringly.app.data.session.SessionManager
import com.ringly.app.data.session.SharedPreferencesUserSessionStorage
import com.ringly.app.overlay.CallerIdOverlayController
import com.ringly.app.overlay.CallerIdOverlayView
import com.ringly.app.sync.ContactChangeObserver
import com.ringly.app.sync.ContactSyncWorker
import com.ringly.app.sync.SharedPreferencesSyncSnapshotStorage
import com.ringly.app.sync.SyncScheduler
import com.ringly.app.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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

    private val phoneStateMonitor: PhoneStateMonitor by lazy { PhoneStateMonitor(this) }

    private val overlayController: CallerIdOverlayController by lazy {
        val snapshotStorage = SharedPreferencesSyncSnapshotStorage(this)
        CallerIdOverlayController(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            lookup = { ContactRepository().lookup(it) },
            localNumbers = { snapshotStorage.load().entries.keys },
            canShowOverlay = { PermissionHelper.canDrawOverlays(this) },
            renderer = CallerIdOverlayView(this)
        )
    }

    override fun onCreate() {
        super.onCreate()
        observeLifecycle()
        observeContactChanges()
        phoneStateMonitor.register()
        overlayController.start()
        syncScheduler.schedulePeriodic()
        syncScheduler.scheduleOneShot()
    }

    fun refreshCallMonitoring() {
        phoneStateMonitor.register()
    }

    private fun observeLifecycle() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                syncScheduler.scheduleOneShot()
                observeContactChanges()
                phoneStateMonitor.register()
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