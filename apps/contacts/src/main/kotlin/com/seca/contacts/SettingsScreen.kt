package com.seca.contacts

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.seca.core.design.SecaIcons
import com.seca.core.design.SecaPalette
import com.seca.core.design.component.SecaGroupItem
import com.seca.core.design.component.SecaHint
import android.net.Uri
import com.seca.core.design.component.SecaPaletteSection
import com.seca.core.design.component.SecaShapeSection
import com.seca.core.design.component.SecaPaletteSwatch
import com.seca.core.design.component.SecaPassphraseDialog
import com.seca.core.design.component.SecaProfileBadge
import com.seca.core.design.component.SecaSectionLabel
import com.seca.core.design.component.SecaSettingRow
import com.seca.core.design.component.SecaSwitchRow
import com.seca.core.design.component.SecaTopBar
import com.seca.core.design.privacy.SecaPrivacySettingsGroup
import android.Manifest
import com.seca.core.model.Profile
import com.seca.core.suite.AutoBackupSection
import com.seca.core.suite.SuiteBackupSection
import com.seca.core.suite.SuiteNamesSection
import com.seca.core.suite.SuiteOrderSection
import java.time.LocalDate
import com.seca.core.design.label
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.activity.compose.BackHandler

/** What a .vcf file may be labelled as, depending on the app that made it. */
private val VCardTypes = arrayOf("text/x-vcard", "text/vcard", "text/directory", "text/plain", "application/octet-stream")

@Composable
internal fun SettingsScreen(ui: ContactsUi, viewModel: ContactsViewModel, withWrite: (() -> Unit) -> Unit) {
    // The system file picker: the user chooses where the file goes, and the app sees nothing else.
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-vcard")) { uri ->
        uri?.let(viewModel::exportContacts)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { withWrite { viewModel.importContacts(it) } }
    }
    // An encrypted backup: the file is chosen first, then its passphrase is asked.
    var backupTarget by remember { mutableStateOf<Uri?>(null) }
    var restoreSource by remember { mutableStateOf<Uri?>(null) }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
        backupTarget = it
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { restoreSource = it }
    // A restoration first asks to write the contacts, to bring back those missing. Refused, the
    // contacts already on the phone are still filed in their profiles.
    var afterWriteAsked by remember { mutableStateOf<(() -> Unit)?>(null) }
    val writeAsk = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        afterWriteAsked?.invoke()
        afterWriteAsked = null
    }
    val askWriteThen: (() -> Unit) -> Unit = { action ->
        afterWriteAsked = action
        writeAsk.launch(Manifest.permission.WRITE_CONTACTS)
    }
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Profile?>(null) }
    var deleting by remember { mutableStateOf<Profile?>(null) }
    val dynamic = ui.palette == null
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
                        icon = SecaIcons.Label,
                        title = stringResource(R.string.profiles),
                        subtitle = stringResource(R.string.settings_profiles_hint),
                        index = 1,
                        count = 5,
                    ) { page = SettingsPage.Profiles }
                    SettingsEntry(
                        icon = SecaIcons.Lock,
                        title = stringResource(R.string.backup),
                        subtitle = stringResource(R.string.settings_backup_hint),
                        index = 2,
                        count = 5,
                    ) { page = SettingsPage.Backup }
                    SettingsEntry(
                        icon = SecaIcons.Contacts,
                        title = stringResource(R.string.tidy_up),
                        subtitle = stringResource(R.string.settings_tidy_hint),
                        index = 3,
                        count = 5,
                    ) { page = SettingsPage.TidyUp }
                    SettingsEntry(
                        icon = SecaIcons.Shield,
                        title = stringResource(R.string.protection),
                        subtitle = stringResource(R.string.settings_privacy_hint),
                        index = 4,
                        count = 5,
                    ) { page = SettingsPage.Privacy }
                }
                SettingsPage.Appearance -> {
                    SecaGroupItem(index = 0, count = 4) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = dynamic,
                                    role = Role.Switch,
                                    onValueChange = { on -> viewModel.setPalette(if (on) null else SecaPalette.Ocean) },
                                )
                                .padding(16.dp),
                        ) {
                            IconBadge(SecaIcons.AutoAwesome)
                            Column(
                                Modifier
                                    .weight(1f)
                                    .padding(horizontal = 16.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.dynamic_colors),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(R.string.dynamic_colors_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = dynamic, onCheckedChange = null)
                        }
                    }
                    SecaGroupItem(index = 1, count = 4) {
                        SecaSwitchRow(
                            icon = SecaIcons.DarkMode,
                            title = stringResource(R.string.black_theme),
                            subtitle = stringResource(R.string.black_theme_hint),
                            checked = ui.black,
                            onCheckedChange = viewModel::setBlack,
                        )
                    }
                    SecaGroupItem(index = 2, count = 4) {
                        SecaPaletteSection(ui.palette, dynamic, viewModel::setPalette)
                    }
                    SecaGroupItem(index = 3, count = 4) {
                        SecaShapeSection(ui.avatarShape, viewModel::setAvatarShape)
                    }
                    SecaHint(stringResource(R.string.dark_mode_hint))
                    SuiteOrderSection()
                    SuiteNamesSection()
                }
                SettingsPage.Profiles -> {
                    val total = ui.profiles.size + 1
                    ui.profiles.forEachIndexed { index, profile ->
                        SecaGroupItem(index = index, count = total) {
                            ProfileRow(
                                profile = profile,
                                tone = index,
                                count = ui.counts[profile.id] ?: 0,
                                editable = profile.id != ProfileStore.Principal.id,
                                onRename = { renaming = profile },
                                onDelete = { deleting = profile },
                            )
                        }
                    }
                    SecaGroupItem(index = ui.profiles.size, count = total, onClick = { adding = true }) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                            IconBadge(SecaIcons.Add)
                            Text(
                                text = stringResource(R.string.add_profile),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                    }
                    SecaHint(stringResource(R.string.profiles_hint))
                }
                SettingsPage.Backup -> {
                    SecaGroupItem(index = 0, count = 4, onClick = { backupLauncher.launch("seca-contacts-${LocalDate.now()}.seca") }) {
                        SecaSettingRow(
                            icon = SecaIcons.Lock,
                            title = stringResource(R.string.encrypted_backup),
                            subtitle = stringResource(R.string.encrypted_backup_hint),
                        )
                    }
                    SecaGroupItem(index = 1, count = 4, onClick = { askWriteThen { restoreLauncher.launch(arrayOf("*/*")) } }) {
                        SecaSettingRow(
                            icon = SecaIcons.Download,
                            title = stringResource(R.string.restore_backup),
                            subtitle = stringResource(R.string.restore_backup_hint),
                        )
                    }
                    SecaGroupItem(index = 2, count = 4, onClick = { exportLauncher.launch("contacts-seca-${LocalDate.now()}.vcf") }) {
                        SecaSettingRow(
                            icon = SecaIcons.Upload,
                            title = stringResource(R.string.export_contacts),
                            subtitle = stringResource(R.string.export_contacts_hint),
                        )
                    }
                    SecaGroupItem(index = 3, count = 4, onClick = { importLauncher.launch(VCardTypes) }) {
                        SecaSettingRow(
                            icon = SecaIcons.Download,
                            title = stringResource(R.string.import_contacts),
                            subtitle = stringResource(R.string.import_contacts_hint),
                        )
                    }
                    SecaHint(stringResource(R.string.backup_hint))

                    SuiteBackupSection(beforeRestore = askWriteThen)
                    AutoBackupSection()
                }
                SettingsPage.TidyUp -> {
                    SecaGroupItem(index = 0, count = 2) {
                        SecaSwitchRow(
                            icon = SecaIcons.PersonOutline,
                            title = stringResource(R.string.show_my_card),
                            subtitle = stringResource(R.string.show_my_card_hint),
                            checked = ui.showMyCard,
                            onCheckedChange = viewModel::setShowMyCard,
                        )
                    }
                    SecaGroupItem(index = 1, count = 2, onClick = { viewModel.open(DuplicatesRoute) }) {
                        SecaSettingRow(
                            icon = SecaIcons.Contacts,
                            title = stringResource(R.string.duplicates),
                            subtitle = stringResource(R.string.duplicates_row_hint),
                        )
                    }
                }
                SettingsPage.Privacy -> {
                    SecaPrivacySettingsGroup(stringResource(R.string.app_name))

                    SecaSectionLabel(stringResource(R.string.privacy))
                    SecaGroupItem(index = 0, count = 1) {
                        Row(Modifier.padding(16.dp)) {
                            IconBadge(SecaIcons.Lock)
                            Column(
                                Modifier
                                    .weight(1f)
                                    .padding(start = 16.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.privacy_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(R.string.privacy_text),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
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
            onConfirm = { passphrase ->
                restoreSource = null
                viewModel.importBackup(uri, passphrase)
            },
        )
    }

    if (adding) {
        ProfileNameDialog(
            title = stringResource(R.string.new_profile),
            initial = "",
            confirmLabel = stringResource(R.string.create),
            onDismiss = { adding = false },
            onConfirm = {
                viewModel.addProfile(it)
                adding = false
            },
        )
    }
    renaming?.let { profile ->
        ProfileNameDialog(
            title = stringResource(R.string.rename_profile),
            initial = profile.name,
            confirmLabel = stringResource(R.string.rename),
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
            icon = { Icon(SecaIcons.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.delete_profile_title, profile.name)) },
            text = { Text(stringResource(R.string.delete_profile_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProfile(profile.id)
                        deleting = null
                    },
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** A round tonal badge for a settings row's icon, as Android's own settings draw them. */
@Composable
private fun IconBadge(icon: ImageVector) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun ProfileRow(
    profile: Profile,
    tone: Int,
    count: Int,
    editable: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
    ) {
        SecaProfileBadge(profile.label(), tone = tone, size = 40.dp)
        Column(
            Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(profile.label(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = pluralStringResource(R.plurals.contacts_count, count, count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (editable) {
            IconButton(onClick = onRename) {
                Icon(SecaIcons.Edit, contentDescription = stringResource(R.string.rename_named, profile.name))
            }
            IconButton(onClick = onDelete) {
                Icon(SecaIcons.Delete, contentDescription = stringResource(R.string.delete_named, profile.name))
            }
        }
    }
}

/** Which part of the settings is showing. */
private enum class SettingsPage(val title: Int) {
    Master(R.string.settings),
    Appearance(R.string.colors),
    Profiles(R.string.profiles),
    Backup(R.string.backup),
    TidyUp(R.string.tidy_up),
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
