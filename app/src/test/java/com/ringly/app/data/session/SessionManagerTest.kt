package com.ringly.app.data.session

import com.ringly.app.data.ApiService
import com.ringly.app.data.models.ApiError
import com.ringly.app.data.repository.UserRepository
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class InMemoryUserSessionStorage : UserSessionStorage {

    override var userId: String? = null
    override var userName: String? = null

    override fun clear() {
        userId = null
        userName = null
    }
}

class SessionManagerTest {

    private lateinit var server: MockWebServer
    private lateinit var storage: InMemoryUserSessionStorage
    private lateinit var session: SessionManager

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
        storage = InMemoryUserSessionStorage()
        session = SessionManager(storage, UserRepository(api))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `register new user persists userId and name`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody("""{"userId":"user-1"}""")
        )

        val result = session.ensureRegistered("dev-1", "Salman")

        assertTrue(result.isSuccess)
        assertEquals("user-1", result.getOrNull())
        assertEquals("user-1", session.userId)
        assertEquals("Salman", session.userName)
        assertTrue(session.isRegistered)
    }

    @Test
    fun `second ensureRegistered skips API when already registered`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"userId":"user-1"}""")
        )

        session.ensureRegistered("dev-1", "Salman")
        assertEquals(1, server.requestCount)

        val result = session.ensureRegistered("dev-1", "Salman")

        assertTrue(result.isSuccess)
        assertEquals("user-1", result.getOrNull())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `already stored userId is returned without any API call`() = runBlocking {
        storage.userId = "user-stored"
        storage.userName = "Karim"

        val result = session.ensureRegistered("dev-1", "Karim")

        assertTrue(result.isSuccess)
        assertEquals("user-stored", result.getOrNull())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `register failure keeps session unregistered`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody("""{"error":"name is required"}""")
        )

        val result = session.ensureRegistered("dev-1", "")

        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull() as? ApiError)
        assertNull(session.userId)
        assertFalse(session.isRegistered)
    }

    @Test
    fun `clear resets stored session`() = runBlocking {
        storage.userId = "user-1"
        storage.userName = "Salman"

        session.clear()

        assertNull(session.userId)
        assertNull(session.userName)
        assertFalse(session.isRegistered)
    }
}