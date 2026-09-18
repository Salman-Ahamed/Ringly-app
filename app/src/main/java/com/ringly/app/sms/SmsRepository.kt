package com.ringly.app.sms

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.provider.Telephony
import android.telephony.SmsManager
import com.ringly.app.util.PermissionHelper

class SmsRepository(private val context: Context) {

    private val resolver get() = context.contentResolver

    fun hasReadPermission(): Boolean =
        PermissionHelper.hasPermission(context, Manifest.permission.READ_SMS)

    fun loadConversations(): List<SmsConversation> {
        if (!hasReadPermission()) return emptyList()
        return runCatching {
            val rows = mutableListOf<SmsMessage>()
            resolver.query(
                Telephony.Sms.CONTENT_URI,
                PROJECTION,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor -> readMessages(cursor, rows) }
            SmsConversationGroup.group(rows)
        }.getOrDefault(emptyList())
    }

    fun loadThread(threadId: Long): List<SmsMessage> {
        if (!hasReadPermission()) return emptyList()
        return runCatching {
            val rows = mutableListOf<SmsMessage>()
            resolver.query(
                Telephony.Sms.CONTENT_URI,
                PROJECTION,
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                "${Telephony.Sms.DATE} ASC"
            )?.use { cursor -> readMessages(cursor, rows) }
            rows
        }.getOrDefault(emptyList())
    }

    fun markThreadRead(threadId: Long) {
        if (!hasReadPermission()) return
        runCatching {
            val values = ContentValues().apply { put(Telephony.Sms.READ, 1) }
            resolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                arrayOf(threadId.toString())
            )
        }
    }

    fun insertIncoming(address: String?, body: String, date: Long): Boolean {
        return runCatching {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, date)
                put(Telephony.Sms.READ, 0)
                put(Telephony.Sms.TYPE, Telephony.TextBasedSmsColumns.MESSAGE_TYPE_INBOX)
            }
            resolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values) != null
        }.getOrDefault(false)
    }

    fun send(to: String, body: String, subscriptionId: Int = -1): Boolean {
        if (body.isBlank()) return false
        val smsManager = if (subscriptionId >= 0 && android.os.Build.VERSION.SDK_INT >= 24) {
            SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        } else {
            SmsManager.getDefault()
        }
        val sent = runCatching {
            smsManager.sendTextMessage(to, null, body, null, null)
        }.isSuccess
        if (sent) runCatching { insertOutbox(to, body) }
        return sent
    }

    fun registerObserver(observer: ContentObserver) {
        runCatching {
            resolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        }
    }

    fun unregisterObserver(observer: ContentObserver) {
        runCatching { resolver.unregisterContentObserver(observer) }
    }

    private fun readMessages(cursor: android.database.Cursor, into: MutableList<SmsMessage>) {
        val idCol = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
        val threadCol = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
        val addressCol = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
        val bodyCol = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
        val dateCol = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
        val typeCol = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
        val readCol = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)
        while (cursor.moveToNext()) {
            into += SmsMessage(
                id = cursor.getLong(idCol),
                threadId = cursor.getLong(threadCol),
                address = cursor.getString(addressCol),
                body = cursor.getString(bodyCol) ?: "",
                date = cursor.getLong(dateCol),
                type = cursor.getInt(typeCol),
                read = cursor.getInt(readCol) != 0
            )
        }
    }

    private fun insertOutbox(address: String, body: String, date: Long = System.currentTimeMillis()): Boolean {
        return runCatching {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, date)
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.TYPE, Telephony.TextBasedSmsColumns.MESSAGE_TYPE_OUTBOX)
            }
            resolver.insert(Telephony.Sms.Outbox.CONTENT_URI, values) != null
        }.getOrDefault(false)
    }

    private companion object {
        val PROJECTION = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ
        )
    }
}