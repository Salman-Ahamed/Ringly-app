package com.ringly.app.data

import com.ringly.app.data.models.DeleteResponse
import com.ringly.app.data.models.LookupResponse
import com.ringly.app.data.models.RegisterRequest
import com.ringly.app.data.models.RegisterResponse
import com.ringly.app.data.models.SyncRequest
import com.ringly.app.data.models.SyncResponse
import com.ringly.app.data.models.UploadRequest
import com.ringly.app.data.models.UploadResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @POST("users/register")
    suspend fun register(@Body body: RegisterRequest): Response<RegisterResponse>

    @POST("photos/upload")
    suspend fun uploadPhoto(@Body body: UploadRequest): Response<UploadResponse>

    @POST("contacts/sync")
    suspend fun syncContacts(@Body body: SyncRequest): Response<SyncResponse>

    @GET("contacts/lookup/{number}")
    suspend fun lookup(@Path("number") number: String): Response<LookupResponse>

    @DELETE("contacts/{contactId}")
    suspend fun deleteContact(
        @Path("contactId") contactId: String,
        @Query("userId") userId: String
    ): Response<DeleteResponse>
}