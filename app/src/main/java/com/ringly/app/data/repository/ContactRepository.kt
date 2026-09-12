package com.ringly.app.data.repository

import com.ringly.app.data.ApiClient
import com.ringly.app.data.ApiService
import com.ringly.app.data.models.ApiError
import com.ringly.app.data.models.DeleteResponse
import com.ringly.app.data.models.LookupResponse
import com.ringly.app.data.models.SyncContact
import com.ringly.app.data.models.SyncRequest
import com.ringly.app.data.models.SyncResponse
import com.ringly.app.data.models.UploadRequest
import com.ringly.app.data.models.UploadResponse

class ContactRepository(private val api: ApiService = ApiClient.api) {

    suspend fun sync(userId: String, contacts: List<SyncContact>): Result<SyncResponse> {
        return try {
            val response = api.syncContacts(SyncRequest(userId, contacts))
            val body = response.body()
            when {
                response.isSuccessful && body != null -> Result.success(body)
                else -> Result.failure(
                    ApiError.from(response) ?: ApiError("Sync failed (HTTP ${response.code()})")
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun lookup(number: String): Result<LookupResponse> {
        return try {
            val response = api.lookup(number)
            val body = response.body()
            when {
                response.isSuccessful && body != null -> Result.success(body)
                else -> Result.failure(
                    ApiError.from(response) ?: ApiError("Lookup failed (HTTP ${response.code()})")
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadPhoto(dataUrl: String): Result<UploadResponse> {
        return try {
            val response = api.uploadPhoto(UploadRequest(dataUrl))
            val body = response.body()
            when {
                response.isSuccessful && body != null -> Result.success(body)
                else -> Result.failure(
                    ApiError.from(response) ?: ApiError("Photo upload failed (HTTP ${response.code()})")
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteContact(contactId: String, userId: String): Result<DeleteResponse> {
        return try {
            val response = api.deleteContact(contactId, userId)
            val body = response.body()
            when {
                response.isSuccessful && body != null -> Result.success(body)
                else -> Result.failure(
                    ApiError.from(response) ?: ApiError("Delete failed (HTTP ${response.code()})")
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}