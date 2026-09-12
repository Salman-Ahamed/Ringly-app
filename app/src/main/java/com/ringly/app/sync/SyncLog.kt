package com.ringly.app.sync

import android.util.Log

internal object SyncLog {
    fun d(tag: String, message: String) = runCatching { Log.d(tag, message) }
    fun d(tag: String, message: String, throwable: Throwable?) =
        runCatching { Log.d(tag, message, throwable) }
    fun w(tag: String, message: String) = runCatching { Log.w(tag, message) }
    fun w(tag: String, message: String, throwable: Throwable?) =
        runCatching { Log.w(tag, message, throwable) }
}