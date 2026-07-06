package com.rainbowcockroach.albumstudio.toprint.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Status of a single photo's upload. The UI observes the table and Workers update it;
 * we never derive UI state from WorkManager queries alone (WorkManager prunes finished
 * work, so the Room row is the durable source of truth).
 */
enum class UploadStatus { QUEUED, UPLOADING, DONE, FAILED }

@Entity(tableName = "uploads")
data class UploadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    /** Absolute path to the app-private copy under filesDir/pending/. Deleted once DONE. */
    val localPath: String,
    /** Small downscaled preview under filesDir/thumbnails/. Unlike localPath, this survives
     *  past DONE — it's how the list keeps showing a photo after the full-res copy is gone. */
    val thumbnailPath: String? = null,
    /** Resolved ISO-8601 capturedAt, or null if every strategy failed. */
    val capturedAt: String?,
    val status: UploadStatus,
    /** SHA-256 returned by the server (or computed locally), once known. */
    val hash: String? = null,
    val errorMsg: String? = null,
    val createdAt: Long,
)
