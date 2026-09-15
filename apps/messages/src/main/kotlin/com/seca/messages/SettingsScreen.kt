package com.seca.messages

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.seca.core.design.component.SecaPassphraseDialog
import java.time.LocalDate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

@Composable
internal fun SettingsScreen(
    ui: MessagesUi,
    viewModel: MessagesViewModel,
    isDefaultApp: Boolean,
    onBecomeDefault: () -> Unit,
) {
    val context = LocalContext.current
    val dynamic = ui.palette == null
    val needDefaultText = stringResource(R.string.need_default_app)
    // An encrypted backup: the file is chosen first, then its passphrase is asked.
    var backupTarget by remember { mutableStateOf<Uri?>(null) }
    var restoreSource by remember { mutableStateOf<Uri?>(null) }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
        backupTarget = it
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { restoreSource = it }

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

            SecaSectionLabel(stringResource(R.string.sms_app))
            SecaGroupItem(
                index = 0,
                count = 1,
                onClick = { if (isDefaultApp) openDefaultAppsSettings(context) else onBecomeDefault() },
            ) {
                SecaSettingRow(
                    icon = SecaIcons.Messages,
                    title = stringResource(if (isDefaultApp) R.string.default_on else R.string.default_off),
                    subtitle = stringResource(if (isDefaultApp) R.string.default_on_hint else R.string.default_off_hint),
                    trailing = if (isDefaultApp) {
                        { Icon(SecaIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    },
                )
            }

            SecaSectionLabel(stringResource(R.string.notifications))
            SecaGroupItem(index = 0, count = 1, onClick = { openNotificationSettings(context) }) {
                SecaSettingRow(
                    icon = SecaIcons.Bell,
                    title = stringResource(R.string.notifications),
                    subtitle = stringResource(R.string.notifications_hint),
                )
            }

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

            SecaSectionLabel(stringResource(R.string.backup))
            SecaGroupItem(index = 0, count = 2, onClick = { backupLauncher.launch("seca-messages-${LocalDate.now()}.seca") }) {
                SecaSettingRow(
                    icon = SecaIcons.Lock,
                    title = stringResource(R.string.encrypted_backup),
                    subtitle = stringResource(R.string.encrypted_backup_hint),
                )
            }
            SecaGroupItem(
                index = 1,
                count = 2,
                onClick = {
                    if (isDefaultApp) {
                        restoreLauncher.launch(arrayOf("*/*"))
                    } else {
                        Toast.makeText(context, needDefaultText, Toast.LENGTH_LONG).show()
                    }
                },
            ) {
                SecaSettingRow(
                    icon = SecaIcons.Download,
                    title = stringResource(R.string.restore_backup),
                    subtitle = stringResource(R.string.restore_backup_hint),
                )
            }
            SecaHint(stringResource(R.string.backup_hint))

            SuiteBackupSection()

            CodeAndSpamSettings()

            SecaSectionLabel("Seca Link")
            val link = ui.link
            var askNetwork by remember { mutableStateOf(false) }
            val published = link.statuses.values.count { it.state == RelayState.Published }
            val linkRows = if (link.enabled) 6 else 1
            SecaGroupItem(index = 0, count = linkRows) {
                SecaSettingRow(
                    icon = SecaIcons.Link,
                    title = stringResource(R.string.link_encryption),
                    subtitle = when {
                        !link.enabled -> stringResource(R.string.link_off_hint)
                        link.error != null -> link.error
                        link.offline -> stringResource(R.string.link_offline)
                        link.statuses.values.any { it.state == RelayState.Publishing } -> stringResource(R.string.link_publishing)
                        published > 0 -> pluralStringResource(R.plurals.link_published, published, published)
                        link.statuses.isNotEmpty() -> stringResource(R.string.link_no_relay)
                        else -> stringResource(R.string.on)
                    },
                    modifier = Modifier.toggleable(
                        value = link.enabled,
                        role = Role.Switch,
                        onValueChange = { on ->
                            viewModel.setLinkEnabled(on)
                            // Android has no prompt for network access, which some systems, GrapheneOS among them, let the owner take away.
                            if (on && !viewModel.networkAvailable()) askNetwork = true
                        },
                    ),
                    trailing = { Switch(checked = link.enabled, onCheckedChange = null) },
                )
            }
            if (link.enabled) {
                SecaGroupItem(index = 1, count = linkRows, onClick = { viewModel.open(MessagesScreen.Relays) }) {
                    SecaSettingRow(
                        icon = SecaIcons.Wifi,
                        title = stringResource(R.string.relays),
                        subtitle = pluralStringResource(R.plurals.relays_count, link.relays.size, link.relays.size),
                    )
                }
                BackgroundAccessRow(index = 2, count = linkRows)
                TorRow(link, viewModel, index = 3, count = linkRows)
                SecaGroupItem(index = 4, count = linkRows, onClick = { viewModel.open(LinkStatusRoute) }) {
                    SecaSettingRow(
                        icon = SecaIcons.Check,
                        title = stringResource(R.string.link_status),
                        subtitle = stringResource(R.string.link_status_hint),
                    )
                }
                SecaGroupItem(index = 5, count = linkRows) {
                    SecaSettingRow(
                        icon = SecaIcons.Shield,
                        title = stringResource(R.string.fingerprint),
                        subtitle = link.fingerprint ?: stringResource(R.string.creating_keys),
                    )
                }
                SecaGroupItem(index = 0, count = 2, modifier = Modifier.padding(top = 8.dp)) {
                    SecaSettingRow(
                        icon = SecaIcons.Check,
                        title = stringResource(R.string.read_receipts),
                        subtitle = stringResource(R.string.read_receipts_hint),
                        modifier = Modifier.toggleable(
                            value = link.readReceipts,
                            role = Role.Switch,
                            onValueChange = viewModel::setReadReceipts,
                        ),
                        trailing = { Switch(checked = link.readReceipts, onCheckedChange = null) },
                    )
                }
                SecaGroupItem(index = 1, count = 2) {
                    SecaSettingRow(
                        icon = SecaIcons.Edit,
                        title = stringResource(R.string.typing_indicator),
                        subtitle = stringResource(R.string.typing_indicator_hint),
                        modifier = Modifier.toggleable(
                            value = link.typingIndicator,
                            role = Role.Switch,
                            onValueChange = viewModel::setTypingIndicator,
                        ),
                        trailing = { Switch(checked = link.typingIndicator, onCheckedChange = null) },
                    )
                }
                if (link.offline) NetworkBlockedCard(Modifier.padding(top = 8.dp))
            }
            SecaHint(stringResource(R.string.link_hint))
            if (askNetwork) NetworkAccessDialog(onDismiss = { askNetwork = false })

            SecaSectionLabel(stringResource(R.string.protection))
            SecaPrivacySettingsGroup(stringResource(R.string.app_name))

            SecaSectionLabel(stringResource(R.string.privacy))
            SecaGroupItem(index = 0, count = 1) {
                SecaSettingRow(
                    icon = SecaIcons.Shield,
                    title = stringResource(R.string.privacy_title),
                    subtitle = stringResource(R.string.privacy_text),
                )
            }
        }
    }

    backupTarget?.let { uri ->
        SecaPassphraseDialog(
            title = stringResource(R.string.encrypt_backup),
            confirmLabel = stringResource(R.string.encrypt),
            creating = true,
            onDismiss = { backupTarget = null },
            onConfirm = {
                viewModel.exportBackup(uri, it)
                backupTarget = null
            },
        )
    }
    restoreSource?.let { uri ->
        SecaPassphraseDialog(
            title = stringResource(R.string.open_backup),
            confirmLabel = stringResource(R.string.restore),
            creating = false,
            onDismiss = { restoreSource = null },
            onConfirm = {
                viewModel.importBackup(uri, it)
                restoreSource = null
            },
        )
    }
}
