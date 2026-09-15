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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.core.content.ContextCompat
import com.mediaorganizer.app.ui.MediaOrganizerUi
import com.mediaorganizer.app.ui.SlateBlack
import com.mediaorganizer.app.ui.CyanPrimary
import com.mediaorganizer.app.ui.VioletSecondary

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        val writeGranted = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: false
        if (!readGranted || !writeGranted) {
            Toast.makeText(this, "Storage permissions are required to scan and rename files", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val viewModel = MediaOrganizerViewModel()
        
        setContent {
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

    override fun onResume() {
        super.onResume()
        // Re-check permissions when coming back to the app from settings
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(this, "All Files Access is required to scan and rename media", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ requires MANAGE_EXTERNAL_STORAGE
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
            }
        } else {
            // Android 10 and below requires normal runtime storage permissions
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
