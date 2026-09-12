package com.ringly.app.sync

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract

class ContactChangeObserver(
    private val onContactsChanged: () -> Unit,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS
) {

    private val handler = Handler(Looper.getMainLooper())
    private var observer: ContentObserver? = null
    private var registered = false
    private var pending = false

    fun register(context: Context) {
        if (registered) return
        val contentObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                notifyDebounced()
            }
        }
        context.contentResolver.registerContentObserver(
            ContactsContract.Contacts.CONTENT_URI,
            true,
            contentObserver
        )
        observer = contentObserver
        registered = true
    }

    fun unregister(context: Context) {
        observer?.let { context.contentResolver.unregisterContentObserver(it) }
        observer = null
        registered = false
        pending = false
    }

    private fun notifyDebounced() {
        if (pending) return
        pending = true
        handler.postDelayed(
            {
                pending = false
                onContactsChanged()
            },
            debounceMillis
        )
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 2000L
    }
}