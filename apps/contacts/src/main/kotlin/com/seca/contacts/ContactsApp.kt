package com.seca.contacts

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seca.core.design.SecaAppIdentity
import com.seca.core.design.SecaIcons
import com.seca.core.design.SecaMotion
import com.seca.core.design.SecaTheme
import com.seca.core.design.component.SecaEmptyState
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

    // Held here, above the screen transitions, so the list keeps its scroll
    // position when the user opens a contact and comes back.
    val homeListState = rememberLazyListState()

    LaunchedEffect(permissionGranted) {
        if (permissionGranted) viewModel.start()
    }
    BackHandler(enabled = viewModel.backStack.size > 1) { viewModel.back() }

    SecaTheme(identity = SecaAppIdentity.Contacts, palette = ui.palette) {
        if (!permissionGranted) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                bottomBar = {
                    SecaSuiteBar(current = SecaAppIdentity.Contacts, onSelect = { openSibling(context, it) })
                },
            ) { padding ->
                SecaEmptyState(
                    icon = SecaIcons.Contacts,
                    title = "Vos contacts, chez vous",
                    description = "Seca Contacts affiche les contacts enregistrés sur ce téléphone. " +
                        "Rien ne quitte l'appareil : l'application n'a pas accès à Internet.",
                    modifier = Modifier.padding(padding),
                    action = {
                        if (permanentlyDenied) {
                            Button(onClick = { openAppSettings(context) }) { Text("Ouvrir les réglages") }
                        } else {
                            Button(onClick = { readLauncher.launch(Manifest.permission.READ_CONTACTS) }) {
                                Text("Autoriser l'accès")
                            }
                        }
                    },
                )
            }
        } else {
            AnimatedContent(
                targetState = viewModel.backStack.last() to viewModel.backStack.size,
                transitionSpec = {
                    // A deeper screen slides in from the end; going back reverses it.
                    val direction = if (targetState.second >= initialState.second) 1 else -1
                    (slideInHorizontally(SecaMotion.emphasized()) { direction * it / 4 } + fadeIn(SecaMotion.standard()))
                        .togetherWith(
                            slideOutHorizontally(SecaMotion.emphasized()) { -direction * it / 4 } +
                                fadeOut(SecaMotion.standard()),
                        )
                },
                label = "screens",
            ) { (screen, _) ->
                when (screen) {
                    Screen.Home -> HomeScreen(ui, viewModel, homeListState, onOpenSibling = { openSibling(context, it) })
                    is Screen.Detail -> DetailScreen(screen.id, ui, viewModel, withWrite)
                    is Screen.Edit -> EditScreen(screen.id, ui, viewModel, withWrite)
                    Screen.Settings -> SettingsScreen(ui, viewModel)
                }
            }
        }
    }
}
