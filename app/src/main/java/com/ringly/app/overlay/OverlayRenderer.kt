package com.ringly.app.overlay

interface OverlayRenderer {
    fun show(card: CallerIdCard)
    fun dismiss()
}