package com.seca.contacts

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.seca.core.contacts.ContactDetail
import com.seca.core.design.SecaIcons
import com.seca.core.design.component.SecaAvatar
import com.seca.core.design.component.SecaEmptyState
import com.seca.core.design.component.SecaGroupItem
import com.seca.core.design.component.SecaProfileBadge
import com.seca.core.design.component.SecaSectionLabel
import com.seca.core.design.component.SecaTopBar
import com.seca.core.design.component.rememberContactPhoto
import com.seca.core.model.Profile
import com.seca.core.model.initialsOf
import kotlinx.coroutines.launch

@Composable
internal fun DetailScreen(
    id: Long,
    ui: ContactsUi,
    viewModel: ContactsViewModel,
    withWrite: (() -> Unit) -> Unit,
) {
    var detail by remember(id) { mutableStateOf<ContactDetail?>(null) }
    var missing by remember(id) { mutableStateOf(false) }
    // Reloads whenever the contact list changes, e.g. after a favourite is toggled.
    LaunchedEffect(id, ui.contacts) {
        val loaded = viewModel.detail(id)
        detail = loaded
        missing = loaded == null
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val current = detail

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            SecaTopBar(title = "", onBack = { viewModel.back() }) {
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
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(SecaIcons.MoreVert, contentDescription = "Plus d'options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Partager") },
                                leadingIcon = { Icon(SecaIcons.Share, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    scope.launch { viewModel.vCardOf(id)?.let { shareVCard(context, current.displayName, it) } }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Supprimer") },
                                leadingIcon = { Icon(SecaIcons.Delete, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    confirmDelete = true
                                },
                            )
                        }
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
                current != null -> DetailContent(current, ui, viewModel)
                missing -> SecaEmptyState(
                    icon = SecaIcons.Contacts,
                    title = "Contact introuvable",
                    description = "Il a peut-être été supprimé.",
                )
            }
        }
    }

    if (confirmDelete && current != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(SecaIcons.Delete, contentDescription = null) },
            title = { Text("Supprimer ce contact ?") },
            text = { Text("« ${current.displayName} » sera supprimé de ce téléphone. C'est définitif.") },
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
private fun DetailContent(detail: ContactDetail, ui: ContactsUi, viewModel: ContactsViewModel) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val profile = ui.profileForKey(detail.lookupKey)
    val firstPhone = detail.phones.firstOrNull()?.value
    val firstEmail = detail.emails.firstOrNull()?.value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            SecaAvatar(
                initials = initialsOf(detail.displayName),
                photoUri = null,
                size = 136.dp,
                tone = ui.toneOf(profile),
                seed = detail.displayName,
                photo = rememberContactPhoto(detail.id),
                expressive = true,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = detail.displayName,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp),
            )
            ProfilePill(
                ui = ui,
                profile = profile,
                onAssign = { viewModel.assignProfile(detail.lookupKey, it.id) },
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 28.dp),
        ) {
            ActionButton(SecaIcons.Phone, "Appeler", enabled = firstPhone != null, Modifier.weight(1f), emphasized = true) {
                firstPhone?.let { dial(context, it) }
            }
            ActionButton(SecaIcons.Messages, "Message", enabled = firstPhone != null, Modifier.weight(1f)) {
                firstPhone?.let { sms(context, it) }
            }
            ActionButton(SecaIcons.Email, "E-mail", enabled = firstEmail != null, Modifier.weight(1f)) {
                firstEmail?.let { email(context, it) }
            }
        }

        SecaSectionLabel("Coordonnées")
        val count = detail.phones.size + detail.emails.size
        if (count == 0) {
            SecaGroupItem(index = 0, count = 1) {
                Text(
                    text = "Aucun numéro ni adresse e-mail.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
        detail.phones.forEachIndexed { index, field ->
            SecaGroupItem(index = index, count = count) {
                FieldRow(
                    icon = SecaIcons.Phone,
                    value = ui.numbers.display(field.value),
                    label = Phone.getTypeLabel(resources, field.type, "").toString(),
                    onClick = { dial(context, field.value) },
                ) {
                    FilledTonalIconButton(onClick = { sms(context, field.value) }) {
                        Icon(SecaIcons.Messages, contentDescription = "Envoyer un message")
                    }
                }
            }
        }
        detail.emails.forEachIndexed { index, field ->
            SecaGroupItem(index = detail.phones.size + index, count = count) {
                FieldRow(
                    icon = SecaIcons.Email,
                    value = field.value,
                    label = Email.getTypeLabel(resources, field.type, "").toString(),
                    onClick = { email(context, field.value) },
                )
            }
        }
    }
}

/** The contact's profile; tapping it moves the contact to another one. */
@Composable
private fun ProfilePill(
    ui: ContactsUi,
    profile: Profile,
    onAssign: (Profile) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClickLabel = "Changer de profil") { open = true }
                .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        ) {
            SecaProfileBadge(profile.name, tone = ui.toneOf(profile), size = 28.dp)
            Text(
                text = profile.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp),
            )
            Icon(SecaIcons.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ProfileMenuItems(
                ui = ui,
                selectedId = profile.id,
                onSelect = {
                    open = false
                    onAssign(it)
                },
            )
        }
    }
}

/** A large tonal button with its label under the icon; [emphasized] fills it with the accent. */
@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val container = when {
        !enabled -> colors.surfaceContainerHigh
        emphasized -> colors.primary
        else -> colors.secondaryContainer
    }
    val content = when {
        !enabled -> colors.onSurface.copy(alpha = 0.38f)
        emphasized -> colors.onPrimary
        else -> colors.onSecondaryContainer
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(container)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content)
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun FieldRow(
    icon: ImageVector,
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
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(
            Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (label.isNotEmpty()) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}
