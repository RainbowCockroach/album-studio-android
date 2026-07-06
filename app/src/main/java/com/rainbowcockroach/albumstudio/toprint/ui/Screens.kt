package com.rainbowcockroach.albumstudio.toprint.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.Intent
import com.rainbowcockroach.albumstudio.toprint.data.UpdateStatus
import com.rainbowcockroach.albumstudio.toprint.data.UploadEntity
import com.rainbowcockroach.albumstudio.toprint.data.UploadStatus
import java.text.DateFormat
import java.util.Date

@Composable
fun AppRoot(
    startOnSettings: Boolean,
    initialMessage: String?,
    viewModel: MainViewModel = viewModel(),
) {
    var showSettings by remember { mutableStateOf(startOnSettings) }

    if (showSettings) {
        SettingsScreen(
            viewModel = viewModel,
            initialMessage = initialMessage,
            onBack = { showSettings = false },
        )
    } else {
        UploadListScreen(
            viewModel = viewModel,
            onOpenSettings = { showSettings = true },
        )
    }

    // Auto-update prompt: the ViewModel checks GitHub on launch; a dialog appears only
    // when a newer release exists. "Update" opens the release page in the browser.
    val updateStatus by viewModel.updateStatus.collectAsStateWithLifecycle()
    (updateStatus as? UpdateStatus.Available)?.let { available ->
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdate() },
            title = { Text("Update available") },
            text = {
                Text("Version ${available.versionName} is available. You have ${viewModel.installedVersion}.")
            },
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, available.releaseUrl.toUri())
                    )
                    viewModel.dismissUpdate()
                }) { Text("Update") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissUpdate() }) { Text("Later") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UploadListScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit,
) {
    val uploads by viewModel.uploads.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("To Print") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        if (uploads.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "No uploads yet",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Share photos from your gallery to \"To Print\" and they'll appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uploads, key = { it.id }) { upload ->
                    UploadRow(upload, onRetry = { viewModel.retry(upload) })
                }
            }
        }
    }
}

@Composable
private fun UploadRow(upload: UploadEntity, onRetry: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    upload.fileName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitleFor(upload),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            when (upload.status) {
                UploadStatus.UPLOADING ->
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                UploadStatus.FAILED ->
                    OutlinedButton(onClick = onRetry) { Text("Retry") }
                else ->
                    StatusLabel(upload.status)
            }
        }
    }
}

@Composable
private fun StatusLabel(status: UploadStatus) {
    val (text, color) = when (status) {
        UploadStatus.QUEUED -> "Queued" to MaterialTheme.colorScheme.onSurfaceVariant
        UploadStatus.UPLOADING -> "Uploading" to MaterialTheme.colorScheme.primary
        UploadStatus.DONE -> "Done" to MaterialTheme.colorScheme.primary
        UploadStatus.FAILED -> "Failed" to MaterialTheme.colorScheme.error
    }
    Text(text, style = MaterialTheme.typography.labelLarge, color = color)
}

private fun subtitleFor(upload: UploadEntity): String {
    val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        .format(Date(upload.createdAt))
    return when (upload.status) {
        UploadStatus.DONE -> upload.errorMsg ?: "Uploaded · $time"
        UploadStatus.FAILED -> upload.errorMsg ?: "Failed · $time"
        else -> time
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    viewModel: MainViewModel,
    initialMessage: String?,
    onBack: () -> Unit,
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var url by remember(config.serverUrl) { mutableStateOf(config.serverUrl) }
    var token by remember(config.token) { mutableStateOf(config.token) }
    var status by remember { mutableStateOf(initialMessage) }
    var testing by remember { mutableStateOf(false) }
    var updateMsg by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it; status = null },
                label = { Text("Server URL") },
                placeholder = { Text("https://photos.example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it; status = null },
                label = { Text("API token") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            status?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    enabled = !testing,
                    onClick = {
                        testing = true
                        status = "Testing…"
                        viewModel.testConnection(url, token) { result ->
                            testing = false
                            status = result.message
                        }
                    },
                ) { Text("Test connection") }

                Button(
                    onClick = {
                        viewModel.save(url, token) {
                            status = "Saved"
                            onBack()
                        }
                    },
                ) { Text("Save") }
            }

            Spacer(Modifier.height(24.dp))

            // --- About / updates ---
            Text(
                "Version ${viewModel.installedVersion}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            updateMsg?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(
                enabled = !checking,
                onClick = {
                    checking = true
                    updateMsg = "Checking…"
                    viewModel.checkForUpdate { result ->
                        checking = false
                        updateMsg = when (result) {
                            is UpdateStatus.Available -> {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, result.releaseUrl.toUri())
                                )
                                "Version ${result.versionName} available — opening download page"
                            }
                            is UpdateStatus.UpToDate -> "You're on the latest version"
                            is UpdateStatus.Failed -> "Check failed: ${result.message}"
                        }
                    }
                },
            ) { Text("Check for updates") }
        }
    }
}
