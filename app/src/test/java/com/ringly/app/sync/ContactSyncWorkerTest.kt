package com.ringly.app.sync

import com.ringly.app.data.ApiService
import com.ringly.app.data.repository.ContactRepository
import com.ringly.app.data.repository.UserRepository
import com.ringly.app.data.session.InMemoryUserSessionStorage
import com.ringly.app.data.session.SessionManager
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class InMemorySyncSnapshotStorage : SyncSnapshotStorage {

    var snapshot: SyncSnapshot = SyncSnapshot.EMPTY

    override fun load(): SyncSnapshot = snapshot

    override fun save(snapshot: SyncSnapshot) {
        this.snapshot = snapshot
    }

    override fun clear() {
        snapshot = SyncSnapshot.EMPTY
    }
}

class FakeContactReader(
    private val device: List<ContactReadCandidate>,
    private val encodedPhotos: Map<String, String> = emptyMap()
) : ContactReader {

    override fun readAll(): List<ContactReadCandidate> = device

    override fun readEncodedPhoto(contactId: String): String? = encodedPhotos[contactId]
}

class ContactSyncWorkerTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: ContactRepository
    private lateinit var storage: InMemorySyncSnapshotStorage
    private lateinit var sessionStorage: InMemoryUserSessionStorage
    private lateinit var session: SessionManager
    private val now = 1000L

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
        storage = InMemorySyncSnapshotStorage()
        sessionStorage = InMemoryUserSessionStorage().apply { userId = "u1" }
        session = SessionManager(sessionStorage, UserRepository(api))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun engine(
        device: List<ContactReadCandidate>,
        encodedPhotos: Map<String, String> = emptyMap(),
        hasPermission: () -> Boolean = { true }
    ): ContactSyncEngine {
        return ContactSyncEngine(
            snapshotStorage = storage,
            reader = FakeContactReader(device, encodedPhotos),
            repository = repository,
            sessionManager = session,
            deviceIdProvider = { "dev-1" },
            defaultName = "Tester",
            hasPermission = hasPermission,
            nowProvider = { now }
        )
    }

    private fun candidate(number: String, name: String, contactId: String, photoHash: String? = null) =
        ContactReadCandidate(number, contactId, name, photoHash)

    private fun entry(number: String, name: String, photoHash: String?, serverId: String?) =
        SyncEntry(number, name, photoHash, serverContactId = serverId)

    private fun contactJson(
        id: String,
        number: String,
        name: String,
        photoUrl: String?,
        photoPublicId: String?
    ): String {
        val url = photoUrl?.let { "\"$it\"" } ?: "null"
        val publicId = photoPublicId?.let { "\"$it\"" } ?: "null"
        return """{"_id":"$id","number":"$number","name":"$name","photoUrl":$url,"photoPublicId":$publicId,"ownerId":"u1"}"""
    }

    private fun listResponse(vararg contacts: String) =
        MockResponse().setResponseCode(200).setBody("{\"contacts\":[${contacts.joinToString(",")}]}")

    private fun takenRequests(): List<RecordedRequest> {
        val requests = mutableListOf<RecordedRequest>()
        while (true) {
            val request = server.takeRequest(0, java.util.concurrent.TimeUnit.MILLISECONDS)
                ?: break
            requests.add(request)
        }
        return requests
    }

    @Test
    fun `missing permission skips sync`() = runBlocking {
        val result = engine(
            device = listOf(candidate("+8801712345678", "Rahim", "ca")),
            hasPermission = { false }
        ).run()

        assertEquals(SyncOutcome.SUCCESS, result)
        assertEquals(0, server.requestCount)
        assertTrue(storage.snapshot.entries.isEmpty())
    }

    @Test
    fun `first sync uploads photos and persists snapshot`() = runBlocking {
        val device = listOf(
            candidate("+8801712345678", "Rahim", "ca", "hash-a"),
            candidate("+8801812345678", "Karim", "cb")
        )
        server.enqueue(listResponse())
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"photoUrl":"https://cdn/1.jpg","photoPublicId":"ringly/1"}""")
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                "{\"synced\":2,\"contacts\":[" +
                    contactJson("s1", "+8801712345678", "Rahim", "https://cdn/1.jpg", "ringly/1") +
                    "," +
                    contactJson("s2", "+8801812345678", "Karim", null, null) +
                    "]}"
            )
        )

        val result = engine(device, encodedPhotos = mapOf("ca" to "data:image/jpeg;base64,AAA")).run()

        assertEquals(SyncOutcome.SUCCESS, result)
        val requests = takenRequests()
        assertEquals(3, requests.size)
        assertTrue(requests[1].body.readUtf8()!!.contains("data:image/jpeg;base64,AAA"))
        val syncBody = requests[2].body.readUtf8()!!
        assertTrue(syncBody.contains("https://cdn/1.jpg"))
        assertTrue(syncBody.contains("ringly/1"))
        assertEquals(2, storage.snapshot.entries.size)
        assertEquals("s1", storage.snapshot.entries["+8801712345678"]?.serverContactId)
        assertEquals("https://cdn/1.jpg", storage.snapshot.entries["+8801712345678"]?.photoUrl)
        assertEquals("hash-a", storage.snapshot.entries["+8801712345678"]?.photoHash)
        assertEquals("s2", storage.snapshot.entries["+8801812345678"]?.serverContactId)
        assertEquals(now, storage.snapshot.lastSyncAt)
    }

    @Test
    fun `unchanged sync only refreshes server list`() = runBlocking {
        storage.snapshot = SyncSnapshot(
            mapOf(
                "+8801712345678" to SyncEntry(
                    number = "+8801712345678",
                    name = "Rahim",
                    photoHash = "hash-a",
                    photoUrl = "https://cdn/1.jpg",
                    photoPublicId = "ringly/1",
                    serverContactId = "s1"
                )
            ),
            1L
        )
        val device = listOf(candidate("+8801712345678", "Rahim", "ca", "hash-a"))
        server.enqueue(
            listResponse(contactJson("s1", "+8801712345678", "Rahim", "https://cdn/1.jpg", "ringly/1"))
        )

        val result = engine(device).run()

        assertEquals(SyncOutcome.SUCCESS, result)
        val requests = takenRequests()
        assertEquals(1, requests.size)
        assertEquals("/contacts?userId=u1", requests[0].path)
        assertEquals("s1", storage.snapshot.entries["+8801712345678"]?.serverContactId)
    }

    @Test
    fun `removed contact is deleted from server and pruned from snapshot`() = runBlocking {
        storage.snapshot = SyncSnapshot(
            mapOf("+8801712345678" to entry("+8801712345678", "Rahim", "hash-a", "s1")),
            1L
        )
        server.enqueue(listResponse())
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"success":true,"deletedId":"s1"}""")
        )

        val result = engine(emptyList()).run()

        assertEquals(SyncOutcome.SUCCESS, result)
        val requests = takenRequests()
        assertEquals(2, requests.size)
        assertEquals("/contacts?userId=u1", requests[0].path)
        assertEquals("/contacts/s1?userId=u1", requests[1].path)
        assertTrue(storage.snapshot.entries.isEmpty())
    }

    @Test
    fun `name change reuses existing photo`() = runBlocking {
        storage.snapshot = SyncSnapshot(
            mapOf(
                "+8801712345678" to SyncEntry(
                    number = "+8801712345678",
                    name = "Rahim",
                    photoHash = "hash-a",
                    photoUrl = "https://cdn/1.jpg",
                    photoPublicId = "ringly/1",
                    serverContactId = "s1"
                )
            ),
            1L
        )
        val device = listOf(candidate("+8801712345678", "Rahim Uddin", "ca", "hash-a"))
        server.enqueue(
            listResponse(contactJson("s1", "+8801712345678", "Rahim", "https://cdn/1.jpg", "ringly/1"))
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                "{\"synced\":1,\"contacts\":[" +
                    contactJson("s1", "+8801712345678", "Rahim Uddin", "https://cdn/1.jpg", "ringly/1") +
                    "]}"
            )
        )

        val result = engine(device).run()

        assertEquals(SyncOutcome.SUCCESS, result)
        val requests = takenRequests()
        assertEquals(2, requests.size)
        val syncBody = requests[1].body.readUtf8()!!
        assertTrue(syncBody.contains("Rahim Uddin"))
        assertTrue(syncBody.contains("https://cdn/1.jpg"))
        assertTrue(syncBody.contains("ringly/1"))
        assertEquals("Rahim Uddin", storage.snapshot.entries["+8801712345678"]?.name)
    }

    @Test
    fun `server list failure falls back to snapshot and still syncs`() = runBlocking {
        val device = listOf(candidate("+8801712345678", "Rahim", "ca"))
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"boom"}"""))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                "{\"synced\":1,\"contacts\":[" +
                    contactJson("s1", "+8801712345678", "Rahim", null, null) +
                    "]}"
            )
        )

        val result = engine(device).run()

        assertEquals(SyncOutcome.SUCCESS, result)
        assertEquals(2, server.requestCount)
        assertEquals("s1", storage.snapshot.entries["+8801712345678"]?.serverContactId)
    }

    @Test
    fun `sync api failure signals retry`() = runBlocking {
        val device = listOf(candidate("+8801712345678", "Rahim", "ca"))
        server.enqueue(listResponse())
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"boom"}"""))

        val result = engine(device).run()

        assertEquals(SyncOutcome.RETRY, result)
    }

    @Test
    fun `unregistered device registers before syncing`() = runBlocking {
        sessionStorage.clear()
        sessionStorage.userId = null
        server.enqueue(
            MockResponse().setResponseCode(201).setBody("""{"userId":"u1"}""")
        )
        server.enqueue(listResponse())

        val result = engine(emptyList()).run()

        assertEquals(SyncOutcome.SUCCESS, result)
        val requests = takenRequests()
        assertEquals(2, requests.size)
        assertEquals("/users/register", requests[0].path)
        assertEquals("u1", session.userId)
    }

    @Test
    fun `photo change uploads new photo and syncs new refs`() = runBlocking {
        storage.snapshot = SyncSnapshot(
            mapOf(
                "+8801712345678" to SyncEntry(
                    number = "+8801712345678",
                    name = "Rahim",
                    photoHash = "old-hash",
                    photoUrl = "https://cdn/old.jpg",
                    photoPublicId = "ringly/old",
                    serverContactId = "s1"
                )
            ),
            1L
        )
        val device = listOf(candidate("+8801712345678", "Rahim", "ca", "new-hash"))
        server.enqueue(
            listResponse(contactJson("s1", "+8801712345678", "Rahim", "https://cdn/old.jpg", "ringly/old"))
        )
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"photoUrl":"https://cdn/new.jpg","photoPublicId":"ringly/new"}""")
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                "{\"synced\":1,\"contacts\":[" +
                    contactJson("s1", "+8801712345678", "Rahim", "https://cdn/new.jpg", "ringly/new") +
                    "]}"
            )
        )

        val result = engine(device, encodedPhotos = mapOf("ca" to "data:image/jpeg;base64,BBB")).run()

        assertEquals(SyncOutcome.SUCCESS, result)
        val requests = takenRequests()
        assertEquals(3, requests.size)
        assertTrue(requests[1].body.readUtf8()!!.contains("BBB"))
        assertTrue(requests[2].body.readUtf8()!!.contains("ringly/new"))
        assertEquals("https://cdn/new.jpg", storage.snapshot.entries["+8801712345678"]?.photoUrl)
        assertEquals("new-hash", storage.snapshot.entries["+8801712345678"]?.photoHash)
        assertEquals("s1", storage.snapshot.entries["+8801712345678"]?.serverContactId)
    }

    @Test
    fun `phantom server contacts are cleaned up`() = runBlocking {
        server.enqueue(
            listResponse(contactJson("s9", "+8801912345678", "Orphan", null, null))
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"success":true,"deletedId":"s9"}""")
        )

        val result = engine(emptyList()).run()

        assertEquals(SyncOutcome.SUCCESS, result)
        val requests = takenRequests()
        assertEquals(2, requests.size)
        assertEquals("/contacts/s9?userId=u1", requests[1].path)
        assertTrue(storage.snapshot.entries.isEmpty())
    }

    @Test
    fun `rejected photo upload keeps existing photo on server`() = runBlocking {
        storage.snapshot = SyncSnapshot(
            mapOf(
                "+8801712345678" to SyncEntry(
                    number = "+8801712345678",
                    name = "Rahim",
                    photoHash = "old-hash",
                    photoUrl = "https://cdn/old.jpg",
                    photoPublicId = "ringly/old",
                    serverContactId = "s1"
                )
            ),
            1L
        )
        val device = listOf(candidate("+8801712345678", "Rahim", "ca", "new-hash"))
        server.enqueue(
            listResponse(contactJson("s1", "+8801712345678", "Rahim", "https://cdn/old.jpg", "ringly/old"))
        )
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"Image is too large"}"""))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                "{\"synced\":1,\"contacts\":[" +
                    contactJson("s1", "+8801712345678", "Rahim", "https://cdn/old.jpg", "ringly/old") +
                    "]}"
            )
        )

        val result = engine(device, encodedPhotos = mapOf("ca" to "data:image/jpeg;base64,XXX")).run()

        assertEquals(SyncOutcome.SUCCESS, result)
        val requests = takenRequests()
        assertEquals(3, requests.size)
        assertEquals("/photos/upload", requests[1].path)
        val syncBody = requests[2].body.readUtf8()!!
        assertTrue(syncBody.contains("https://cdn/old.jpg"))
        assertTrue(syncBody.contains("ringly/old"))
        assertEquals("old-hash", storage.snapshot.entries["+8801712345678"]?.photoHash)
    }

    @Test
    fun `partial photo upload failure only syncs successfully uploaded photos`() = runBlocking {
        val device = listOf(
            candidate("+8801712345678", "Rahim", "ca", "hash-a"),
            candidate("+8801812345678", "Karim", "cb", "hash-b")
        )
        server.enqueue(listResponse())
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"photoUrl":"https://cdn/a.jpg","photoPublicId":"ringly/a"}""")
        )
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"Image is too large"}"""))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                "{\"synced\":2,\"contacts\":[" +
                    contactJson("s1", "+8801712345678", "Rahim", "https://cdn/a.jpg", "ringly/a") +
                    "," +
                    contactJson("s2", "+8801812345678", "Karim", null, null) +
                    "]}"
            )
        )

        val result = engine(
            device,
            encodedPhotos = mapOf("ca" to "data:image/jpeg;base64,A", "cb" to "data:image/jpeg;base64,B")
        ).run()

        assertEquals(SyncOutcome.SUCCESS, result)
        val requests = takenRequests()
        assertEquals(4, requests.size)
        val syncBody = requests[3].body.readUtf8()!!
        assertTrue(syncBody.contains("https://cdn/a.jpg"))
        assertTrue(syncBody.contains("ringly/a"))
        assertEquals("hash-a", storage.snapshot.entries["+8801712345678"]?.photoHash)
        assertNull(storage.snapshot.entries["+8801812345678"]?.photoHash)
    }

    @Test
    fun `valid photo is not uploaded twice for same contact`() = runBlocking {
        val device = listOf(
            candidate("+8801712345678", "Rahim", "ca", "hash-a"),
            candidate("+8801812345678", "Rahim", "ca", "hash-a")
        )
        server.enqueue(listResponse())
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"photoUrl":"https://cdn/a.jpg","photoPublicId":"ringly/a"}""")
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                "{\"synced\":2,\"contacts\":[" +
                    contactJson("s1", "+8801712345678", "Rahim", "https://cdn/a.jpg", "ringly/a") +
                    "," +
                    contactJson("s2", "+8801812345678", "Rahim", "https://cdn/a.jpg", "ringly/a") +
                    "]}"
            )
        )

        val result = engine(
            device,
            encodedPhotos = mapOf("ca" to "data:image/jpeg;base64,SHARED")
        ).run()

        assertEquals(SyncOutcome.SUCCESS, result)
        val requests = takenRequests()
        assertEquals(3, requests.size)
        val uploadPaths = requests.count { it.path == "/photos/upload" }
        assertEquals(1, uploadPaths)
        val syncBody = requests[2].body.readUtf8()!!
        assertTrue(syncBody.contains("https://cdn/a.jpg"))
        assertTrue(syncBody.contains("ringly/a"))
    }

    @Test
    fun `oversized photo data url is skipped and old refs preserved`() = runBlocking {
        storage.snapshot = SyncSnapshot(
            mapOf(
                "+8801712345678" to SyncEntry(
                    number = "+8801712345678",
                    name = "Rahim",
                    photoHash = "old-hash",
                    photoUrl = "https://cdn/old.jpg",
                    photoPublicId = "ringly/old",
                    serverContactId = "s1"
                )
            ),
            1L
        )
        val device = listOf(candidate("+8801712345678", "Rahim", "ca", "new-hash"))
        server.enqueue(
            listResponse(contactJson("s1", "+8801712345678", "Rahim", "https://cdn/old.jpg", "ringly/old"))
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                "{\"synced\":1,\"contacts\":[" +
                    contactJson("s1", "+8801712345678", "Rahim", "https://cdn/old.jpg", "ringly/old") +
                    "]}"
            )
        )

        val result = engine(
            device,
            encodedPhotos = mapOf("ca" to "X".repeat(3_500_001))
        ).run()

        assertEquals(SyncOutcome.SUCCESS, result)
        val requests = takenRequests()
        assertEquals(2, requests.size)
        assertTrue(requests.none { it.path == "/photos/upload" })
        val syncBody = requests[1].body.readUtf8()!!
        assertTrue(syncBody.contains("https://cdn/old.jpg"))
        assertEquals("old-hash", storage.snapshot.entries["+8801712345678"]?.photoHash)
    }

    @Test
    fun `delete failure is swallowed but snapshot is pruned`() = runBlocking {
        storage.snapshot = SyncSnapshot(
            mapOf("+8801712345678" to entry("+8801712345678", "Rahim", "hash-a", "s1")),
            1L
        )
        server.enqueue(listResponse())
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"boom"}"""))

        val result = engine(emptyList()).run()

        assertEquals(SyncOutcome.SUCCESS, result)
        assertEquals(2, server.requestCount)
        assertTrue(storage.snapshot.entries.isEmpty())
    }

    @Test
    fun `sync 404 maps to failure`() = runBlocking {
        val device = listOf(candidate("+8801712345678", "Rahim", "ca"))
        server.enqueue(listResponse())
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"User not found"}"""))

        val result = engine(device).run()

        assertEquals(SyncOutcome.FAILURE, result)
    }
}