package com.rainbowcockroach.albumstudio.toprint.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rainbowcockroach.albumstudio.toprint.ToPrintApp
import com.rainbowcockroach.albumstudio.toprint.data.ServerConfig
import com.rainbowcockroach.albumstudio.toprint.data.UploadEntity
import com.rainbowcockroach.albumstudio.toprint.data.UploadStatus
import com.rainbowcockroach.albumstudio.toprint.upload.UploadQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class TestResult(val ok: Boolean, val message: String)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = ToPrintApp.from(application)

    val uploads: StateFlow<List<UploadEntity>> =
        app.uploadDao.observeAll().stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
        )

    val config: StateFlow<ServerConfig> =
        app.settings.config.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000), ServerConfig("", "")
        )

    fun save(serverUrl: String, token: String, onSaved: () -> Unit) {
        viewModelScope.launch {
            app.settings.save(serverUrl, token)
            onSaved()
        }
    }

    /** Runs GET /health (no auth) then a token-authenticated GET /photos check. */
    fun testConnection(serverUrl: String, token: String, onResult: (TestResult) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val base = serverUrl.trim().trimEnd('/')
                when {
                    base.isBlank() -> TestResult(false, "Enter a server URL")
                    !app.photoApi.health(base) ->
                        TestResult(false, "Server unreachable (GET /health failed)")
                    token.isBlank() -> TestResult(false, "Server reachable, but enter a token")
                    !app.photoApi.validateToken(base, token.trim(), currentMonth()) ->
                        TestResult(false, "Reachable, but token rejected")
                    else -> TestResult(true, "Connection OK — server and token valid")
                }
            }
            onResult(result)
        }
    }

    /** Re-queue a failed upload (tap-to-retry). */
    fun retry(upload: UploadEntity) {
        viewModelScope.launch {
            app.uploadDao.updateResult(upload.id, UploadStatus.QUEUED, upload.hash, null)
            UploadQueue.enqueue(getApplication(), upload.id)
        }
    }

    private fun currentMonth(): String =
        LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))
}
