package com.seca.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seca.core.design.SecaIcons
import com.seca.core.design.secaToneColors
import com.seca.core.model.initialsOf

/** A group's title, lined up with the content of the rounded group under it. */
@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 8.dp),
    )
}

/** A line of explanation under a group. */
@Composable
internal fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp),
    )
}

/** A back or close button, a title, and optional actions at the end. */
@Composable
internal fun SimpleTopBar(
    title: String,
    onBack: () -> Unit,
    navigationIcon: ImageVector = SecaIcons.Back,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .height(64.dp)
            .padding(horizontal = 4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(navigationIcon, contentDescription = if (navigationIcon == SecaIcons.Close) "Fermer" else "Retour")
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
        )
        actions()
    }
}

/**
 * A profile's badge: its initial on the profile's own accent, so profiles can
 * be told apart at a glance. [tone] is the profile's position in the list.
 */
@Composable
internal fun ProfileBadge(name: String, tone: Int, size: Dp = 32.dp) {
    val (container, content) = secaToneColors(tone, strong = true)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(container),
    ) {
        Text(
            text = initialsOf(name).take(1).ifEmpty { "?" },
            color = content,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = (size.value * 0.44f).sp,
                lineHeight = (size.value * 0.44f).sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

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
            leadingIcon = { ProfileBadge(profile.name, tone = index, size = 28.dp) },
            trailingIcon = if (profile.id == selectedId) {
                { Icon(SecaIcons.Check, contentDescription = "Profil actuel") }
            } else {
                null
            },
            onClick = { onSelect(profile) },
        )
    }
}
