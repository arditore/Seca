package com.seca.phone

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seca.core.design.SecaIcons
import com.seca.core.design.component.SecaChoicePill
import com.seca.core.design.component.SecaProfileBadge
import com.seca.core.model.Profile
import com.seca.phone.screening.BlockFor
import com.seca.phone.screening.BlockMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.annotation.StringRes
import com.seca.core.design.label
import com.seca.core.model.uiLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

/** The profiles blocked right now, as Seca Contacts names them; none without Seca Contacts, or once the block has ended. */
internal val PhoneUi.blockedProfileList: List<Profile>
    get() {
        if (!profiles.connected || blockedUntil in 1..System.currentTimeMillis()) return emptyList()
        return profiles.profiles.filter { it.id in blockedProfiles }
    }

/** Picks the profiles whose contacts cannot call, for how long, and what their calls become. */
@Composable
internal fun BlockedProfilesDialog(ui: PhoneUi, viewModel: PhoneViewModel, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val counts = remember(ui.contacts, ui.profiles) { ui.contacts.groupingBy { ui.profileOf(it).id }.eachCount() }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(SecaIcons.Block, contentDescription = null) },
        title = { Text(stringResource(R.string.blocked_profiles)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.blocked_profiles_dialog_hint),
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
                        SecaProfileBadge(profile.label(), tone = ui.profiles.toneOf(profile), size = 36.dp)
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                        ) {
                            Text(
                                text = profile.label(),
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

                DialogLabel(stringResource(R.string.block_duration))
                BlockFor.entries.chunked(2).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp),
                    ) {
                        row.forEach { choice ->
                            SecaChoicePill(stringResource(durationLabel(choice)), selected = ui.blockFor == choice, onClick = { viewModel.setBlockFor(choice) })
                        }
                    }
                }

                DialogLabel(stringResource(R.string.when_they_call))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecaChoicePill(stringResource(R.string.decline), selected = ui.blockMode == BlockMode.Decline, onClick = { viewModel.setBlockMode(BlockMode.Decline) })
                    SecaChoicePill(stringResource(R.string.silently), selected = ui.blockMode == BlockMode.Silence, onClick = { viewModel.setBlockMode(BlockMode.Silence) })
                }
                Text(
                    text = stringResource(if (ui.blockMode == BlockMode.Decline) R.string.block_decline_hint else R.string.block_silence_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } },
    )
}

@Composable
private fun DialogLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

/** Stays at the top of the history while profiles are blocked, so it is never forgotten. */
@Composable
internal fun BlockedProfilesBanner(ui: PhoneUi, onManage: () -> Unit, onUnblock: () -> Unit, modifier: Modifier = Modifier) {
    val blocked = ui.blockedProfileList
    if (blocked.isEmpty()) return
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val calls = stringResource(if (ui.blockMode == BlockMode.Decline) R.string.blocked_declined else R.string.blocked_silenced)
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
                    text = stringResource(R.string.blocked_calls, blocked.joinToString(", ") { it.label(context) }),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (ui.blockedUntil > 0) stringResource(R.string.blocked_until, calls, endLabel(context, ui.blockedUntil)) else calls,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            TextButton(
                onClick = onUnblock,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.onTertiaryContainer),
            ) { Text(stringResource(R.string.unblock)) }
        }
    }
}

@StringRes
private fun durationLabel(choice: BlockFor): Int = when (choice) {
    BlockFor.UntilLifted -> R.string.block_until_lifted
    BlockFor.OneHour -> R.string.block_one_hour
    BlockFor.UntilMorning -> R.string.block_until_morning
    BlockFor.UntilMonday -> R.string.block_until_monday
}

/** "18:30" today, "tomorrow 08:00", else the day's name. */
private fun endLabel(context: Context, millis: Long): String {
    val day = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    val time = timeOf(context, millis)
    return when (day) {
        today -> time
        today.plusDays(1) -> context.getString(R.string.tomorrow_at, time)
        else -> "${day.format(DateTimeFormatter.ofPattern("EEEE", uiLocale()))} $time"
    }
}

@Composable
private fun contactCount(count: Int): String =
    if (count == 0) stringResource(R.string.no_contacts) else pluralStringResource(R.plurals.contacts_count, count, count)
