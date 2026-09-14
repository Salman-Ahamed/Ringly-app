package com.ringly.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ringly.app.util.DeviceIdManager
import com.ringly.app.util.PermissionHelper
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val phonePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) (application as RinglyApp).refreshCallMonitoring()
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val deviceId = DeviceIdManager.getOrCreateDeviceId(this)
        findViewById<TextView>(R.id.device_id_text).text =
            getString(R.string.device_id_label_format, deviceId)

        findViewById<Button>(R.id.overlay_permission_button).setOnClickListener {
            PermissionHelper.openOverlaySettings(this)
        }
        updateOverlayStatus()
        requestPhonePermissionIfNeeded()
        requestNotificationPermissionIfNeeded()

        val statusText = findViewById<TextView>(R.id.status_text)
        val session = (application as RinglyApp).sessionManager

        val existingUserId = session.userId
        if (existingUserId != null) {
            statusText.text = getString(R.string.status_registered_format, existingUserId)
        } else {
            statusText.text = getString(R.string.status_registering)
            lifecycleScope.launch {
                session.ensureRegistered(deviceId, Build.MODEL)
                    .onSuccess { userId ->
                        statusText.text = getString(R.string.status_registered_format, userId)
                    }
                    .onFailure {
                        statusText.text = getString(R.string.status_register_failed)
                    }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateOverlayStatus()
    }

    private fun requestPhonePermissionIfNeeded() {
        if (!PermissionHelper.hasPermission(this, Manifest.permission.READ_PHONE_STATE)) {
            phonePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            !PermissionHelper.hasPermission(this, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun updateOverlayStatus() {
        val overlayStatus = findViewById<TextView>(R.id.overlay_status_text)
        overlayStatus.text = if (PermissionHelper.canDrawOverlays(this)) {
            getString(R.string.overlay_status_enabled)
        } else {
            getString(R.string.overlay_status_needs_permission)
        }
    }
}