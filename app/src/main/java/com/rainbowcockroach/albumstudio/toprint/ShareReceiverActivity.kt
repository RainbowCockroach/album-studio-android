package com.rainbowcockroach.albumstudio.toprint

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.rainbowcockroach.albumstudio.toprint.data.UploadEntity
import com.rainbowcockroach.albumstudio.toprint.data.UploadStatus
import com.rainbowcockroach.albumstudio.toprint.upload.CaptureTimeResolver
import com.rainbowcockroach.albumstudio.toprint.upload.UploadQueue
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID

/**
 * The app's reason to exist: a Share target. Gallery → Share → "To Print" lands here.
 *
 * Everything that needs the (short-lived) content-URI grant happens NOW, synchronously,
 * before [finish]: copy each image into app-private storage and resolve its `capturedAt`.
 * Raw content URIs are never handed to WorkManager — the grant won't survive this activity.
 * Typical shares are a handful of photos, so the copy is quick; uploads then run in the
 * background via [com.rainbowcockroach.albumstudio.toprint.upload.UploadWorker].
 */
class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uris = extractUris(intent)
        if (uris.isEmpty()) {
            toast("No photos to upload")
            finish()
            return
        }

        val app = ToPrintApp.from(this)
        val config = runBlocking { app.settings.current() }
        if (!config.isConfigured) {
            // Can't upload without a destination — send the owner to Settings and explain.
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN_SETTINGS, true)
                    .putExtra(MainActivity.EXTRA_MESSAGE, "Set your server URL and token first")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            finish()
            return
        }

        val queued = runBlocking { ingest(uris) }
        toast(if (queued == 1) "1 photo queued" else "$queued photos queued")
        finish()
    }

    /** Copy each shared image locally, resolve capturedAt, persist a row, enqueue upload. */
    private suspend fun ingest(uris: List<Uri>): Int {
        val app = ToPrintApp.from(this)
        val resolver = CaptureTimeResolver(this)
        val pendingDir = File(filesDir, "pending").apply { mkdirs() }

        var count = 0
        for (uri in uris) {
            try {
                tryTakePersistablePermission(uri)

                val displayName = resolver.queryDisplayName(uri)
                val fileName = safeFileName(uri, displayName)
                val capturedAt = resolver.resolve(uri)

                val dest = File(pendingDir, "${UUID.randomUUID()}_$fileName")
                contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                } ?: continue

                val id = app.uploadDao.insert(
                    UploadEntity(
                        fileName = fileName,
                        localPath = dest.absolutePath,
                        capturedAt = capturedAt,
                        status = UploadStatus.QUEUED,
                        createdAt = System.currentTimeMillis(),
                    )
                )
                UploadQueue.enqueue(this, id)
                count++
            } catch (_: Exception) {
                // Skip this one image; the rest of the batch still goes through.
            }
        }
        return count
    }

    private fun extractUris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        return when (intent.action) {
            Intent.ACTION_SEND ->
                listOfNotNull(intent.parcelableExtra(Intent.EXTRA_STREAM))
            Intent.ACTION_SEND_MULTIPLE ->
                intent.parcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            else -> emptyList()
        }
    }

    private fun tryTakePersistablePermission(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Most share grants aren't persistable; that's fine — we copy bytes right now anyway.
        }
    }

    /** A clean original-ish filename with an extension, for the multipart `file` field. */
    private fun safeFileName(uri: Uri, displayName: String?): String {
        val raw = displayName?.substringAfterLast('/')?.trim().orEmpty()
        if (raw.isNotEmpty() && raw.contains('.')) return raw

        val ext = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(contentResolver.getType(uri))
            ?: "jpg"
        val base = raw.ifEmpty { "photo_${System.currentTimeMillis()}" }
        return "$base.$ext"
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // --- Type-safe Parcelable extras across the API 33 signature change ---

    @Suppress("DEPRECATION")
    private inline fun <reified T : android.os.Parcelable> Intent.parcelableExtra(key: String): T? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(key, T::class.java)
        } else {
            getParcelableExtra(key) as? T
        }

    @Suppress("DEPRECATION")
    private inline fun <reified T : android.os.Parcelable> Intent.parcelableArrayListExtra(key: String): ArrayList<T>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayListExtra(key, T::class.java)
        } else {
            getParcelableArrayListExtra(key)
        }
}
