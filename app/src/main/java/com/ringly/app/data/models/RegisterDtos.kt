package com.ringly.app.data.models

data class RegisterRequest(
    val deviceId: String,
    val name: String
)

data class RegisterResponse(
    val userId: String
)