package com.seca.contacts

import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.seca.core.contacts.ContactDetail
import com.seca.core.design.SecaIcons
import com.seca.core.design.component.SecaAvatar
import com.seca.core.design.component.SecaEmptyState
import com.seca.core.model.initialsOf

@Composable
internal fun DetailScreen(
    id: Long,
    ui: ContactsUi,
    viewModel: ContactsViewModel,
    withWrite: (() -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var detail by remember(id) { mutableStateOf<ContactDetail?>(null) }
    var missing by remember(id) { mutableStateOf(false) }
    // Reloads whenever the contact list changes, e.g. after a favourite is toggled.
    LaunchedEffect(id, ui.contacts) {
        val loaded = viewModel.detail(id)
        detail = loaded
        missing = loaded == null
    }
    var confirmDelete by remember { mutableStateOf(false) }
    val current = detail

    Scaffold(
        topBar = {
            SimpleTopBar(title = "", onBack = { viewModel.back() }) {
                if (current != null) {
                    IconButton(onClick = { withWrite { viewModel.setStarred(id, !current.starred) } }) {
                        Icon(
                            if (current.starred) SecaIcons.Star else SecaIcons.StarOutline,
                            contentDescription = if (current.starred) "Retirer des favoris" else "Ajouter aux favoris",
                            tint = if (current.starred) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(onClick = { viewModel.open(Screen.Edit(id)) }) {
                        Icon(SecaIcons.Edit, contentDescription = "Modifier")
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(SecaIcons.Delete, contentDescription = "Supprimer")
                    }
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                current != null -> DetailContent(current, ui, viewModel, context)
                missing -> SecaEmptyState(title = "Contact introuvable", description = "Il a peut-être été supprimé.")
            }
        }
    }

    if (confirmDelete && current != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ce contact ?") },
            text = { Text("« ${current.displayName} » sera supprimé de ce téléphone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        withWrite { viewModel.delete(id) }
                    },
                ) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun DetailContent(detail: ContactDetail, ui: ContactsUi, viewModel: ContactsViewModel, context: Context) {
    val profile = ui.profileForKey(detail.lookupKey)
    var profileMenu by remember { mutableStateOf(false) }
    val firstPhone = detail.phones.firstOrNull()?.value
    val firstEmail = detail.emails.firstOrNull()?.value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SecaAvatar(
            initials = initialsOf(detail.displayName),
            photoUri = null,
            size = 96.dp,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = detail.displayName,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp),
        )

        // The contact's profile; tapping it moves the contact to another one.
        Box(Modifier.padding(top = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable { profileMenu = true }
                    .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            ) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Icon(
                    SecaIcons.ArrowDropDown,
                    contentDescription = "Changer de profil",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            DropdownMenu(expanded = profileMenu, onDismissRequest = { profileMenu = false }) {
                ProfileMenuItems(
                    profiles = ui.profiles,
                    selectedId = profile.id,
                    onSelect = {
                        profileMenu = false
                        viewModel.assignProfile(detail.lookupKey, it.id)
                    },
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(top = 24.dp),
        ) {
            if (firstPhone != null) {
                QuickAction(SecaIcons.Phone, "Appeler") { dial(context, firstPhone) }
                QuickAction(SecaIcons.Messages, "Message") { sms(context, firstPhone) }
            }
            if (firstEmail != null) {
                QuickAction(SecaIcons.Email, "E-mail") { email(context, firstEmail) }
            }
        }

        Column(Modifier.fillMaxWidth()) {
            if (detail.phones.isNotEmpty()) {
                SectionHeader("Téléphone")
                detail.phones.forEach { field ->
                    FieldRow(
                        value = field.value,
                        label = Phone.getTypeLabel(context.resources, field.type, "").toString(),
                        onClick = { dial(context, field.value) },
                    ) {
                        IconButton(onClick = { sms(context, field.value) }) {
                            Icon(SecaIcons.Messages, contentDescription = "Envoyer un message")
                        }
                    }
                }
            }
            if (detail.emails.isNotEmpty()) {
                SectionHeader("E-mail")
                detail.emails.forEach { field ->
                    FieldRow(
                        value = field.value,
                        label = Email.getTypeLabel(context.resources, field.type, "").toString(),
                        onClick = { email(context, field.value) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FieldRow(
    value: String,
    label: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (label.isNotEmpty()) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}
