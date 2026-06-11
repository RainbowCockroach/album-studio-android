package com.rainbowcockroach.albumstudio.toprint.upload

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Runs the full capture-time resolution chain against a content [Uri].
 *
 * MUST be called while the URI read grant is still alive (i.e. inside
 * [com.rainbowcockroach.albumstudio.toprint.ShareReceiverActivity], before it
 * finishes) — strategies 1 and 3 both need to read from the original content URI.
 *
 * Priority order (mirrors the desktop app's `get_display_date()`):
 *   1. EXIF DateTimeOriginal, falling back to EXIF DateTime
 *   2. `YYYYMMDD_HHMMSS` prefix on the display name
 *   3. MediaStore DATE_TAKEN
 *   4. null (field omitted from upload; desktop falls back to upload month)
 */
class CaptureTimeResolver(private val context: Context) {

    /** @return ISO-8601 local datetime string, or null if every strategy fails. */
    fun resolve(uri: Uri): String? {
        exifDateTime(uri)?.let { return CaptureTime.format(it) }
        CaptureTime.parseFilename(queryDisplayName(uri))?.let { return CaptureTime.format(it) }
        mediaStoreDateTaken(uri)?.let { return CaptureTime.format(it) }
        return null
    }

    private fun exifDateTime(uri: Uri): LocalDateTime? = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            val original = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            CaptureTime.parseExifDateTime(original)
                ?: CaptureTime.parseExifDateTime(exif.getAttribute(ExifInterface.TAG_DATETIME))
        }
    } catch (_: Exception) {
        null
    }

    fun queryDisplayName(uri: Uri): String? = try {
        context.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else null
        }
    } catch (_: Exception) {
        null
    }

    private fun mediaStoreDateTaken(uri: Uri): LocalDateTime? = try {
        context.contentResolver.query(
            uri, arrayOf(MediaStore.Images.Media.DATE_TAKEN), null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                if (idx >= 0 && !cursor.isNull(idx)) {
                    val epochMillis = cursor.getLong(idx)
                    if (epochMillis > 0) {
                        LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()
                        )
                    } else null
                } else null
            } else null
        }
    } catch (_: Exception) {
        null
    }
}
