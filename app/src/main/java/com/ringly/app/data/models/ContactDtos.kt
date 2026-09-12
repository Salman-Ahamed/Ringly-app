package com.ringly.app.data.models

import com.google.gson.annotations.SerializedName

data class SyncContact(
    val number: String,
    val name: String,
    val photoUrl: String? = null,
    val photoPublicId: String? = null
)

data class SyncRequest(
    val userId: String,
    val contacts: List<SyncContact>
)

data class SyncResponse(
    val synced: Int,
    val contacts: List<ContactDto>
)

data class ContactDto(
    @SerializedName("_id")
    val id: String,
    val number: String,
    val name: String,
    val photoUrl: String? = null,
    val photoPublicId: String? = null,
    val ownerId: String? = null
)

data class ListContactsResponse(
    val contacts: List<ContactDto>
)