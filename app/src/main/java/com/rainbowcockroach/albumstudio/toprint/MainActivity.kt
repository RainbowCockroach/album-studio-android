package com.rainbowcockroach.albumstudio.toprint

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.rainbowcockroach.albumstudio.toprint.ui.AppRoot
import com.rainbowcockroach.albumstudio.toprint.ui.theme.ToPrintTheme

class MainActivity : ComponentActivity() {

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best-effort */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maybeRequestNotificationPermission()

        val openSettings = intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)
        val message = intent.getStringExtra(EXTRA_MESSAGE)

        enableEdgeToEdge()
        setContent {
            ToPrintTheme {
                AppRoot(startOnSettings = openSettings, initialMessage = message)
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val EXTRA_OPEN_SETTINGS = "open_settings"
        const val EXTRA_MESSAGE = "message"
    }
}
