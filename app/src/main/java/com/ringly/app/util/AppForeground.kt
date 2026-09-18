package com.ringly.app.util

import java.util.concurrent.atomic.AtomicBoolean

object AppForeground {
    private val foreground = AtomicBoolean(false)

    fun enterForeground() = foreground.set(true)

    fun exitForeground() = foreground.set(false)

    fun isForeground(): Boolean = foreground.get()
}