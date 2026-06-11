package com.rainbowcockroach.albumstudio.toprint

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.rainbowcockroach.albumstudio.toprint.data.AppDatabase
import com.rainbowcockroach.albumstudio.toprint.data.PhotoApi
import com.rainbowcockroach.albumstudio.toprint.data.SettingsRepository
import com.rainbowcockroach.albumstudio.toprint.data.UploadDao

/**
 * Manual dependency container (no DI framework at this size). Holds the singletons the
 * Workers and UI share: Room, settings, and the OkHttp-backed [PhotoApi]. Also owns the
 * upload-progress notification channel.
 */
class ToPrintApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val uploadDao: UploadDao by lazy { database.uploadDao() }
    val settings: SettingsRepository by lazy { SettingsRepository(this) }
    val photoApi: PhotoApi by lazy { PhotoApi() }

    override fun onCreate() {
        super.onCreate()
        createUploadChannel()
    }

    private fun createUploadChannel() {
        val channel = NotificationChannel(
            UPLOAD_CHANNEL_ID,
            "Photo uploads",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Progress while photos upload to your server" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val UPLOAD_CHANNEL_ID = "uploads"

        fun from(context: Context): ToPrintApp =
            context.applicationContext as ToPrintApp
    }
}
