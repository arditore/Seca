package com.seca.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.seca.core.design.SecaAppIdentity
import com.seca.core.design.SecaIcons
import com.seca.core.design.SecaPalette
import com.seca.core.design.SecaTheme
import com.seca.core.design.component.SecaAvatar
import com.seca.core.model.initialsOf

@Composable
internal fun SettingsScreen(ui: ContactsUi, viewModel: ContactsViewModel) {
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Profile?>(null) }
    var deleting by remember { mutableStateOf<Profile?>(null) }

    Scaffold(topBar = { SimpleTopBar(title = "Paramètres", onBack = { viewModel.back() }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SectionHeader("Palette")
            Hint("Le thème clair ou sombre suit celui du téléphone.")
            SecaPalette.entries.forEach { palette ->
                PaletteRow(palette, selected = palette == ui.palette, onClick = { viewModel.setPalette(palette) })
            }

            SectionHeader("Profils")
            Hint("Rangez vos contacts par profil, comme Travail ou Famille. Ils restent sur ce téléphone.")
            ui.profiles.forEach { profile ->
                ProfileRow(
                    profile = profile,
                    count = ui.contacts.count { ui.profileOf(it).id == profile.id },
                    editable = profile.id != ProfileStore.Principal.id,
                    onRename = { renaming = profile },
                    onDelete = { deleting = profile },
                )
            }
            TextButton(onClick = { adding = true }, modifier = Modifier.padding(start = 8.dp)) {
                Icon(SecaIcons.Add, contentDescription = null)
                Text("Ajouter un profil", modifier = Modifier.padding(start = 8.dp))
            }

            SectionHeader("Confidentialité")
            Hint(
                "Seca Contacts n'a pas accès à Internet. Vos contacts, vos profils et vos réglages " +
                    "restent sur ce téléphone, et les contacts que vous créez ne sont synchronisés avec aucun compte.",
            )
        }
    }

    if (adding) {
        ProfileNameDialog(
            title = "Nouveau profil",
            initial = "",
            confirmLabel = "Créer",
            onDismiss = { adding = false },
            onConfirm = {
                viewModel.addProfile(it)
                adding = false
            },
        )
    }
    renaming?.let { profile ->
        ProfileNameDialog(
            title = "Renommer le profil",
            initial = profile.name,
            confirmLabel = "Renommer",
            onDismiss = { renaming = null },
            onConfirm = {
                viewModel.renameProfile(profile.id, it)
                renaming = null
            },
        )
    }
    deleting?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Supprimer « ${profile.name} » ?") },
            text = { Text("Ses contacts reviennent dans Principal. Aucun contact n'est supprimé.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProfile(profile.id)
                        deleting = null
                    },
                ) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
}

@Composable
private fun PaletteRow(palette: SecaPalette, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        // One swatch per Seca app, each in its own identity within this palette.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SecaAppIdentity.entries.forEach { identity ->
                SecaTheme(identity = identity, palette = palette) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
        Text(
            text = palette.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        )
        if (selected) {
            Icon(SecaIcons.Check, contentDescription = "Palette choisie", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ProfileRow(
    profile: Profile,
    count: Int,
    editable: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
    ) {
        SecaAvatar(initials = initialsOf(profile.name).take(1), photoUri = null, size = 36.dp)
        Column(
            Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(profile.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = if (count == 1) "1 contact" else "$count contacts",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (editable) {
            IconButton(onClick = onRename) { Icon(SecaIcons.Edit, contentDescription = "Renommer") }
            IconButton(onClick = onDelete) { Icon(SecaIcons.Delete, contentDescription = "Supprimer") }
        }
    }
}
