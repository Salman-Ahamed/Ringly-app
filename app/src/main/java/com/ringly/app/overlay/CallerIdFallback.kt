package com.ringly.app.overlay

interface CallerIdFallback {
    fun show(card: CallerIdCard)
    fun hide()
}

object NoOpCallerIdFallback : CallerIdFallback {
    override fun show(card: CallerIdCard) = Unit
    override fun hide() = Unit
}