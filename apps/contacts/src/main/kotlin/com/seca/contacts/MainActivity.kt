package com.seca.contacts

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {

    /** Re-read on every resume: the user may grant or revoke access in Settings. */
    private val permissionGranted = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ContactsApp(
                permissionGranted = permissionGranted.value,
                onPermissionResult = { permissionGranted.value = it },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        permissionGranted.value =
            checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }
}
