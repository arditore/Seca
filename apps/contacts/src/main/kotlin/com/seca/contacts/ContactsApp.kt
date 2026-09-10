package com.seca.contacts

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seca.core.design.SecaAppIdentity
import com.seca.core.design.SecaTheme
import com.seca.core.design.component.SecaSuiteBar

@Composable
fun ContactsApp(
    permissionGranted: Boolean,
    onPermissionResult: (Boolean) -> Unit,
    viewModel: ContactsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val ui by viewModel.ui.collectAsState()

    var permanentlyDenied by remember { mutableStateOf(false) }
    val readLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onPermissionResult(granted)
        // After a refusal, no rationale means "don't ask again": only Settings can grant it now.
        permanentlyDenied = !granted &&
            (context as? Activity)?.shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS) == false
    }

    // Write access is asked only the first time the user changes something.
    var pendingWrite by remember { mutableStateOf<(() -> Unit)?>(null) }
    val writeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingWrite?.invoke()
        pendingWrite = null
    }
    val withWrite: (() -> Unit) -> Unit = { action ->
        if (context.checkSelfPermission(Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            pendingWrite = action
            writeLauncher.launch(Manifest.permission.WRITE_CONTACTS)
        }
    }

    LaunchedEffect(permissionGranted) {
        if (permissionGranted) viewModel.start()
    }
    BackHandler(enabled = viewModel.backStack.size > 1) { viewModel.back() }

    SecaTheme(identity = SecaAppIdentity.Contacts, palette = ui.palette) {
        if (!permissionGranted) {
            Scaffold(
                bottomBar = {
                    SecaSuiteBar(current = SecaAppIdentity.Contacts, onSelect = { openSibling(context, it) })
                },
            ) { padding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    PermissionPrompt(
                        permanentlyDenied = permanentlyDenied,
                        onAllow = { readLauncher.launch(Manifest.permission.READ_CONTACTS) },
                        onOpenSettings = { openAppSettings(context) },
                    )
                }
            }
        } else {
            when (val screen = viewModel.backStack.last()) {
                Screen.Home -> HomeScreen(ui, viewModel, onOpenSibling = { openSibling(context, it) })
                is Screen.Detail -> DetailScreen(screen.id, ui, viewModel, withWrite)
                is Screen.Edit -> EditScreen(screen.id, ui, viewModel, withWrite)
                Screen.Settings -> SettingsScreen(ui, viewModel)
            }
        }
    }
}

@Composable
private fun PermissionPrompt(
    permanentlyDenied: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Accès aux contacts",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Seca Contacts affiche les contacts enregistrés sur ce téléphone. " +
                "Rien ne quitte l'appareil : l'application n'a pas accès à Internet.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
        )
        if (permanentlyDenied) {
            Button(onClick = onOpenSettings) { Text("Ouvrir les réglages") }
        } else {
            Button(onClick = onAllow) { Text("Autoriser") }
        }
    }
}
