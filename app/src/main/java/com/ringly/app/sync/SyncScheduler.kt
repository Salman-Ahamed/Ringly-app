package com.ringly.app.sync

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration

class SyncScheduler(private val context: Context) {

    fun scheduleOneShot() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_ONE_SHOT,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ContactSyncWorker>().build()
        )
    }

    fun schedulePeriodic() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<ContactSyncWorker>(Duration.ofHours(PERIODIC_HOURS)).build()
        )
    }

    fun cancelAll() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_ONE_SHOT)
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC)
    }

    companion object {
        const val UNIQUE_ONE_SHOT = "ringly_contact_sync_oneshot"
        const val UNIQUE_PERIODIC = "ringly_contact_sync_periodic"
        private const val PERIODIC_HOURS = 6L
    }
}