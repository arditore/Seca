package com.seca.contacts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {

    /** Re-read on every resume: the user may grant or revoke access in Settings. */
    private val permissionGranted = mutableStateOf(false)
    private val viewModel: ContactsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent {
            ContactsApp(
                permissionGranted = permissionGranted.value,
                onPermissionResult = { permissionGranted.value = it },
                viewModel = viewModel,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        permissionGranted.value =
            checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }

    /** Another app asked to add a number, or to show a contact: open straight on it. */
    private fun handle(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_INSERT, Intent.ACTION_INSERT_OR_EDIT -> viewModel.startWith(
                Screen.Edit(id = null, prefillPhone = intent.getStringExtra(ContactsContract.Intents.Insert.PHONE)),
            )
            Intent.ACTION_VIEW -> intent.data?.let(viewModel::openExternal)
        }
    }
}
