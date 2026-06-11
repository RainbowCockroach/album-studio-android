package com.rainbowcockroach.albumstudio.toprint.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Server URL + bearer token, persisted in Jetpack DataStore (Preferences). */
data class ServerConfig(val serverUrl: String, val token: String) {
    val isConfigured: Boolean
        get() = serverUrl.isNotBlank() && token.isNotBlank()

    /** Base URL with any trailing slash removed, so we can append `/photos` cleanly. */
    val baseUrl: String
        get() = serverUrl.trim().trimEnd('/')
}

class SettingsRepository(private val context: Context) {

    private val urlKey = stringPreferencesKey("server_url")
    private val tokenKey = stringPreferencesKey("token")

    val config: Flow<ServerConfig> = context.dataStore.data.map { prefs ->
        ServerConfig(
            serverUrl = prefs[urlKey].orEmpty(),
            token = prefs[tokenKey].orEmpty(),
        )
    }

    suspend fun current(): ServerConfig = config.first()

    suspend fun save(serverUrl: String, token: String) {
        context.dataStore.edit { prefs ->
            prefs[urlKey] = serverUrl.trim()
            prefs[tokenKey] = token.trim()
        }
    }
}
