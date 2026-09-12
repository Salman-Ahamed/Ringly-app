package com.ringly.app.data.repository

import com.ringly.app.data.ApiClient
import com.ringly.app.data.ApiService
import com.ringly.app.data.models.ApiError
import com.ringly.app.data.models.RegisterRequest
import com.ringly.app.data.models.RegisterResponse

class UserRepository(private val api: ApiService = ApiClient.api) {

    suspend fun register(deviceId: String, name: String): Result<RegisterResponse> {
        return try {
            val response = api.register(RegisterRequest(deviceId, name))
            val body = response.body()
            when {
                response.isSuccessful && body != null -> Result.success(body)
                else -> Result.failure(
                    ApiError.from(response) ?: ApiError("Registration failed (HTTP ${response.code()})")
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}