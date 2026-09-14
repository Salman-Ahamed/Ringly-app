package com.ringly.app.overlay

import com.ringly.app.data.models.LookupMatch

object BestMatchSelector {

    fun selectBest(matches: List<LookupMatch>): LookupMatch? {
        if (matches.isEmpty()) return null
        return matches.firstOrNull { it.photoUrl != null } ?: matches.first()
    }
}
