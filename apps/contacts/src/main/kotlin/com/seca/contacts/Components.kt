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
import com.seca.core.design.component.SecaDialog
import com.seca.core.design.SecaIcons
import com.seca.core.design.component.SecaProfileBadge
import com.seca.core.model.Profile
import com.seca.core.design.label
import androidx.compose.ui.res.stringResource

@Composable
internal fun ProfileNameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    SecaDialog(
        onDismiss = onDismiss,
        icon = SecaIcons.Label,
        title = title,
        confirmLabel = confirmLabel,
        dismissLabel = stringResource(R.string.cancel),
        onConfirm = { onConfirm(name.trim()) },
        content = {
        OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.profile_name)) },
                        shape = MaterialTheme.shapes.medium,
                    )
        },
    )
}

/** Dropdown entries for choosing a profile, with a check on [selectedId]. */
@Composable
internal fun ProfileMenuItems(ui: ContactsUi, selectedId: String, onSelect: (Profile) -> Unit) {
    ui.profiles.forEachIndexed { index, profile ->
        DropdownMenuItem(
            text = { Text(profile.label()) },
            leadingIcon = { SecaProfileBadge(profile.label(), tone = index, size = 28.dp) },
            trailingIcon = if (profile.id == selectedId) {
                { Icon(SecaIcons.Check, contentDescription = stringResource(R.string.current_profile)) }
            } else {
                null
            },
            onClick = { onSelect(profile) },
        )
    }
}
