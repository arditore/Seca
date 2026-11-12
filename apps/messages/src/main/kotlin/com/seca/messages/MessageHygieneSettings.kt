package com.seca.messages

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.seca.core.design.SecaIcons
import com.seca.core.design.component.SecaChoicePill
import com.seca.core.design.component.SecaGroupItem
import com.seca.core.design.component.SecaHint
import com.seca.core.design.component.SecaSectionLabel
import com.seca.core.design.component.SecaSettingRow
import com.seca.messages.sms.CodeCleanup
import com.seca.messages.sms.DeliveryReports
import com.seca.messages.sms.CodeLifetime
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.pluralStringResource
import com.seca.messages.sms.SentSound
import com.seca.messages.sms.SpamSweep
import com.seca.messages.sms.SweptConversation
import com.seca.messages.sms.SpamFilter
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource

/**
 * Verification codes that erase themselves, and advertising filed away.
 *
 * [onFile] does the filing rather than this screen, because filing has to be felt everywhere at
 * once: the conversation list, the counter on the Spam row and any notification still showing.
 * Writing the preference here left all three stale until the app was restarted, which read as
 * the button doing nothing at all.
 */
@Composable
internal fun CodeAndSpamSettings(onFile: (List<SweptConversation>) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lifetime by remember { mutableStateOf(CodeCleanup.lifetime(context)) }
    var filterSpam by remember { mutableStateOf(SpamFilter.enabled(context)) }
    var deliveryReports by remember { mutableStateOf(DeliveryReports.enabled(context)) }
    var sentSound by remember { mutableStateOf(SentSound.enabled(context)) }
    var sweeping by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<List<SweptConversation>?>(null) }

    SecaSectionLabel(stringResource(R.string.verification_codes))
    SecaGroupItem(index = 0, count = 1) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            CodeLifetime.entries.forEach { choice ->
                SecaChoicePill(
                    label = stringResource(choice.label),
                    selected = lifetime == choice,
                    onClick = {
                        lifetime = choice
                        CodeCleanup.setLifetime(context, choice)
                        // Codes already past the new delay go at once.
                        scope.launch { runCatching { CodeCleanup.sweep(context) } }
                    },
                )
            }
        }
    }
    SecaHint(stringResource(R.string.codes_hint))

    SecaSectionLabel(stringResource(R.string.advertising_texts))
    SecaGroupItem(index = 0, count = 2) {
        SecaSettingRow(
            icon = SecaIcons.Block,
            title = stringResource(R.string.file_advertising),
            subtitle = stringResource(R.string.file_advertising_hint),
            modifier = Modifier.toggleable(
                value = filterSpam,
                role = Role.Switch,
                onValueChange = { on ->
                    filterSpam = on
                    SpamFilter.setEnabled(context, on)
                },
            ),
            trailing = { Switch(checked = filterSpam, onCheckedChange = null) },
        )
    }
    // The filter only ever ran on arriving messages, so anything older than Seca being the
    // messaging app was never looked at. This looks, and asks before moving anything.
    SecaGroupItem(
        index = 1,
        count = 2,
        onClick = {
            sweeping = true
            scope.launch {
                found = SpamSweep.look(context)
                sweeping = false
            }
        },
    ) {
        SecaSettingRow(
            icon = SecaIcons.Search,
            title = stringResource(R.string.sweep_old),
            subtitle = stringResource(R.string.sweep_old_hint),
        )
    }
    SecaHint(stringResource(R.string.advertising_hint))

    if (sweeping) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.sweep_looking)) },
            text = { Text(stringResource(R.string.sweep_looking_hint), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {},
        )
    }

    found?.let { conversations ->
        AlertDialog(
            onDismissRequest = { found = null },
            icon = { Icon(SecaIcons.Block, contentDescription = null) },
            title = {
                Text(
                    if (conversations.isEmpty()) {
                        stringResource(R.string.sweep_none)
                    } else {
                        pluralStringResource(R.plurals.sweep_found, conversations.size, conversations.size)
                    },
                )
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (conversations.isEmpty()) {
                        Text(stringResource(R.string.sweep_none_hint), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        // Named rather than counted: the owner decides on what they can see.
                        conversations.forEach { swept ->
                            Text(
                                text = swept.address,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = swept.snippet,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (conversations.isEmpty()) {
                    TextButton(onClick = { found = null }) { Text(stringResource(android.R.string.ok)) }
                } else {
                    TextButton(
                        onClick = {
                            onFile(conversations)
                            found = null
                        },
                    ) { Text(stringResource(R.string.sweep_file)) }
                }
            },
            dismissButton = {
                if (conversations.isNotEmpty()) {
                    TextButton(onClick = { found = null }) { Text(stringResource(android.R.string.cancel)) }
                }
            },
        )
    }

    SecaSectionLabel(stringResource(R.string.text_messages))
    SecaGroupItem(index = 0, count = 1) {
        SecaSettingRow(
            icon = SecaIcons.Check,
            title = stringResource(R.string.delivery_reports),
            subtitle = stringResource(R.string.delivery_reports_hint),
            modifier = Modifier.toggleable(
                value = deliveryReports,
                role = Role.Switch,
                onValueChange = { on ->
                    deliveryReports = on
                    DeliveryReports.setEnabled(context, on)
                },
            ),
            trailing = { Switch(checked = deliveryReports, onCheckedChange = null) },
        )
    }
    SecaHint(stringResource(R.string.delivery_reports_note))

    SecaSectionLabel(stringResource(R.string.sent_sound_section))
    SecaGroupItem(index = 0, count = 1) {
        SecaSettingRow(
            icon = SecaIcons.Send,
            title = stringResource(R.string.sent_sound),
            subtitle = stringResource(R.string.sent_sound_hint),
            modifier = Modifier.toggleable(
                value = sentSound,
                role = Role.Switch,
                onValueChange = { on ->
                    sentSound = on
                    SentSound.setEnabled(context, on)
                },
            ),
            trailing = { Switch(checked = sentSound, onCheckedChange = null) },
        )
    }
}
