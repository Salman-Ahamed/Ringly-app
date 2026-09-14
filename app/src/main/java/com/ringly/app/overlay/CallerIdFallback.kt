package com.ringly.app.overlay

import com.ringly.app.data.models.LookupMatch

interface CallerIdFallback {
    fun showUnknown(number: String)
    fun showMatch(match: LookupMatch, number: String)
    fun hide()
}

object NoOpCallerIdFallback : CallerIdFallback {
    override fun showUnknown(number: String) = Unit
    override fun showMatch(match: LookupMatch, number: String) = Unit
    override fun hide() = Unit
}