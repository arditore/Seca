package com.seca.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seca.core.design.SecaIcons
import com.seca.core.design.component.SecaChoicePill
import com.seca.core.design.component.SecaProfileBadge
import com.seca.core.model.Profile
import com.seca.phone.screening.BlockMode

/** The blocked profiles, as Seca Contacts names them; none without Seca Contacts. */
internal val PhoneUi.blockedProfileList: List<Profile>
    get() = if (profiles.connected) profiles.profiles.filter { it.id in blockedProfiles } else emptyList()

/** Picks the profiles whose contacts cannot call, and what their calls become. */
@Composable
internal fun BlockedProfilesDialog(ui: PhoneUi, viewModel: PhoneViewModel, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val counts = remember(ui.contacts, ui.profiles) { ui.contacts.groupingBy { ui.profileOf(it).id }.eachCount() }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(SecaIcons.Block, contentDescription = null) },
        title = { Text("Profils bloqués") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Les contacts d'un profil bloqué ne peuvent plus vous appeler, jusqu'à ce que vous le réactiviez.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                ui.profiles.profiles.forEach { profile ->
                    val blocked = profile.id in ui.blockedProfiles
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .toggleable(value = blocked, role = Role.Switch) { viewModel.setProfileBlocked(profile.id, it) }
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                    ) {
                        SecaProfileBadge(profile.name, tone = ui.profiles.toneOf(profile), size = 36.dp)
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                        ) {
                            Text(
                                text = profile.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = contactCount(counts[profile.id] ?: 0),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        Switch(checked = blocked, onCheckedChange = null)
                    }
                }
                Text(
                    text = "Quand ils appellent",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecaChoicePill("Refuser", selected = ui.blockMode == BlockMode.Decline, onClick = { viewModel.setBlockMode(BlockMode.Decline) })
                    SecaChoicePill("En silence", selected = ui.blockMode == BlockMode.Silence, onClick = { viewModel.setBlockMode(BlockMode.Silence) })
                }
                Text(
                    text = if (ui.blockMode == BlockMode.Decline) {
                        "L'appel est refusé sans sonner et reste dans l'historique, parmi les refusés."
                    } else {
                        "L'appel s'affiche sans sonner ni vibrer : vous répondez si vous le voulez."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

/** Stays at the top of the history while profiles are blocked, so it is never forgotten. */
@Composable
internal fun BlockedProfilesBanner(ui: PhoneUi, onManage: () -> Unit, onUnblock: () -> Unit, modifier: Modifier = Modifier) {
    val blocked = ui.blockedProfileList
    if (blocked.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onManage,
        color = colors.tertiaryContainer,
        contentColor = colors.onTertiaryContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Icon(SecaIcons.Block, contentDescription = null)
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = "Appels bloqués : " + blocked.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (ui.blockMode == BlockMode.Decline) "Leurs appels sont refusés" else "Leurs appels sonnent en silence",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            TextButton(
                onClick = onUnblock,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.onTertiaryContainer),
            ) { Text("Réactiver") }
        }
    }
}

private fun contactCount(count: Int): String = when (count) {
    0 -> "Aucun contact"
    1 -> "1 contact"
    else -> "$count contacts"
}
