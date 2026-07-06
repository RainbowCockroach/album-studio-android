package com.rainbowcockroach.albumstudio.toprint.data

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Result of a `POST /photos` attempt, already classified for WorkManager. */
sealed interface UploadOutcome {
    /** 200 — both `existed` true and false are success. Hash always present. */
    data class Success(val hash: String, val existed: Boolean) : UploadOutcome

    /** 401 — bad/missing token. Permanent: do NOT retry, surface "check token". */
    data class Unauthorized(val message: String) : UploadOutcome

    /** 415 or other 4xx — the request itself is wrong; retrying won't help. */
    data class PermanentFailure(val message: String) : UploadOutcome

    /** 5xx or network/IO error — transient; WorkManager should retry with backoff. */
    data class Retryable(val message: String) : UploadOutcome
}

/**
 * Thin OkHttp wrapper around the two-and-a-bit endpoints this app uses. No Retrofit —
 * the contract is tiny. The [client] is shared (see [com.rainbowcockroach.albumstudio.toprint.ToPrintApp]).
 */
class PhotoApi(private val client: OkHttpClient = defaultClient()) {

    /** `GET /health` (no auth) → true iff 200. */
    fun health(baseUrl: String): Boolean = try {
        val request = Request.Builder().url("${baseUrl.trimEnd('/')}/health").get().build()
        client.newCall(request).execute().use { it.isSuccessful }
    } catch (_: Exception) {
        false
    }

    /**
     * Token check: `GET /photos?since=<month>` with auth → expect 200. A 401 means the
     * token is wrong; anything else (network down, 5xx) we report as failure too.
     */
    fun validateToken(baseUrl: String, token: String, sinceMonth: String): Boolean = try {
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/photos?since=$sinceMonth")
            .header(HEADER_API_KEY, token)
            .get()
            .build()
        client.newCall(request).execute().use { it.isSuccessful }
    } catch (_: Exception) {
        false
    }

    /** `HEAD /photos/:hash` → true iff 200 (already on the server). */
    fun existsByHash(baseUrl: String, token: String, hash: String): Boolean = try {
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/photos/$hash")
            .header(HEADER_API_KEY, token)
            .head()
            .build()
        client.newCall(request).execute().use { it.isSuccessful }
    } catch (_: Exception) {
        false
    }

    /** `POST /photos` multipart upload. [capturedAt] is omitted from the body when null. */
    fun upload(
        baseUrl: String,
        token: String,
        file: File,
        fileName: String,
        capturedAt: String?,
    ): UploadOutcome {
        val bodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                fileName,
                file.asRequestBody(mediaTypeFor(fileName)),
            )
        if (capturedAt != null) {
            bodyBuilder.addFormDataPart("capturedAt", capturedAt)
        }

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/photos")
            .header(HEADER_API_KEY, token)
            .post(bodyBuilder.build())
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> parseSuccess(bodyStr)
                    response.code == 401 -> UploadOutcome.Unauthorized(errorMessage(bodyStr, "Unauthorized — check token"))
                    response.code in 400..499 -> UploadOutcome.PermanentFailure(errorMessage(bodyStr, "Rejected (HTTP ${response.code})"))
                    else -> UploadOutcome.Retryable("Server error (HTTP ${response.code})")
                }
            }
        } catch (e: Exception) {
            UploadOutcome.Retryable(e.message ?: "Network error")
        }
    }

    private fun parseSuccess(body: String): UploadOutcome = try {
        val json = JSONObject(body)
        val hash = json.getString("hash")
        val existed = json.optBoolean("existed", false)
        UploadOutcome.Success(hash, existed)
    } catch (e: Exception) {
        // 200 but unparseable body — treat as retryable; a healthy server returns JSON.
        UploadOutcome.Retryable("Malformed success response")
    }

    private fun errorMessage(body: String, fallback: String): String = try {
        JSONObject(body).optString("error").takeIf { it.isNotBlank() } ?: fallback
    } catch (_: Exception) {
        fallback
    }

    private fun mediaTypeFor(fileName: String) = when (fileName.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "heic", "heif" -> "image/heic"
        "webp" -> "image/webp"
        "dng" -> "image/x-adobe-dng"
        "gif" -> "image/gif"
        else -> "application/octet-stream"
    }.toMediaTypeOrNull()

    companion object {
        /** Single static API key header, per the canonical server contract. */
        const val HEADER_API_KEY = "x-api-key"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
