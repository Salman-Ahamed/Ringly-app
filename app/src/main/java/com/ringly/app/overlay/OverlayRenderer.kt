package com.ringly.app.overlay

import com.ringly.app.data.models.LookupMatch

interface OverlayRenderer {
    fun show(match: LookupMatch, number: String)
    fun dismiss()
}