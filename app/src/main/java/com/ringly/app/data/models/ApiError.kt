package com.ringly.app.data.models

import com.google.gson.Gson
import retrofit2.Response

data class ApiError(
    val error: String,
    val details: String? = null,
    val statusCode: Int? = null
) : Exception(error) {

    companion object {
        private val gson = Gson()

        private data class ErrorBody(
            val error: String? = null,
            val details: String? = null
        )

        fun from(response: Response<*>): ApiError? {
            val body = response.errorBody()?.string() ?: return null
            return try {
                val parsed = gson.fromJson(body, ErrorBody::class.java)
                val message = parsed?.error?.takeIf { it.isNotBlank() }
                when {
                    message != null -> ApiError(message, parsed.details, response.code())
                    body.isBlank() -> ApiError("Request failed (HTTP ${response.code()})", null, response.code())
                    else -> ApiError("Request failed (HTTP ${response.code()})", body.take(300), response.code())
                }
            } catch (e: Exception) {
                ApiError("Unexpected error (HTTP ${response.code()})", body.take(300), response.code())
            }
        }
    }
}