package com.ringly.app.data.repository

import com.ringly.app.data.ApiService
import com.ringly.app.data.models.ApiError
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class UserRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: UserRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
        repository = UserRepository(api)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `register returns userId on 201`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody("""{"userId":"user-123"}""")
        )

        val result = repository.register("dev-1", "Salman")

        assertTrue(result.isSuccess)
        assertEquals("user-123", result.getOrNull()?.userId)
    }

    @Test
    fun `register returns existing userId on 200`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"userId":"user-existing"}""")
        )

        val result = repository.register("dev-1", "Salman")

        assertTrue(result.isSuccess)
        assertEquals("user-existing", result.getOrNull()?.userId)
    }

    @Test
    fun `register maps ApiError on failed response`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody("""{"error":"name is required"}""")
        )

        val result = repository.register("dev-1", "")

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as? ApiError
        assertNotNull(error)
        assertEquals("name is required", error?.error)
    }

    @Test
    fun `register surfaces network failure`() = runBlocking {
        server.shutdown()

        val result = repository.register("dev-1", "Salman")

        assertTrue(result.isFailure)
        assertFalse(result.exceptionOrNull() is ApiError)
    }
}