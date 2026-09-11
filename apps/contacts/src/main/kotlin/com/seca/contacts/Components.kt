package com.seca.contacts

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.seca.core.design.SecaIcons
import com.seca.core.design.component.SecaProfileBadge
import com.seca.core.model.Profile

@Composable
internal fun ProfileNameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(SecaIcons.Label, contentDescription = null) },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Nom du profil") },
                shape = MaterialTheme.shapes.medium,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Dropdown entries for choosing a profile, with a check on [selectedId]. */
@Composable
internal fun ProfileMenuItems(ui: ContactsUi, selectedId: String, onSelect: (Profile) -> Unit) {
    ui.profiles.forEachIndexed { index, profile ->
        DropdownMenuItem(
            text = { Text(profile.name) },
            leadingIcon = { SecaProfileBadge(profile.name, tone = index, size = 28.dp) },
            trailingIcon = if (profile.id == selectedId) {
                { Icon(SecaIcons.Check, contentDescription = "Profil actuel") }
            } else {
                null
            },
            onClick = { onSelect(profile) },
        )
    }
}
