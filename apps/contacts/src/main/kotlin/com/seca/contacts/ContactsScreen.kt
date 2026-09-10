package com.seca.contacts

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.seca.core.design.component.SecaContactRow
import com.seca.core.design.component.SecaEmptyState
import com.seca.core.design.component.SecaSuiteBar
import com.seca.core.model.SecaContact
import java.text.Normalizer

@Composable
fun ContactsApp(
    permissionGranted: Boolean,
    onPermissionResult: (Boolean) -> Unit,
    viewModel: ContactsViewModel = viewModel(),
) {
    val context = LocalContext.current
    var permanentlyDenied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onPermissionResult(granted)
        // After a refusal, no rationale means "don't ask again": only Settings can grant it now.
        permanentlyDenied = !granted &&
            (context as? Activity)?.shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS) == false
    }
    LaunchedEffect(permissionGranted) {
        if (permissionGranted) viewModel.start()
    }
    val state by viewModel.state.collectAsState()

    SecaTheme(identity = SecaAppIdentity.Contacts) {
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
                val loaded = state as? ContactsUiState.Loaded
                when {
                    !permissionGranted -> PermissionPrompt(
                        permanentlyDenied = permanentlyDenied,
                        onAllow = { launcher.launch(Manifest.permission.READ_CONTACTS) },
                        onOpenSettings = { openAppSettings(context) },
                    )
                    loaded == null -> Unit
                    loaded.contacts.isEmpty() -> SecaEmptyState(
                        title = "Aucun contact",
                        description = "Les contacts enregistrés sur ce téléphone apparaîtront ici.",
                    )
                    else -> ContactList(loaded.contacts, onOpen = { openContact(context, it) })
                }
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

@Composable
private fun ContactList(contacts: List<SecaContact>, onOpen: (SecaContact) -> Unit) {
    // The provider already sorts by name, so grouping keeps the letters in order.
    val sections = remember(contacts) { contacts.groupBy { initialOf(it.displayName) } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        item(key = "count") {
            Text(
                text = if (contacts.size == 1) "1 contact" else "${contacts.size} contacts",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        sections.forEach { (letter, group) ->
            item(key = "section-$letter") {
                Text(
                    text = letter,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(group, key = { it.id }) { contact ->
                SecaContactRow(contact = contact, onClick = { onOpen(contact) })
            }
        }
    }
}

/** "Élodie" files under E, not under a separate É; anything without a letter under #. */
private fun initialOf(name: String): String {
    val letter = name.firstOrNull { it.isLetter() } ?: return "#"
    return Normalizer.normalize(letter.toString(), Normalizer.Form.NFD).first().uppercaseChar().toString()
}

/** Temporary bridge until Seca's own contact screen exists: the system shows the contact. */
private fun openContact(context: Context, contact: SecaContact) {
    val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contact.id)
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}

/** Opens the sibling Seca app, or the system's dialer or messaging app until it exists. */
private fun openSibling(context: Context, identity: SecaAppIdentity) {
    val packages = context.packageManager
    val intent = when (identity) {
        SecaAppIdentity.Contacts -> return
        SecaAppIdentity.Phone -> packages.getLaunchIntentForPackage("com.seca.phone")
            ?: Intent(Intent.ACTION_DIAL)
        SecaAppIdentity.Messages -> packages.getLaunchIntentForPackage("com.seca.messages")
            ?: Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)
    }
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    runCatching { context.startActivity(intent) }
}
