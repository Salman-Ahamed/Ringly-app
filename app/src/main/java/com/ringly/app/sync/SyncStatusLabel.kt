package com.ringly.app.sync

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object SyncStatusLabel {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private val dateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    fun describe(lastSyncAt: Long, now: Long): String? {
        if (lastSyncAt <= 0L) return null
        val zone = ZoneId.systemDefault()
        val then = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastSyncAt), zone)
        val nowLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone)
        val diffMinutes = Duration.between(then, nowLdt).toMinutes()
        return when {
            diffMinutes < 1L -> "just now"
            diffMinutes < 60L -> "$diffMinutes min ago"
            then.toLocalDate() == nowLdt.toLocalDate() -> "today at ${then.format(timeFormatter)}"
            else -> "${then.format(dateFormatter)} ${then.format(timeFormatter)}"
        }
    }
}