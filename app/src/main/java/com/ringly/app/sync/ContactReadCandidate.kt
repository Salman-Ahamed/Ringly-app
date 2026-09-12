package com.ringly.app.sync

data class ContactReadCandidate(
    val number: String,
    val contactId: String,
    val name: String,
    val photoHash: String? = null
)

data class RawContactRow(
    val contactId: String,
    val name: String,
    val number: String
)