package com.mediaorganizer.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.mediaorganizer.app.ui.CyanPrimary
import com.mediaorganizer.app.ui.MediaOrganizerUi
import com.mediaorganizer.app.ui.SlateBlack
import com.mediaorganizer.app.ui.VioletSecondary

class MainActivity : AppCompatActivity() {

    private var pendingPreviewTrigger by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        val writeGranted = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: false
        if (!readGranted || !writeGranted) {
            Toast.makeText(this, "Storage permissions are required to scan and rename files", Toast.LENGTH_LONG).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "Notification permission allows Watchdog scan alerts", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WatchdogNotificationHelper.ensureChannel(this)
        handleIncomingIntent(intent)

        setContent {
            val viewModel: MediaOrganizerViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

            LaunchedEffect(pendingPreviewTrigger) {
                if (pendingPreviewTrigger) {
                    pendingPreviewTrigger = false
                    viewModel.previewTriggerCount++
                    viewModel.startScan()
                }
            }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = SlateBlack,
                    surface = SlateBlack,
                    primary = CyanPrimary,
                    secondary = VioletSecondary
                )
            ) {
                MediaOrganizerUi(viewModel = viewModel)
            }
        }

        checkAndRequestPermissions()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == WatchdogNotificationHelper.ACTION_PREVIEW_SCAN) {
            WatchdogNotificationHelper.cancelScanPrompt(this)
            pendingPreviewTrigger = true
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(this, "All Files Access is required to scan and rename media", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                    Toast.makeText(this, "Please grant All Files Access to organize your media files", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        startActivity(intent)
                    } catch (ex: Exception) {
                        Toast.makeText(this, "Could not open settings. Please grant All Files Access manually.", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                requestNotificationPermissionIfNeeded()
            }
        } else {
            val readPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            val writePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)

            if (readPermission != PackageManager.PERMISSION_GRANTED || writePermission != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            }
        }
    }
}
