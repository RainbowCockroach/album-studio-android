package com.rainbowcockroach.albumstudio.toprint

import com.rainbowcockroach.albumstudio.toprint.data.PhotoApi
import com.rainbowcockroach.albumstudio.toprint.data.UploadOutcome
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class PhotoApiTest {

    private lateinit var server: MockWebServer
    private val api = PhotoApi()
    private lateinit var photo: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        photo = File.createTempFile("photo", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
    }

    @After
    fun tearDown() {
        server.shutdown()
        photo.delete()
    }

    private fun upload(capturedAt: String? = "2026-05-30T18:21:09"): UploadOutcome =
        api.upload(
            baseUrl = server.url("/").toString(),
            token = "secret",
            file = photo,
            fileName = "photo.jpg",
            capturedAt = capturedAt,
        )

    @Test
    fun success_newPhoto_parsesHashAndExistedFalse() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"hash":"abc123","existed":false,"size":4,"uploadedAt":"2026-06-11T00:00:00","capturedAt":"2026-05-30T18:21:09"}"""
            )
        )
        val outcome = upload()
        assertTrue(outcome is UploadOutcome.Success)
        outcome as UploadOutcome.Success
        assertEquals("abc123", outcome.hash)
        assertFalse(outcome.existed)
    }

    @Test
    fun success_existing_parsesExistedTrue() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"hash":"abc123","existed":true,"size":4,"uploadedAt":"2026-06-11T00:00:00","capturedAt":null}"""
            )
        )
        val outcome = upload()
        assertTrue(outcome is UploadOutcome.Success)
        assertTrue((outcome as UploadOutcome.Success).existed)
    }

    @Test
    fun unauthorized_401_isPermanent() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"bad token"}"""))
        val outcome = upload()
        assertTrue(outcome is UploadOutcome.Unauthorized)
    }

    @Test
    fun unsupportedType_415_isPermanent() {
        server.enqueue(MockResponse().setResponseCode(415).setBody("""{"error":"unsupported"}"""))
        val outcome = upload()
        assertTrue(outcome is UploadOutcome.PermanentFailure)
    }

    @Test
    fun serverError_500_isRetryable() {
        server.enqueue(MockResponse().setResponseCode(500))
        val outcome = upload()
        assertTrue(outcome is UploadOutcome.Retryable)
    }

    @Test
    fun capturedAt_includedAsFormField_whenPresent() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"hash":"h","existed":false}"""))
        upload(capturedAt = "2026-05-30T18:21:09")
        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("name=\"capturedAt\""))
        assertTrue(body.contains("2026-05-30T18:21:09"))
        assertTrue(body.contains("name=\"file\""))
    }

    @Test
    fun capturedAt_omitted_whenNull() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"hash":"h","existed":false}"""))
        upload(capturedAt = null)
        val body = server.takeRequest().body.readUtf8()
        assertFalse(body.contains("name=\"capturedAt\""))
        assertTrue(body.contains("name=\"file\""))
    }

    @Test
    fun upload_sendsApiKeyHeader_notBearer() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"hash":"h","existed":false}"""))
        upload()
        val recorded = server.takeRequest()
        assertEquals("secret", recorded.getHeader("x-api-key"))
        assertNull(recorded.getHeader("Authorization"))
    }

    @Test
    fun health_200_isTrue() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok"}"""))
        assertTrue(api.health(server.url("/").toString()))
    }

    @Test
    fun validateToken_200_isTrue_401_isFalse() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"photos":[]}"""))
        assertTrue(api.validateToken(server.url("/").toString(), "secret", "2026-06"))

        server.enqueue(MockResponse().setResponseCode(401))
        assertFalse(api.validateToken(server.url("/").toString(), "bad", "2026-06"))
    }
}
