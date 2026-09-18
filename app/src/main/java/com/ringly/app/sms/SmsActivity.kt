package com.ringly.app.sms

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ringly.app.R
import com.ringly.app.RinglyApp
import com.ringly.app.sync.SyncLog
import kotlinx.coroutines.launch

class SmsActivity : AppCompatActivity() {

    private val repository by lazy { SmsRepository(applicationContext) }
    private val callerIdProvider by lazy { (application as RinglyApp).callerIdCardProvider }
    private lateinit var adapter: SmsConversationAdapter

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) load() else renderLoadState(loadState)
        }

    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (!SmsRole.isHeld(this)) {
                SyncLog.d(TAG, "role not granted after request (result=${result.resultCode}) — opening default apps")
                runCatching { startActivity(SmsRole.settingsIntent()) }
            }
            updateDefaultBanner()
            load()
        }

    private val observer by lazy {
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                if (!selfChange) load()
            }
        }
    }

    private var loadState: LoadState = LoadState.LOADING

    private enum class LoadState { LOADING, CONTENT, EMPTY, PERMISSION, ERROR }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sms)

        adapter = SmsConversationAdapter { conversation ->
            startActivity(
                Intent(this, SmsThreadActivity::class.java)
                    .putExtra(SmsThreadActivity.EXTRA_THREAD_ID, conversation.threadId)
                    .putExtra(SmsThreadActivity.EXTRA_ADDRESS, conversation.address)
                    .putExtra(SmsThreadActivity.EXTRA_TITLE, conversation.title)
                    .putExtra(SmsThreadActivity.EXTRA_PHOTO, conversation.photoUrl)
            )
        }

        findViewById<RecyclerView>(R.id.sms_recycler).apply {
            layoutManager = LinearLayoutManager(this@SmsActivity)
            this.adapter = this@SmsActivity.adapter
        }

        findViewById<View>(R.id.sms_retry).setOnClickListener { load() }
        findViewById<View>(R.id.sms_grant_button).setOnClickListener { requestSmsPermission() }
        findViewById<View>(R.id.sms_default_button).setOnClickListener {
            try {
                val intent = SmsRole.requestIntent(this)
                SyncLog.d(TAG, "role request intent: $intent")
                if (intent != null) roleLauncher.launch(intent)
            } catch (t: Throwable) {
                SyncLog.d(TAG, "role request failed", t)
            }
        }
        findViewById<View>(R.id.sms_compose_fab).setOnClickListener { showComposeDialog() }
    }

    override fun onResume() {
        super.onResume()
        updateDefaultBanner()
        repository.registerObserver(observer)
        load()
    }

    override fun onPause() {
        repository.unregisterObserver(observer)
        super.onPause()
    }

    private fun requestSmsPermission() {
        permissionLauncher.launch(Manifest.permission.READ_SMS)
    }

    private fun updateDefaultBanner() {
        val show = SmsRole.isHeld(this)
        findViewById<View>(R.id.sms_default_button).isVisible = !show
    }

    private fun load() {
        if (!repository.hasReadPermission()) {
            loadState = LoadState.PERMISSION
            renderLoadState(LoadState.PERMISSION)
            return
        }
        loadState = LoadState.LOADING
        renderLoadState(LoadState.LOADING)
        lifecycleScope.launch {
            val ui = runCatching { buildUiModels(repository.loadConversations()) }
                .getOrElse { loadState = LoadState.ERROR; emptyList() }
            adapter.submit(ui)
            val state = if (loadState == LoadState.ERROR) LoadState.ERROR
            else if (ui.isEmpty()) LoadState.EMPTY else LoadState.CONTENT
            loadState = state
            renderLoadState(state)
        }
    }

    private suspend fun buildUiModels(conversations: List<SmsConversation>): List<SmsUiConversation> {
        val ui = mutableListOf<SmsUiConversation>()
        for (c in conversations) {
            val card = try {
                callerIdProvider(c.address ?: "")
            } catch (t: Throwable) {
                null
            }
            val title = card?.name?.takeIf { it.isNotBlank() }
                ?: c.address?.takeIf { it.isNotBlank() }
                ?: getString(R.string.sms_unknown_sender)
            ui += SmsUiConversation(
                threadId = c.threadId,
                address = c.address.orEmpty(),
                title = title,
                photoUrl = card?.photoUrl,
                snippet = c.snippet.ifBlank { getString(R.string.sms_empty) },
                timeLabel = c.date.takeIf { it > 0L }?.let { smsTimeLabel(this@SmsActivity, it) }.orEmpty(),
                unread = !c.read
            )
        }
        return ui
    }

    private fun renderLoadState(state: LoadState) {
        findViewById<View>(R.id.sms_recycler).isVisible = state == LoadState.CONTENT
        findViewById<View>(R.id.sms_empty).isVisible =
            state == LoadState.EMPTY || state == LoadState.PERMISSION
        findViewById<View>(R.id.sms_progress).isVisible = state == LoadState.LOADING
        findViewById<View>(R.id.sms_error).isVisible = state == LoadState.ERROR
        findViewById<View>(R.id.sms_grant_button).isVisible = state == LoadState.PERMISSION
        if (state == LoadState.PERMISSION) {
            findViewById<TextView>(R.id.sms_empty_text).text = getString(R.string.sms_permission_needed)
        }
    }

    private fun showComposeDialog() {
        val input = EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_PHONE
        input.hint = getString(R.string.sms_compose_input_hint)
        AlertDialog.Builder(this)
            .setTitle(R.string.sms_compose)
            .setView(input)
            .setPositiveButton(R.string.sms_compose_open) { _, _ ->
                val number = input.text.toString().trim()
                if (number.isNotEmpty()) {
                    startActivity(
                        Intent(this, SmsThreadActivity::class.java)
                            .putExtra(SmsThreadActivity.EXTRA_THREAD_ID, -1L)
                            .putExtra(SmsThreadActivity.EXTRA_ADDRESS, number)
                    )
                }
            }
            .setNegativeButton(R.string.sms_compose_cancel, null)
            .show()
    }

    companion object {
        private const val TAG = "RinglySmsActivity"
    }
}