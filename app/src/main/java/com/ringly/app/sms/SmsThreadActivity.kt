package com.ringly.app.sms

import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.ringly.app.R
import com.ringly.app.RinglyApp
import kotlinx.coroutines.launch

class SmsThreadActivity : AppCompatActivity() {

    private val repository by lazy { SmsRepository(applicationContext) }
    private val callerIdProvider by lazy { (application as RinglyApp).callerIdCardProvider }
    private val threadId by lazy { intent.getLongExtra(EXTRA_THREAD_ID, -1L) }
    private val address by lazy { intent.getStringExtra(EXTRA_ADDRESS).orEmpty() }
    private lateinit var adapter: SmsMessageAdapter
    private lateinit var input: EditText

    private val observer by lazy {
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                if (!selfChange) reload()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sms_thread)

        adapter = SmsMessageAdapter()
        findViewById<RecyclerView>(R.id.thread_recycler).apply {
            layoutManager = LinearLayoutManager(this@SmsThreadActivity)
            adapter = this@SmsThreadActivity.adapter
        }

        input = findViewById(R.id.thread_input)
        findViewById<View>(R.id.thread_back_button).setOnClickListener { finish() }
        findViewById<View>(R.id.thread_send_button).setOnClickListener { sendMessage() }
    }

    override fun onResume() {
        super.onResume()
        if (threadId > 0) repository.markThreadRead(threadId)
        repository.registerObserver(observer)
        resolveTitle()
        reload()
    }

    override fun onPause() {
        repository.unregisterObserver(observer)
        super.onPause()
    }

    private fun resolveTitle() {
        val preset = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val titleView = findViewById<TextView>(R.id.thread_title_text)
        titleView.text = preset.ifBlank { address.ifBlank { getString(R.string.sms_unknown_sender) } }
        if (preset.isBlank() && address.isNotBlank()) {
            lifecycleScope.launch {
                val card = try {
                    callerIdProvider(address)
                } catch (t: Throwable) {
                    null
                }
                val title = card?.name?.takeIf { it.isNotBlank() } ?: address
                titleView.text = title
            }
        }
    }

    private fun reload() {
        if (!repository.hasReadPermission()) {
            showEmpty()
            return
        }
        lifecycleScope.launch {
            val rows = runCatching { repository.loadThread(threadId) }
                .getOrElse { loadThreadError(); return@launch }
            val ui = rows.map { message ->
                val outgoing =
                    message.type == android.provider.Telephony.TextBasedSmsColumns.MESSAGE_TYPE_SENT ||
                        message.type == android.provider.Telephony.TextBasedSmsColumns.MESSAGE_TYPE_OUTBOX
                SmsUiMessage(
                    body = message.body,
                    timeLabel = smsTimeLabel(this@SmsThreadActivity, message.date),
                    outgoing = outgoing
                )
            }
            adapter.submit(ui)
            findViewById<View>(R.id.thread_recycler).isVisible = ui.isNotEmpty()
            showEmpty(ui.isEmpty())
        }
    }

    private fun showEmpty(show: Boolean = true) {
        findViewById<View>(R.id.thread_empty).isVisible = show
    }

    private fun loadThreadError() {
        Snackbar.make(findViewById(R.id.thread_recycler), R.string.sms_error, Snackbar.LENGTH_SHORT).show()
    }

    private fun sendMessage() {
        val body = input.text.toString().trim()
        if (body.isEmpty() || address.isEmpty()) return
        val sent = runCatching { repository.send(address, body) }.getOrDefault(false)
        input.setText("")
        if (!sent) {
            Snackbar.make(findViewById(R.id.thread_send_button), R.string.sms_send_failed_snackbar, Snackbar.LENGTH_SHORT)
                .show()
            return
        }
        if (threadId <= 0) {
            finish()
        } else {
            lifecycleScope.launch { reload() }
        }
    }

    companion object {
        const val EXTRA_THREAD_ID = "extra_thread_id"
        const val EXTRA_ADDRESS = "extra_address"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_PHOTO = "extra_photo"
    }
}