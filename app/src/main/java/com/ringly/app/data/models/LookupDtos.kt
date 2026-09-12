package com.ringly.app.data.models

data class LookupMatch(
    val name: String,
    val photoUrl: String? = null,
    val ownerName: String
)

data class LookupResponse(
    val found: Boolean,
    val matches: List<LookupMatch> = emptyList()
)