package com.ringly.app.dialer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.ringly.app.R
import com.ringly.app.RinglyApp
import com.ringly.app.sync.SharedPreferencesSyncSnapshotStorage
import kotlinx.coroutines.launch

class ContactListActivity : AppCompatActivity() {

    private val repository by lazy { com.ringly.app.data.repository.ContactRepository() }
    private val snapshotStorage by lazy { SharedPreferencesSyncSnapshotStorage(this) }

    private lateinit var searchInput: EditText
    private lateinit var filterGroup: MaterialButtonToggleGroup
    private lateinit var recycler: RecyclerView
    private lateinit var emptyView: View
    private lateinit var progressView: ProgressBar
    private lateinit var errorView: View
    private val adapter = ContactListAdapter(::onContactClick)

    private var allContacts: List<MergedContact> = emptyList()
    private var loadFailed = false

    private val searchWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = applyFilter()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_list)

        searchInput = findViewById(R.id.search_input)
        filterGroup = findViewById(R.id.filter_group)
        recycler = findViewById(R.id.contacts_recycler)
        emptyView = findViewById(R.id.contacts_empty)
        progressView = findViewById(R.id.contacts_progress)
        errorView = findViewById(R.id.contacts_error)

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        findViewById<View>(R.id.contacts_retry).setOnClickListener { loadContacts() }

        filterGroup.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) applyFilter()
        }

        searchInput.addTextChangedListener(searchWatcher)

        loadContacts()
    }

    override fun onDestroy() {
        searchInput.removeTextChangedListener(searchWatcher)
        super.onDestroy()
    }

    private fun loadContacts() {
        progressView.isVisible = true
        emptyView.isVisible = false
        errorView.isVisible = false

        lifecycleScope.launch {
            val userId = (application as RinglyApp).sessionManager.userId
            val locals = snapshotStorage.load().entries
            val poolResult = if (userId != null) {
                repository.listPoolContacts(userId)
            } else {
                Result.success(com.ringly.app.data.models.ListPoolContactsResponse())
            }
            val pool = poolResult.getOrNull()?.contacts.orEmpty()
            loadFailed = poolResult.isFailure && locals.isEmpty()
            allContacts = MergedContactList.build(locals, pool)
            applyFilter()
        }
    }

    private fun applyFilter() {
        val query = searchInput.text.toString()
        val source = when (filterGroup.checkedButtonId) {
            R.id.filter_local -> ContactSource.LOCAL
            R.id.filter_pool -> ContactSource.POOL
            else -> null
        }
        val filtered = MergedContactList.filter(allContacts, query, source)
        adapter.submit(filtered)

        progressView.isVisible = false
        val showError = loadFailed && filtered.isEmpty()
        val showEmpty = !showError && filtered.isEmpty()
        errorView.isVisible = showError
        emptyView.isVisible = showEmpty
        recycler.isVisible = !showError && !showEmpty
    }

    private fun onContactClick(contact: MergedContact) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.number}"))
        startActivity(intent)
    }
}