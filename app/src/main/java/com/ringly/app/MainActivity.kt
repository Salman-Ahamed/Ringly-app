package com.ringly.app

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.lifecycle.lifecycleScope
import com.ringly.app.call.CallScreeningRole
import com.ringly.app.dialer.ContactListActivity
import com.ringly.app.dialer.DialerActivity
import com.ringly.app.onboarding.DenyState
import com.ringly.app.onboarding.PermissionDenyClassifier
import com.ringly.app.sync.ContactSyncWorker
import com.ringly.app.sync.SharedPreferencesSyncSnapshotStorage
import com.ringly.app.sync.SyncStatusLabel
import com.ringly.app.util.DeviceIdManager
import com.ringly.app.util.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val phonePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (isPermanentPhoneDenial(granted)) {
                PermissionHelper.openAppSettings(this)
            } else {
                updatePermissionStatuses()
                if (granted) (application as RinglyApp).refreshCallMonitoring()
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (isPermanentNotificationDenial(granted)) {
                PermissionHelper.openAppSettings(this)
            } else {
                updatePermissionStatuses()
            }
        }

    private val screeningRoleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updatePermissionStatuses()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val deviceId = DeviceIdManager.getOrCreateDeviceId(this)
        findViewById<TextView>(R.id.device_id_text).text =
            getString(R.string.device_id_label_format, deviceId)

        findViewById<View>(R.id.dialer_button).setOnClickListener {
            startActivity(Intent(this, DialerActivity::class.java))
        }
        findViewById<View>(R.id.contacts_button).setOnClickListener {
            startActivity(Intent(this, ContactListActivity::class.java))
        }
        findViewById<View>(R.id.setup_card).setOnClickListener {
            openPermissions()
        }
        findViewById<View>(R.id.setup_status_phone).setOnClickListener {
            requestPhonePermissionIfNeeded()
        }
        findViewById<View>(R.id.setup_status_notifications).setOnClickListener {
            requestNotificationPermissionIfNeeded()
        }
        findViewById<View>(R.id.setup_status_screening).setOnClickListener {
            requestScreeningRole()
        }
        exposeAsButton(findViewById<View>(R.id.setup_status_phone))
        exposeAsButton(findViewById<View>(R.id.setup_status_notifications))
        exposeAsButton(findViewById<View>(R.id.setup_status_screening))
        findViewById<View>(R.id.sync_now_button).setOnClickListener {
            startSync()
        }

        updatePermissionStatuses()
        updateSyncStatus()

        val statusText = findViewById<TextView>(R.id.status_text)
        val progress = findViewById<View>(R.id.registration_progress)
        val session = (application as RinglyApp).sessionManager

        val existingUserId = session.userId
        if (existingUserId != null) {
            statusText.text = getString(R.string.hub_status_registered)
        } else {
            statusText.text = getString(R.string.status_registering)
            progress.visibility = View.VISIBLE
            lifecycleScope.launch {
                session.ensureRegistered(deviceId, Build.MODEL)
                    .onSuccess {
                        progress.visibility = View.GONE
                        statusText.text = getString(R.string.hub_status_registered)
                    }
                    .onFailure {
                        progress.visibility = View.GONE
                        statusText.text = getString(R.string.status_register_failed)
                    }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatuses()
        updateSyncStatus()
    }

    private fun openPermissions() {
        val intent = Intent(this, com.ringly.app.onboarding.PermissionRationaleActivity::class.java)
        startActivity(intent)
    }

    private fun exposeAsButton(view: View) {
        ViewCompat.setAccessibilityDelegate(view, object : androidx.core.view.AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = Button::class.java.name
            }

            override fun onPopulateAccessibilityEvent(host: View, event: AccessibilityEvent) {
                super.onPopulateAccessibilityEvent(host, event)
                event.className = Button::class.java.name
            }
        })
    }

    private fun requestPhonePermissionIfNeeded() {
        if (!PermissionHelper.hasPermission(this, Manifest.permission.READ_PHONE_STATE)) {
            phonePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
        }
    }

    private fun isPermanentPhoneDenial(granted: Boolean): Boolean =
        PermissionDenyClassifier.classify(
            granted,
            shouldShowRequestPermissionRationale(Manifest.permission.READ_PHONE_STATE)
        ) == DenyState.PERMANENT

    private fun isPermanentNotificationDenial(granted: Boolean): Boolean =
        PermissionDenyClassifier.classify(
            granted,
            shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        ) == DenyState.PERMANENT

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            !PermissionHelper.hasPermission(this, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestScreeningRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val roleManager = runCatching { getSystemService(RoleManager::class.java) }.getOrNull()
        val requestIntent = roleManager
            ?.let { runCatching { it.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING) }.getOrNull() }
        if (requestIntent != null) {
            runCatching { screeningRoleLauncher.launch(requestIntent) }
                .onFailure { openDefaultAppsSettings() }
        } else {
            openDefaultAppsSettings()
        }
    }

    private fun isScreeningRoleHeld(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return runCatching {
            getSystemService(RoleManager::class.java)
                ?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
                ?: false
        }.getOrDefault(false)
    }

    private fun openDefaultAppsSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
    }

    private fun updateSyncStatus() {
        val text = findViewById<TextView>(R.id.sync_status_text)
        val snapshot = SharedPreferencesSyncSnapshotStorage(this).load()
        val label = SyncStatusLabel.describe(snapshot.lastSyncAt, System.currentTimeMillis())
        text.text = if (label == null) {
            getString(R.string.hub_status_never_synced)
        } else {
            getString(R.string.hub_status_synced, label)
        }
    }

    private var syncRunning = false

    private fun startSync() {
        if (syncRunning) return
        syncRunning = true
        val button = findViewById<View>(R.id.sync_now_button)
        button.isEnabled = false
        val text = findViewById<TextView>(R.id.sync_status_text)
        text.text = getString(R.string.hub_status_syncing)
        val engine = ContactSyncWorker.defaultEngine(this, (application as RinglyApp).sessionManager)
        lifecycleScope.launch(Dispatchers.IO) {
            val outcome = runCatching { engine.run() }.getOrNull()
            withContext(Dispatchers.Main) {
                syncRunning = false
                button.isEnabled = true
                if (outcome == com.ringly.app.sync.SyncOutcome.SUCCESS) {
                    updateSyncStatus()
                } else {
                    text.text = getString(R.string.hub_status_sync_failed)
                }
            }
        }
    }

    private fun updatePermissionStatuses() {
        val grantedColor = ContextCompat.getColor(this, R.color.granted_green)
        val deniedColor = ContextCompat.getColor(this, R.color.denied_text)

        val contacts = findViewById<TextView>(R.id.setup_status_contacts)
        if (PermissionHelper.hasPermission(this, Manifest.permission.READ_CONTACTS)) {
            contacts.text = getString(R.string.hub_contacts_permission_granted)
            contacts.setTextColor(grantedColor)
        } else {
            contacts.text = getString(R.string.hub_contacts_permission_needed)
            contacts.setTextColor(deniedColor)
        }

        val phone = findViewById<TextView>(R.id.setup_status_phone)
        if (PermissionHelper.hasPermission(this, Manifest.permission.READ_PHONE_STATE)) {
            phone.text = getString(R.string.hub_phone_permission_granted)
            phone.setTextColor(grantedColor)
        } else {
            phone.text = getString(R.string.hub_phone_permission_needed)
            phone.setTextColor(deniedColor)
        }

        val overlay = findViewById<TextView>(R.id.setup_status_overlay)
        if (PermissionHelper.canDrawOverlays(this)) {
            overlay.text = getString(R.string.hub_overlay_permission_granted)
            overlay.setTextColor(grantedColor)
        } else {
            overlay.text = getString(R.string.hub_overlay_permission_needed)
            overlay.setTextColor(deniedColor)
        }

        val notifications = findViewById<TextView>(R.id.setup_status_notifications)
        if (Build.VERSION.SDK_INT >= 33) {
            notifications.visibility = View.VISIBLE
            if (PermissionHelper.hasPermission(this, Manifest.permission.POST_NOTIFICATIONS)) {
                notifications.text = getString(R.string.hub_notifications_permission_granted)
                notifications.setTextColor(grantedColor)
            } else {
                notifications.text = getString(R.string.hub_notifications_permission_needed)
                notifications.setTextColor(deniedColor)
            }
        } else {
            notifications.visibility = View.GONE
        }

        val screening = findViewById<TextView>(R.id.setup_status_screening)
        if (CallScreeningRole.isSupported(Build.VERSION.SDK_INT)) {
            screening.visibility = View.VISIBLE
            val held = isScreeningRoleHeld()
            when (CallScreeningRole.status(Build.VERSION.SDK_INT, held)) {
                CallScreeningRole.Status.GRANTED -> {
                    screening.text = getString(R.string.hub_screening_permission_granted)
                    screening.setTextColor(grantedColor)
                }
                CallScreeningRole.Status.NEEDED -> {
                    screening.text = getString(R.string.hub_screening_permission_needed)
                    screening.setTextColor(deniedColor)
                }
                CallScreeningRole.Status.UNAVAILABLE -> screening.visibility = View.GONE
            }
        } else {
            screening.visibility = View.GONE
        }
    }
}