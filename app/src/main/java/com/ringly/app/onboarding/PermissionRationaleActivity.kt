package com.ringly.app.onboarding

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.ringly.app.R
import com.ringly.app.RinglyApp
import com.ringly.app.util.PermissionHelper

class PermissionRationaleActivity : AppCompatActivity() {

    private lateinit var stepsContainer: LinearLayout
    private lateinit var binder: PermissionRowBinder

    private val permanentlyDenied = mutableSetOf<String>()

    private val runtimeLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            trackPermanentDenials(result)
            refreshChecklist()
            val app = application as RinglyApp
            app.refreshCallMonitoring()
        }

    private val overlayLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshChecklist()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission_rationale)

        stepsContainer = findViewById(R.id.permission_steps_container)
        binder = PermissionRowBinder(
            layoutInflater,
            stepsContainer,
            onAllow = { kind -> onAllow(kind) },
            onOpenAppSettings = { PermissionHelper.openAppSettings(this) }
        )
        findViewById<com.google.android.material.button.MaterialButton>(R.id.rationale_done_button)
            .setOnClickListener { finish() }
        refreshChecklist()
    }

    override fun onResume() {
        super.onResume()
        refreshChecklist()
    }

    private fun trackPermanentDenials(result: Map<String, Boolean>) {
        result.forEach { (permission, granted) ->
            if (PermissionDenyClassifier.classify(
                    granted,
                    shouldShowRequestPermissionRationale(permission)
                ) == DenyState.PERMANENT
            ) {
                permanentlyDenied += permission
            }
        }
    }

    private fun onAllow(kind: PermissionKind) {
        when (kind.action) {
            PermissionAction.ALLOW_RUNTIME -> {
                kind.runtimePermission?.let {
                    runtimeLauncher.launch(arrayOf(it))
                }
            }
            PermissionAction.OPEN_SETTINGS -> {
                PermissionHelper.openOverlaySettings(this)
            }
        }
    }

    private fun refreshChecklist() {
        val checklist = PermissionChecklist.build(
            apiLevel = Build.VERSION.SDK_INT,
            hasContacts = PermissionHelper.hasPermission(this, Manifest.permission.READ_CONTACTS),
            hasPhoneState = PermissionHelper.hasPermission(this, Manifest.permission.READ_PHONE_STATE),
            hasNotifications =
                Build.VERSION.SDK_INT < 33 ||
                PermissionHelper.hasPermission(this, Manifest.permission.POST_NOTIFICATIONS),
            canDrawOverlays = PermissionHelper.canDrawOverlays(this),
            permanentlyDenied = { kind ->
                kind.runtimePermission?.let { it in permanentlyDenied } ?: false
            }
        )
        binder.render(checklist)
    }
}