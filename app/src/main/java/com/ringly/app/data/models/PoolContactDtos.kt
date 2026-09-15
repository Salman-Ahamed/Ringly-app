package com.ringly.app.data.models

data class PoolContact(
    val number: String,
    val name: String,
    val photoUrl: String? = null,
    val ownerName: String
)

data class ListPoolContactsResponse(
    val contacts: List<PoolContact> = emptyList()
)