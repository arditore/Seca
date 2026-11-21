package com.seca.phone

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.OutlinedTextField
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
import com.seca.core.contacts.PhoneNumbers
import com.seca.core.design.component.SecaDialog
import com.seca.core.design.SecaIcons
import com.seca.core.design.SecaPalette
import com.seca.core.design.component.SecaCalendarSection
import com.seca.core.model.SecaCalendar
import com.seca.core.design.component.SecaGroupItem
import com.seca.core.design.component.SecaHint
import com.seca.core.design.component.SecaColourPerPersonRow
import com.seca.core.design.component.SecaPaletteSection
import com.seca.core.design.component.SecaShapeSection
import com.seca.core.design.component.SecaPaletteSwatch
import com.seca.core.design.component.SecaSectionLabel
import com.seca.core.design.component.SecaSettingRow
import com.seca.core.design.component.SecaSwitchRow
import com.seca.core.design.component.SecaTopBar
import com.seca.core.design.privacy.SecaPrivacySettingsGroup
import com.seca.core.suite.AutoBackupSection
import com.seca.core.suite.SuiteBackupSection
import com.seca.core.suite.LanguageSection
import com.seca.core.suite.SuiteNamesSection
import com.seca.core.suite.SuiteOrderSection
import com.seca.phone.call.InCallSettings
import com.seca.phone.screening.BlockMode
import com.seca.phone.screening.ScreeningSettings
import com.seca.phone.screening.Telemarketing
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.seca.core.design.label
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler

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
    )

    // Which part of the settings is showing. State rather than a route of its own: it is one
    // screen showing one of its parts, and the back gesture already means "the part before".
    var page by remember { mutableStateOf(SettingsPage.Master) }
    BackHandler(enabled = page != SettingsPage.Master) { page = SettingsPage.Master }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            SecaTopBar(
                title = "",
                onBack = { if (page == SettingsPage.Master) viewModel.back() else page = SettingsPage.Master },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(page.title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
            )

            when (page) {
                SettingsPage.Master -> {
                    SettingsEntry(
                        icon = SecaIcons.Palette,
                        title = stringResource(R.string.colors),
                        subtitle = stringResource(R.string.settings_appearance_hint),
                        index = 0,
                        count = 5,
                    ) { page = SettingsPage.Appearance }
                    SettingsEntry(
                        icon = SecaIcons.Phone,
                        title = stringResource(R.string.calls),
                        subtitle = stringResource(R.string.settings_calls_hint),
                        index = 1,
                        count = 5,
                    ) { page = SettingsPage.Calls }
                    SettingsEntry(
                        icon = SecaIcons.Block,
                        title = stringResource(R.string.call_filtering),
                        subtitle = stringResource(R.string.settings_filtering_hint),
                        index = 2,
                        count = 5,
                    ) { page = SettingsPage.Filtering }
                    SettingsEntry(
                        icon = SecaIcons.Lock,
                        title = stringResource(R.string.backup),
                        subtitle = stringResource(R.string.settings_backup_hint),
                        index = 3,
                        count = 5,
                    ) { page = SettingsPage.Backup }
                    SettingsEntry(
                        icon = SecaIcons.Shield,
                        title = stringResource(R.string.protection),
                        subtitle = stringResource(R.string.settings_privacy_hint),
                        index = 4,
                        count = 5,
                    ) { page = SettingsPage.Privacy }
                    SecaCalendarSection(SecaCalendar.named(ui.profiles.calendar)) { viewModel.setCalendar(it) }
                    LanguageSection()
                }
                SettingsPage.Appearance -> {
                    if (ui.profiles.connected) {
                        SecaGroupItem(index = 0, count = 3) {
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
                        SecaGroupItem(index = 1, count = 3) {
                            SecaSwitchRow(
                                icon = SecaIcons.DarkMode,
                                title = stringResource(R.string.black_theme),
                                subtitle = stringResource(R.string.black_theme_hint),
                                checked = ui.black,
                                onCheckedChange = viewModel::setBlack,
                            )
                        }
                        SecaGroupItem(index = 2, count = 5) {
                            SecaPaletteSection(ui.palette, dynamic, viewModel::setPalette)
                        }
                        SecaGroupItem(index = 3, count = 5) {
                            SecaColourPerPersonRow(ui.colourPerPerson, viewModel::setColourPerPerson)
                        }
                        SecaGroupItem(index = 4, count = 5) {
                            SecaShapeSection(ui.avatarShape, viewModel::setAvatarShape)
                        }
                        SecaHint(stringResource(R.string.colors_shared_hint))
                    } else {
                        SecaHint(stringResource(R.string.colors_need_contacts))
                    }
                    SuiteOrderSection()
                    SuiteNamesSection()
                }
                SettingsPage.Calls -> {
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
                    SecaSectionLabel(stringResource(R.string.call_screen))
                    val inCall = remember { InCallSettings(context) }
                    var showLabels by remember { mutableStateOf(inCall.showLabels) }
                    var screenOffNearEar by remember { mutableStateOf(inCall.proximity) }
                    var swipeToAct by remember { mutableStateOf(inCall.swipeToAct) }
                    var editingReplies by remember { mutableStateOf(false) }
                    if (editingReplies) {
                        QuickRepliesDialog(settings = inCall, onDismiss = { editingReplies = false })
                    }
                    SecaGroupItem(index = 0, count = 4) {
                        SecaSwitchRow(
                            icon = SecaIcons.Subject,
                            title = stringResource(R.string.button_labels),
                            subtitle = stringResource(R.string.button_labels_hint),
                            checked = showLabels,
                            onCheckedChange = { on ->
                                showLabels = on
                                inCall.showLabels = on
                            },
                        )
                    }
                    SecaGroupItem(index = 1, count = 4) {
                        SecaSwitchRow(
                            icon = SecaIcons.VisibilityOff,
                            title = stringResource(R.string.screen_off_near_ear),
                            subtitle = stringResource(R.string.screen_off_near_ear_hint),
                            checked = screenOffNearEar,
                            onCheckedChange = { on ->
                                screenOffNearEar = on
                                inCall.proximity = on
                            },
                        )
                    }
                    SecaGroupItem(index = 2, count = 4) {
                        SecaSwitchRow(
                            icon = SecaIcons.ArrowUp,
                            title = stringResource(R.string.swipe_to_act),
                            subtitle = stringResource(R.string.swipe_to_act_hint),
                            checked = swipeToAct,
                            onCheckedChange = { on ->
                                swipeToAct = on
                                inCall.swipeToAct = on
                            },
                        )
                    }
                    SecaGroupItem(index = 3, count = 4, onClick = { editingReplies = true }) {
                        SecaSettingRow(
                            icon = SecaIcons.Messages,
                            title = stringResource(R.string.decline_and_reply),
                            subtitle = stringResource(R.string.decline_and_reply_hint),
                        )
                    }
                    SecaHint(stringResource(R.string.call_screen_hint))

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
                }
                SettingsPage.Filtering -> {
                    val screening = remember { ScreeningSettings(context) }
                    var blockTelemarketing by remember { mutableStateOf(screening.blockTelemarketing) }
                    var silenceUnknown by remember { mutableStateOf(screening.silenceUnknown) }
                    // The telemarketing ranges are France's, set by its regulator, and the
                    // filter can do nothing anywhere else: offering it to somebody in Poland is
                    // a switch that promises something and then never fires (#77).
                    //
                    // By the SIM's country, not the phone's language. A French speaker in
                    // Belgium has no use for it and an English speaker in France does, which is
                    // the opposite of what the language would have said.
                    val inFrance = remember { Telemarketing.offeredIn(PhoneNumbers.detectRegion(context)) }
                    // One more row where the phone lets this app see the blocked list (#user).
                    val blockedList = remember { BlockedNumbers.available(context) }
                    val rows = (if (inFrance) 3 else 2) + if (blockedList) 1 else 0
                    if (inFrance) {
                        SecaGroupItem(index = 0, count = rows) {
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
                    }
                    SecaGroupItem(index = rows - (if (blockedList) 3 else 2), count = rows) {
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
                    SecaGroupItem(
                        index = rows - (if (blockedList) 2 else 1),
                        count = rows,
                        onClick = if (ui.profiles.connected) ({ managingBlocked = true }) else null,
                    ) {
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
                    // Android's own list, kept here rather than behind a trip to the system
                    // settings: undoing something you did in this app should not need another.
                    if (blockedList) {
                        SecaGroupItem(index = rows - 1, count = rows, onClick = { page = SettingsPage.Blocked }) {
                            SecaSettingRow(
                                icon = SecaIcons.Block,
                                title = stringResource(R.string.blocked_numbers),
                                subtitle = stringResource(R.string.blocked_numbers_hint),
                            )
                        }
                    }
                    SecaHint(stringResource(if (isDefaultDialer) R.string.filtering_local else R.string.filtering_needs_default))
                }
                SettingsPage.Blocked -> BlockedNumbersPage(ui)
                SettingsPage.Backup -> {
                    SuiteBackupSection(beforeRestore = askCallLogThen)
                    AutoBackupSection()
                }
                SettingsPage.Privacy -> {
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
        }
    }

    if (confirmClear) {
        SecaDialog(
            onDismiss = { confirmClear = false },
            icon = SecaIcons.Delete,
            title = stringResource(R.string.clear_history_title),
            message = stringResource(R.string.clear_history_text),
            confirmLabel = stringResource(R.string.erase),
            dismissLabel = stringResource(R.string.cancel),
            destructive = true,
            onConfirm = {
                        confirmClear = false
                        withCallLogWrite { viewModel.clearHistory() }
                    },
        )
    }
}

/**
 * The four sentences offered when a call is declined with a message.
 *
 * Asked for by someone whose phone lets its own dialer's four be edited, and who found Seca's
 * fixed. Android keeps no shared list — each dialer holds its own, where no other app can read
 * it — so this is Seca's own, and emptying a line puts the shipped sentence back.
 */
@Composable
private fun QuickRepliesDialog(settings: InCallSettings, onDismiss: () -> Unit) {
    val defaults = remember(settings) { settings.defaultReplies }
    val texts = remember(settings) { settings.replies.toMutableStateList() }
    SecaDialog(
        onDismiss = onDismiss,
        icon = SecaIcons.Messages,
        title = stringResource(R.string.decline_and_reply),
        confirmLabel = stringResource(R.string.save),
        dismissLabel = stringResource(R.string.cancel),
        onConfirm = {
                    settings.replies = texts
                    onDismiss()
                },
        content = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        texts.forEachIndexed { index, text ->
                            OutlinedTextField(
                                value = text,
                                onValueChange = { texts[index] = it },
                                placeholder = { Text(defaults[index]) },
                                singleLine = false,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
        },
    )
}

/** Which part of the settings is showing. */
private enum class SettingsPage(val title: Int) {
    Master(R.string.settings),
    Appearance(R.string.colors),
    Calls(R.string.calls),
    Filtering(R.string.call_filtering),
    Blocked(R.string.blocked_numbers),
    Backup(R.string.backup),
    Privacy(R.string.protection),
}

/** One row of the first page: what is in there, and the way in. */
@Composable
private fun SettingsEntry(
    icon: ImageVector,
    title: String,
    subtitle: String,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    SecaGroupItem(index = index, count = count, onClick = onClick) {
        SecaSettingRow(icon = icon, title = title, subtitle = subtitle)
    }
}
