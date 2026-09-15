package com.seca.phone

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.seca.core.design.SecaIcons
import com.seca.core.design.SecaPalette
import com.seca.core.design.component.SecaGroupItem
import com.seca.core.design.component.SecaHint
import com.seca.core.design.component.SecaPaletteSwatch
import com.seca.core.design.component.SecaSectionLabel
import com.seca.core.design.component.SecaSettingRow
import com.seca.core.design.component.SecaTopBar
import com.seca.core.design.privacy.SecaPrivacySettingsGroup
import com.seca.core.suite.SuiteBackupSection
import com.seca.phone.screening.BlockMode
import com.seca.phone.screening.ScreeningSettings
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.seca.core.design.label
import androidx.compose.ui.res.stringResource

/** A way into one of the system's own call screens; [intents] are tried in order. */
private data class SystemEntry(val icon: ImageVector, val title: String, val subtitle: String, val intents: List<Intent>)

@Composable
internal fun SettingsScreen(
    ui: PhoneUi,
    viewModel: PhoneViewModel,
    withCallLogWrite: (() -> Unit) -> Unit,
    isDefaultDialer: Boolean,
    onBecomeDefault: () -> Unit,
) {
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    val dynamic = ui.palette == null
    // The suite's backup brings calls back only with the right to read and write the history: asked before the file is chosen.
    var afterCallLogAsked by remember { mutableStateOf<(() -> Unit)?>(null) }
    val callLogAccess = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        afterCallLogAsked?.invoke()
        afterCallLogAsked = null
    }
    val askCallLogThen: (() -> Unit) -> Unit = { action ->
        afterCallLogAsked = action
        callLogAccess.launch(arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.WRITE_CALL_LOG))
    }
    val unavailableText = stringResource(R.string.unavailable)
    val calls = listOf(
        SystemEntry(SecaIcons.Wifi, stringResource(R.string.wifi_calling), stringResource(R.string.wifi_calling_hint), wifiCallingSettings()),
        SystemEntry(
            SecaIcons.Phone,
            stringResource(R.string.carrier_settings),
            stringResource(R.string.carrier_settings_hint),
            listOf(callSettings()),
        ),
        SystemEntry(SecaIcons.Voicemail, stringResource(R.string.voicemail), stringResource(R.string.voicemail_settings_hint), listOf(voicemailSettings())),
        SystemEntry(
            SecaIcons.Block,
            stringResource(R.string.blocked_numbers),
            stringResource(R.string.blocked_numbers_hint),
            listOfNotNull(blockedNumbers(context)),
        ),
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { SecaTopBar(title = "", onBack = { viewModel.back() }) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
            )

            SecaSectionLabel(stringResource(R.string.phone_app))
            SecaGroupItem(
                index = 0,
                count = 1,
                onClick = {
                    if (isDefaultDialer) {
                        openSystemScreen(context, listOf(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)))
                    } else {
                        onBecomeDefault()
                    }
                },
            ) {
                SecaSettingRow(
                    icon = SecaIcons.Phone,
                    title = stringResource(if (isDefaultDialer) R.string.default_on else R.string.default_off),
                    subtitle = stringResource(if (isDefaultDialer) R.string.default_on_hint else R.string.default_off_hint),
                    trailing = if (isDefaultDialer) {
                        { Icon(SecaIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    },
                )
            }
            SecaHint(stringResource(R.string.default_hint))

            SecaSectionLabel(stringResource(R.string.call_filtering))
            val screening = remember { ScreeningSettings(context) }
            var blockTelemarketing by remember { mutableStateOf(screening.blockTelemarketing) }
            var silenceUnknown by remember { mutableStateOf(screening.silenceUnknown) }
            SecaGroupItem(index = 0, count = 3) {
                SecaSettingRow(
                    icon = SecaIcons.Shield,
                    title = stringResource(R.string.block_telemarketing),
                    subtitle = stringResource(R.string.block_telemarketing_hint),
                    modifier = Modifier.toggleable(value = blockTelemarketing, role = Role.Switch) {
                        blockTelemarketing = it
                        screening.blockTelemarketing = it
                    },
                    trailing = { Switch(checked = blockTelemarketing, onCheckedChange = null) },
                )
            }
            SecaGroupItem(index = 1, count = 3) {
                SecaSettingRow(
                    icon = SecaIcons.VolumeOff,
                    title = stringResource(R.string.silence_unknown),
                    subtitle = stringResource(R.string.silence_unknown_hint),
                    modifier = Modifier.toggleable(value = silenceUnknown, role = Role.Switch) {
                        silenceUnknown = it
                        screening.silenceUnknown = it
                    },
                    trailing = { Switch(checked = silenceUnknown, onCheckedChange = null) },
                )
            }
            var managingBlocked by remember { mutableStateOf(false) }
            val blockedProfiles = ui.blockedProfileList
            SecaGroupItem(index = 2, count = 3, onClick = if (ui.profiles.connected) ({ managingBlocked = true }) else null) {
                SecaSettingRow(
                    icon = SecaIcons.Block,
                    title = stringResource(R.string.blocked_profiles),
                    subtitle = when {
                        !ui.profiles.connected -> stringResource(R.string.blocked_profiles_needs_contacts)
                        blockedProfiles.isEmpty() -> stringResource(R.string.blocked_profiles_none)
                        ui.blockMode == BlockMode.Decline ->
                            stringResource(R.string.blocked_profiles_declined, blockedProfiles.joinToString(", ") { it.label(context) })
                        else -> stringResource(R.string.blocked_profiles_silenced, blockedProfiles.joinToString(", ") { it.label(context) })
                    },
                )
            }
            if (managingBlocked) BlockedProfilesDialog(ui, viewModel, onDismiss = { managingBlocked = false })
            SecaHint(stringResource(if (isDefaultDialer) R.string.filtering_local else R.string.filtering_needs_default))

            SecaSectionLabel(stringResource(R.string.colors))
            if (ui.profiles.connected) {
                SecaGroupItem(index = 0, count = 2) {
                    SecaSettingRow(
                        icon = SecaIcons.AutoAwesome,
                        title = stringResource(R.string.dynamic_colors),
                        subtitle = stringResource(R.string.dynamic_colors_hint),
                        modifier = Modifier.toggleable(
                            value = dynamic,
                            role = Role.Switch,
                            onValueChange = { on -> viewModel.setPalette(if (on) null else SecaPalette.Ocean) },
                        ),
                        trailing = { Switch(checked = dynamic, onCheckedChange = null) },
                    )
                }
                SecaGroupItem(index = 1, count = 2) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        SecaPalette.entries.forEach { palette ->
                            SecaPaletteSwatch(
                                palette = palette,
                                selected = ui.palette == palette,
                                onClick = { viewModel.setPalette(palette) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                SecaHint(stringResource(R.string.colors_shared_hint))
            } else {
                SecaHint(stringResource(R.string.colors_need_contacts))
            }

            SecaSectionLabel(stringResource(R.string.calls))
            calls.forEachIndexed { index, entry ->
                SecaGroupItem(
                    index = index,
                    count = calls.size,
                    onClick = {
                        if (!openSystemScreen(context, entry.intents)) {
                            Toast.makeText(context, unavailableText, Toast.LENGTH_SHORT).show()
                        }
                    },
                ) {
                    SecaSettingRow(icon = entry.icon, title = entry.title, subtitle = entry.subtitle)
                }
            }

            SecaSectionLabel(stringResource(R.string.history))
            SecaGroupItem(index = 0, count = 1, onClick = { confirmClear = true }) {
                SecaSettingRow(
                    icon = SecaIcons.Delete,
                    title = stringResource(R.string.clear_history),
                    subtitle = stringResource(R.string.clear_history_hint),
                )
            }

            SuiteBackupSection(beforeRestore = askCallLogThen)

            SecaSectionLabel(stringResource(R.string.protection))
            SecaPrivacySettingsGroup(stringResource(R.string.app_name))

            SecaSectionLabel(stringResource(R.string.privacy))
            SecaGroupItem(index = 0, count = 1) {
                SecaSettingRow(
                    icon = SecaIcons.Lock,
                    title = stringResource(R.string.privacy_title),
                    subtitle = stringResource(R.string.privacy_text),
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { Icon(SecaIcons.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.clear_history_title)) },
            text = { Text(stringResource(R.string.clear_history_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        withCallLogWrite { viewModel.clearHistory() }
                    },
                ) { Text(stringResource(R.string.erase)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
