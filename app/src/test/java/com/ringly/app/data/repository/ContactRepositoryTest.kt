package com.ringly.app.data.repository

import com.ringly.app.data.ApiService
import com.ringly.app.data.models.ApiError
import com.ringly.app.data.models.SyncContact
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.HttpURLConnection

class ContactRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: ContactRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
        repository = ContactRepository(api)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `sync returns synced count`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"synced":1,"contacts":[{"_id":"c1","number":"+8801712345678",
                "name":"Rahim","photoUrl":null,"ownerId":"u1"}]}
                """.trimIndent()
            )
        )

        val result = repository.sync(
            "u1",
            listOf(SyncContact(number = "01712345678", name = "Rahim"))
        )

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.synced)
        assertEquals("c1", result.getOrNull()?.contacts?.firstOrNull()?.id)
    }

    @Test
    fun `lookup returns matches`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"found":true,"matches":[{"name":"Rahim Uddin","photoUrl":"https://x/a.jpg",
                "ownerName":"Salman"}]}
                """.trimIndent()
            )
        )

        val result = repository.lookup("+8801712345678")

        assertTrue(result.isSuccess)
        assertEquals("Salman", result.getOrNull()?.matches?.firstOrNull()?.ownerName)
    }

    @Test
    fun `lookup not found returns empty matches`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"found":false,"matches":[]}""")
        )

        val result = repository.lookup("+8801712345678")

        assertTrue(result.isSuccess)
        assertEquals(false, result.getOrNull()?.found)
        assertTrue(result.getOrNull()?.matches?.isEmpty() == true)
    }

    @Test
    fun `listMyContacts returns contacts`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"contacts":[{"_id":"c1","number":"+8801712345678","name":"Rahim",
                "photoUrl":null,"ownerId":"u1"}]}
                """.trimIndent()
            )
        )

        val result = repository.listMyContacts("u1")

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.contacts?.size)
        assertEquals("c1", result.getOrNull()?.contacts?.firstOrNull()?.id)
        assertEquals("u1", result.getOrNull()?.contacts?.firstOrNull()?.ownerId)
    }

    @Test
    fun `listMyContacts maps 404 user missing to ApiError`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_FOUND)
                .setBody("""{"error":"User not found"}""")
        )

        val result = repository.listMyContacts("missing")

        assertTrue(result.isFailure)
        assertEquals("User not found", (result.exceptionOrNull() as? ApiError)?.error)
    }

    @Test
    fun `sync maps 404 user missing to ApiError`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_FOUND)
                .setBody("""{"error":"User not found"}""")
        )

        val result = repository.sync("missing", listOf(SyncContact(number = "01712345678", name = "Rahim")))

        assertTrue(result.isFailure)
        assertEquals("User not found", (result.exceptionOrNull() as? ApiError)?.error)
        assertEquals(HttpURLConnection.HTTP_NOT_FOUND, (result.exceptionOrNull() as? ApiError)?.statusCode)
    }

    @Test
    fun `uploadPhoto returns cloudinary url`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"photoUrl":"https://res.cloudinary.com/x/a.png","photoPublicId":"ringly/a"}"""
            )
        )

        val result = repository.uploadPhoto("data:image/png;base64,abc")

        assertTrue(result.isSuccess)
        assertEquals("https://res.cloudinary.com/x/a.png", result.getOrNull()?.photoUrl)
        assertEquals("ringly/a", result.getOrNull()?.photoPublicId)
    }

    @Test
    fun `deleteContact succeeds`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"success":true,"deletedId":"c1"}""")
        )

        val result = repository.deleteContact("c1", "u1")

        assertTrue(result.isSuccess)
        assertEquals("c1", result.getOrNull()?.deletedId)
    }

    @Test
    fun `deleteContact maps 403 forbidden to ApiError`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(403).setBody("""{"error":"Not authorized to delete this contact"}""")
        )

        val result = repository.deleteContact("c1", "wrong-user")

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as? ApiError
        assertNotNull(error)
        assertEquals("Not authorized to delete this contact", error?.error)
    }

    @Test
    fun `deleteContact surfaces 404 not found`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_FOUND)
                .setBody("""{"error":"Contact not found"}""")
        )

        val result = repository.deleteContact("missing", "u1")

        assertTrue(result.isFailure)
        assertEquals("Contact not found", (result.exceptionOrNull() as? ApiError)?.error)
    }

    @Test
    fun `uploadPhoto maps 413 too large to ApiError`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(413)
                .setBody("""{"error":"Image is too large"}""")
        )

        val result = repository.uploadPhoto("data:image/jpeg;base64,XXXX")

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as? ApiError
        assertNotNull(error)
        assertEquals(413, error?.statusCode)
        assertEquals("Image is too large", error?.error)
    }

    @Test
    fun `listMyContacts maps 400 missing userId to ApiError`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"error":"userId query param missing"}""")
        )

        val result = repository.listMyContacts("")

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as? ApiError
        assertNotNull(error)
        assertEquals(400, error?.statusCode)
        assertEquals("userId query param missing", error?.error)
    }
}