package com.ringly.app.onboarding

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.ringly.app.MainActivity
import com.ringly.app.R
import com.ringly.app.RinglyApp
import com.ringly.app.sync.ContactSyncWorker
import com.ringly.app.sync.SyncOutcome
import com.ringly.app.util.DeviceIdManager
import com.ringly.app.util.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OnboardingActivity : AppCompatActivity() {

    private enum class Step { WELCOME, NAME, PERMISSIONS, SYNC, READY }

    private lateinit var storage: OnboardingStorage
    private lateinit var titleView: TextView
    private lateinit var subtitleView: TextView
    private lateinit var content: FrameLayout
    private lateinit var progress: View
    private lateinit var primary: MaterialButton
    private lateinit var secondary: MaterialButton
    private lateinit var permissionsList: LinearLayout
    private lateinit var permissionBinder: PermissionRowBinder

    private var step = Step.WELCOME

    private val permanentlyDenied = mutableSetOf<String>()

    private val runtimeLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            trackPermanentDenials(result)
            refreshPermissionStep()
            (application as RinglyApp).refreshCallMonitoring()
        }

    private val overlayLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshPermissionStep()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storage = OnboardingStorage(this)

        val session = (application as RinglyApp).sessionManager
        if (session.isRegistered || storage.onboardingCompleted) {
            openMain()
            return
        }

        setContentView(R.layout.activity_onboarding)
        titleView = findViewById(R.id.onboarding_title)
        subtitleView = findViewById(R.id.onboarding_subtitle)
        content = findViewById(R.id.onboarding_content)
        progress = findViewById(R.id.onboarding_progress)
        primary = findViewById(R.id.onboarding_primary)
        secondary = findViewById(R.id.onboarding_secondary)

        showStep(Step.WELCOME)
    }

    override fun onResume() {
        super.onResume()
        if (::permissionBinder.isInitialized && step == Step.PERMISSIONS) {
            refreshPermissionStep()
        }
    }

    private fun showStep(next: Step) {
        step = next
        primary.visibility = View.VISIBLE
        secondary.visibility = View.GONE
        progress.visibility = View.GONE
        content.removeAllViews()

        when (next) {
            Step.WELCOME -> {
                titleView.text = getString(R.string.onboarding_welcome_title)
                subtitleView.text = getString(R.string.onboarding_welcome_subtitle)
                primary.text = getString(R.string.onboarding_get_started)
                primary.setOnClickListener { showStep(Step.NAME) }
            }
            Step.NAME -> {
                titleView.text = getString(R.string.onboarding_name_title)
                subtitleView.text = " "
                content.addView(
                    layoutInflater.inflate(R.layout.item_onboarding_name, content, false)
                )
                primary.text = getString(R.string.onboarding_next)
                primary.setOnClickListener { registerAndAdvance() }
            }
            Step.PERMISSIONS -> {
                titleView.text = getString(R.string.onboarding_permissions_title)
                subtitleView.text = getString(R.string.onboarding_permissions_subtitle)
                val root = layoutInflater.inflate(
                    R.layout.item_onboarding_permissions,
                    content,
                    false
                )
                content.addView(root)
                permissionsList = root.findViewById(R.id.onboarding_permissions_container)
                permissionBinder = PermissionRowBinder(
                    layoutInflater,
                    permissionsList,
                    onAllow = { kind -> onAllow(kind) },
                    onOpenAppSettings = { PermissionHelper.openAppSettings(this) }
                )
                refreshPermissionStep()
                secondary.text = getString(R.string.rationale_not_now)
                secondary.visibility = View.VISIBLE
                secondary.setOnClickListener { showStep(Step.SYNC) }
                primary.text = getString(R.string.onboarding_next)
                primary.setOnClickListener { showStep(Step.SYNC) }
            }
            Step.SYNC -> {
                titleView.text = getString(R.string.onboarding_sync_title)
                subtitleView.text = getString(R.string.onboarding_sync_subtitle)
                primary.visibility = View.GONE
                progress.visibility = View.VISIBLE
                runSync()
            }
            Step.READY -> {
                titleView.text = getString(R.string.onboarding_ready_title)
                subtitleView.text = getString(R.string.onboarding_ready_subtitle)
                storage.onboardingCompleted = true
                primary.text = getString(R.string.onboarding_ready_start)
                primary.setOnClickListener { openMain() }
            }
        }
    }

    private fun registerAndAdvance() {
        val nameInput = content.findViewById<EditText>(R.id.onboarding_name_input)
        val name = nameInput?.text?.toString()?.trim().orEmpty().ifBlank { Build.MODEL }
        val deviceId = DeviceIdManager.getOrCreateDeviceId(this)
        val session = (application as RinglyApp).sessionManager

        primary.isEnabled = false
        progress.visibility = View.VISIBLE
        titleView.text = getString(R.string.status_registering)

        lifecycleScope.launch {
            session.ensureRegistered(deviceId, name)
                .onSuccess { showStep(Step.PERMISSIONS) }
                .onFailure {
                    Toast.makeText(
                        this@OnboardingActivity,
                        R.string.status_register_failed,
                        Toast.LENGTH_LONG
                    ).show()
                    titleView.text = getString(R.string.onboarding_name_title)
                    progress.visibility = View.GONE
                    primary.isEnabled = true
                }
        }
    }

    private fun runSync() {
        val session = (application as RinglyApp).sessionManager
        val engine = ContactSyncWorker.defaultEngine(this, session)
        titleView.text = getString(R.string.onboarding_sync_title)
        subtitleView.text = getString(R.string.onboarding_sync_subtitle)
        primary.visibility = View.GONE
        secondary.visibility = View.GONE
        progress.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val outcome = runCatching { engine.run() }.getOrDefault(SyncOutcome.RETRY)
            val hasContacts = PermissionHelper.hasPermission(
                this@OnboardingActivity,
                Manifest.permission.READ_CONTACTS
            )
            val decision = SyncFlowRouter.decide(outcome, hasContacts)
            withContext(Dispatchers.Main) { handleSyncResult(decision) }
        }
    }

    private fun handleSyncResult(decision: SyncFlowResult) {
        progress.visibility = View.GONE
        when (decision) {
            SyncFlowResult.READY -> showStep(Step.READY)
            SyncFlowResult.READY_DEGRADED -> showReadyDegraded()
            SyncFlowResult.FAILED -> showSyncFailed()
        }
    }

    private fun showReadyDegraded() {
        step = Step.READY
        titleView.text = getString(R.string.onboarding_ready_title)
        subtitleView.text = getString(R.string.onboarding_ready_subtitle_degraded)
        storage.onboardingCompleted = true
        primary.text = getString(R.string.onboarding_ready_start)
        primary.visibility = View.VISIBLE
        primary.setOnClickListener { openMain() }
    }

    private fun showSyncFailed() {
        titleView.text = getString(R.string.onboarding_sync_title)
        subtitleView.text = getString(R.string.onboarding_sync_failed)
        primary.text = getString(R.string.onboarding_retry)
        primary.visibility = View.VISIBLE
        primary.setOnClickListener { runSync() }
        secondary.text = getString(R.string.onboarding_skip)
        secondary.visibility = View.VISIBLE
        secondary.setOnClickListener { showReadyDegraded() }
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

    private fun refreshPermissionStep() {
        permissionBinder.render(
            PermissionChecklist.build(
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
        )
    }

    private fun onAllow(kind: PermissionKind) {
        when (kind.action) {
            PermissionAction.ALLOW_RUNTIME -> {
                kind.runtimePermission?.let { runtimeLauncher.launch(arrayOf(it)) }
            }
            PermissionAction.OPEN_SETTINGS -> {
                PermissionHelper.openOverlaySettings(this)
            }
        }
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}