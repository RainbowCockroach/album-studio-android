package com.rainbowcockroach.albumstudio.toprint.upload

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.rainbowcockroach.albumstudio.toprint.R
import com.rainbowcockroach.albumstudio.toprint.ToPrintApp
import com.rainbowcockroach.albumstudio.toprint.data.UploadOutcome
import com.rainbowcockroach.albumstudio.toprint.data.UploadStatus
import java.io.File
import java.security.MessageDigest

/**
 * Uploads a single photo (identified by its Room row id) and keeps the row up to date.
 * Survives process death, airplane mode and bad signal: transient failures return
 * [Result.retry] so WorkManager re-runs with exponential backoff once a network is back.
 */
class UploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val app = ToPrintApp.from(appContext)
    private val dao = app.uploadDao

    override suspend fun doWork(): Result {
        val uploadId = inputData.getLong(UploadQueue.KEY_UPLOAD_ID, -1L)
        if (uploadId < 0) return Result.failure()

        val row = dao.getById(uploadId) ?: return Result.success() // already cleaned up
        if (row.status == UploadStatus.DONE) return Result.success()

        val file = File(row.localPath)
        if (!file.exists()) {
            dao.updateResult(uploadId, UploadStatus.FAILED, row.hash, "Local file missing")
            return Result.failure()
        }

        val config = app.settings.current()
        if (!config.isConfigured) {
            // Without a server we can't proceed; surface it and stop (re-enqueued from UI later).
            dao.updateResult(uploadId, UploadStatus.FAILED, null, "Server not configured")
            return Result.failure()
        }

        dao.updateStatus(uploadId, UploadStatus.UPLOADING)
        runCatching { setForeground(buildForegroundInfo(row.fileName)) }

        // Optional optimization: if we already know this photo is on the server by hash,
        // skip the upload body entirely.
        val localHash = runCatching { sha256(file) }.getOrNull()
        if (localHash != null && app.photoApi.existsByHash(config.baseUrl, config.token, localHash)) {
            return finishSuccess(uploadId, file, localHash, existed = true)
        }

        val outcome = app.photoApi.upload(
            baseUrl = config.baseUrl,
            token = config.token,
            file = file,
            fileName = row.fileName,
            capturedAt = row.capturedAt,
        )

        return when (outcome) {
            is UploadOutcome.Success -> finishSuccess(uploadId, file, outcome.hash, outcome.existed)

            is UploadOutcome.Unauthorized -> {
                dao.updateResult(uploadId, UploadStatus.FAILED, null, outcome.message)
                Result.failure() // 401 is permanent — do not retry
            }

            is UploadOutcome.PermanentFailure -> {
                dao.updateResult(uploadId, UploadStatus.FAILED, null, outcome.message)
                Result.failure()
            }

            is UploadOutcome.Retryable -> {
                // Keep the row visible as failed-but-retrying; WorkManager backs off.
                dao.updateResult(uploadId, UploadStatus.QUEUED, null, outcome.message)
                Result.retry()
            }
        }
    }

    private suspend fun finishSuccess(
        uploadId: Long,
        file: File,
        hash: String,
        existed: Boolean,
    ): Result {
        dao.updateResult(uploadId, UploadStatus.DONE, hash, if (existed) "Already on server" else null)
        file.delete() // local pending copy no longer needed
        return Result.success()
    }

    // Required for expedited work: the system needs a notification to promote us to a
    // foreground service when there's no quota left.
    override suspend fun getForegroundInfo(): ForegroundInfo = buildForegroundInfo(null)

    private fun buildForegroundInfo(fileName: String?): ForegroundInfo {
        val notification: Notification = NotificationCompat.Builder(applicationContext, ToPrintApp.UPLOAD_CHANNEL_ID)
            .setContentTitle("Uploading photo")
            .setContentText(fileName ?: "Sending to your server")
            .setSmallIcon(R.drawable.ic_upload)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()

        val notificationId = inputData.getLong(UploadQueue.KEY_UPLOAD_ID, 0L).toInt()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            var read = stream.read(buffer)
            while (read >= 0) {
                digest.update(buffer, 0, read)
                read = stream.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
