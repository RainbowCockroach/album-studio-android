package com.rainbowcockroach.albumstudio.toprint.upload

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Enqueues one [UploadWorker] per photo. Each Work item is keyed by the Room row id and
 * uses a unique name so a retry (tapping a failed item) just REPLACEs the existing chain
 * rather than stacking duplicates.
 */
object UploadQueue {

    const val KEY_UPLOAD_ID = "upload_id"
    const val TAG = "photo_upload"

    fun enqueue(context: Context, uploadId: Long) {
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(Data.Builder().putLong(KEY_UPLOAD_ID, uploadId).build())
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(uploadId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun workName(uploadId: Long) = "upload_$uploadId"
}
