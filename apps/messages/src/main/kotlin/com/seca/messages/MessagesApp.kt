package com.seca.messages

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seca.core.design.SecaAppIdentity
import com.seca.core.design.SecaIcons
import com.seca.core.design.SecaMotion
import com.seca.core.design.SecaTheme
import com.seca.core.design.component.SecaEmptyState
import com.seca.core.design.component.SecaSuiteBar

private val MessagePermissions = arrayOf(
    Manifest.permission.READ_SMS,
    Manifest.permission.READ_CONTACTS,
)

@Composable
fun MessagesApp(
    smsGranted: Boolean,
    contactsGranted: Boolean,
    isDefaultApp: Boolean,
    onPermissionsResult: () -> Unit,
    viewModel: MessagesViewModel = viewModel(),
) {
    val context = LocalContext.current
    val ui by viewModel.ui.collectAsState()

    var permanentlyDenied by remember { mutableStateOf(false) }
    val accessLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        onPermissionsResult()
        // After a refusal, no rationale means "don't ask again": only Settings can grant it now.
        permanentlyDenied = result[Manifest.permission.READ_SMS] != true &&
            (context as? Activity)?.shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS) == false
    }

    // Becoming the SMS app grants reading, receiving and sending in one go. Notifications
    // are asked first, since they are how a message that arrives gets seen.
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        onPermissionsResult()
    }
    val requestRole = {
        val roles = context.getSystemService(RoleManager::class.java)
        if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_SMS) && !roles.isRoleHeld(RoleManager.ROLE_SMS)) {
            roleLauncher.launch(roles.createRequestRoleIntent(RoleManager.ROLE_SMS))
        }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        requestRole()
    }
    val onBecomeDefault: () -> Unit = { withNotificationPermission(context, notificationLauncher, requestRole) }

    // Held above the screen transitions, so the list keeps its scroll position.
    val homeListState = rememberLazyListState()

    LaunchedEffect(smsGranted, contactsGranted) { viewModel.start(smsGranted, contactsGranted) }
    BackHandler(enabled = viewModel.backStack.size > 1) { viewModel.back() }

    SecaTheme(identity = SecaAppIdentity.Messages, palette = ui.palette) {
        if (!smsGranted) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                bottomBar = { SecaSuiteBar(current = SecaAppIdentity.Messages, onSelect = { openSibling(context, it) }) },
            ) { padding ->
                SecaEmptyState(
                    icon = SecaIcons.Messages,
                    title = "Vos messages",
                    description = "Seca Messages lit, reçoit et envoie les SMS de ce téléphone. " +
                        "Ils restent sur l'appareil et ne partent que vers leur destinataire.",
                    modifier = Modifier.padding(padding),
                    action = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(onClick = onBecomeDefault) { Text("Utiliser Seca Messages") }
                            if (permanentlyDenied) {
                                TextButton(onClick = { openAppSettings(context) }) { Text("Ouvrir les réglages") }
                            } else {
                                TextButton(onClick = { accessLauncher.launch(MessagePermissions) }) {
                                    Text("Seulement lire les messages")
                                }
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
                    MessagesScreen.Home -> ConversationsScreen(ui, viewModel, homeListState, isDefaultApp, onBecomeDefault)
                    is MessagesScreen.Conversation -> ConversationScreen(screen, ui, viewModel, isDefaultApp)
                    MessagesScreen.NewMessage -> NewMessageScreen(ui, viewModel)
                    MessagesScreen.Settings -> SettingsScreen(ui, viewModel, isDefaultApp, onBecomeDefault)
                    MessagesScreen.Archived -> ArchivedScreen(ui, viewModel)
                    SpamRoute -> SpamScreen(ui, viewModel)
                    MessagesScreen.Relays -> RelaysScreen(ui, viewModel)
                    is SafetyNumberRoute -> SafetyNumberScreen(screen, ui, viewModel)
                }
            }
        }
    }
}
