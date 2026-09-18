package com.ringly.app.sms

import android.content.Context
import android.text.format.DateUtils
import android.provider.Telephony

data class SmsMessage(
    val id: Long,
    val threadId: Long,
    val address: String?,
    val body: String,
    val date: Long,
    val type: Int,
    val read: Boolean
)

data class SmsConversation(
    val threadId: Long,
    val address: String?,
    val snippet: String,
    val date: Long,
    val read: Boolean,
    val hasOutgoing: Boolean,
    val messageCount: Int
)

object SmsConversationGroup {

    fun group(rows: List<SmsMessage>): List<SmsConversation> {
        return rows.groupBy { it.threadId }
            .mapNotNull { (threadId, messages) ->
                groupConversation(threadId, messages)
            }
            .sortedByDescending { it.date }
    }

    private fun groupConversation(threadId: Long, messages: List<SmsMessage>): SmsConversation? {
        val latest = messages.maxByOrNull { it.date } ?: return null
        return SmsConversation(
            threadId = threadId,
            address = latest.address,
            snippet = latest.body,
            date = latest.date,
            read = latest.read,
            hasOutgoing = messages.any {
                it.type == Telephony.TextBasedSmsColumns.MESSAGE_TYPE_SENT ||
                    it.type == Telephony.TextBasedSmsColumns.MESSAGE_TYPE_OUTBOX
            },
            messageCount = messages.size
        )
    }
}

fun smsTimeLabel(context: Context, millis: Long): String {
    return DateUtils.getRelativeTimeSpanString(
        millis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()
}