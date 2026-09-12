package com.ringly.app.data.models

data class UploadRequest(
    val dataUrl: String
)

data class UploadResponse(
    val photoUrl: String,
    val photoPublicId: String
)