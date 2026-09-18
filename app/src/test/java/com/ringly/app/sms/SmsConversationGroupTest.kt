package com.ringly.app.sms

import android.provider.Telephony
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsConversationGroupTest {

    private fun message(
        id: Long,
        threadId: Long,
        address: String,
        body: String,
        date: Long,
        type: Int = Telephony.TextBasedSmsColumns.MESSAGE_TYPE_INBOX,
        read: Boolean = true
    ) = SmsMessage(
        id = id,
        threadId = threadId,
        address = address,
        body = body,
        date = date,
        type = type,
        read = read
    )

    @Test
    fun `groups all rows of a thread into one conversation`() {
        val rows = listOf(
            message(1, 10, "8801", "hi", 1000),
            message(2, 10, "8801", "hello", 2000),
            message(3, 10, "8801", "how are you", 3000)
        )
        val conversations = SmsConversationGroup.group(rows)
        assertEquals(1, conversations.size)
        assertEquals(10L, conversations[0].threadId)
        assertEquals(3, conversations[0].messageCount)
        assertEquals("how are you", conversations[0].snippet)
    }

    @Test
    fun `latest message drives snippet and date`() {
        val rows = listOf(
            message(1, 10, "8801", "old", 1000),
            message(2, 10, "8801", "new", 5000)
        )
        val conversations = SmsConversationGroup.group(rows)
        assertEquals("new", conversations[0].snippet)
        assertEquals(5000L, conversations[0].date)
    }

    @Test
    fun `conversations sorted by latest date descending`() {
        val rows = listOf(
            message(1, 10, "8801", "old thread", 1000),
            message(2, 30, "8802", "newer thread", 9000),
            message(3, 20, "8803", "middle thread", 5000)
        )
        val conversations = SmsConversationGroup.group(rows)
        assertEquals(listOf(30L, 20L, 10L), conversations.map { it.threadId })
    }

    @Test
    fun `flags outgoing conversations`() {
        val rows = listOf(
            message(1, 10, "8801", "incoming", 1000),
            message(
                2, 10, "8801", "outgoing",
                2000, type = Telephony.TextBasedSmsColumns.MESSAGE_TYPE_SENT
            )
        )
        val conversations = SmsConversationGroup.group(rows)
        assertTrue(conversations[0].hasOutgoing)
    }

    @Test
    fun `inbox-only conversation is not outgoing`() {
        val rows = listOf(
            message(1, 10, "8801", "incoming", 1000)
        )
        val conversations = SmsConversationGroup.group(rows)
        assertFalse(conversations[0].hasOutgoing)
    }

    @Test
    fun `unread conversation is flagged from latest message`() {
        val rows = listOf(
            message(1, 10, "8801", "read", 1000, read = true),
            message(2, 10, "8801", "unread", 2000, read = false)
        )
        val conversations = SmsConversationGroup.group(rows)
        assertFalse(conversations[0].read)
    }

    @Test
    fun `empty input yields empty output`() {
        assertEquals(0, SmsConversationGroup.group(emptyList()).size)
    }

    @Test
    fun `address of latest message wins`() {
        val rows = listOf(
            message(1, 10, "8801", "from primary", 1000),
            message(2, 10, "8802", "from secondary", 2000)
        )
        val conversations = SmsConversationGroup.group(rows)
        assertEquals("8802", conversations[0].address)
    }
}