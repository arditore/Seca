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
import com.seca.core.design.component.SecaPaletteSwatch
import com.seca.core.design.component.SecaPassphraseDialog
import com.seca.core.design.component.SecaProfileBadge
import com.seca.core.design.component.SecaSectionLabel
import com.seca.core.design.component.SecaSettingRow
import com.seca.core.design.component.SecaTopBar
import com.seca.core.design.privacy.SecaPrivacySettingsGroup
import android.Manifest
import com.seca.core.model.Profile
import com.seca.core.suite.SuiteBackupSection
import java.time.LocalDate
import com.seca.core.design.label
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

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

            SecaSectionLabel(stringResource(R.string.colors))
            SecaGroupItem(index = 0, count = 2) {
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
            SecaGroupItem(index = 1, count = 2) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(SecaIcons.Palette)
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(start = 16.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.seca_palette),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(if (dynamic) R.string.seca_palette_hint_dynamic else R.string.seca_palette_hint_on),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
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
            }
            SecaHint(stringResource(R.string.dark_mode_hint))

            SecaSectionLabel(stringResource(R.string.profiles))
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

            SecaSectionLabel(stringResource(R.string.backup))
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

            SecaSectionLabel(stringResource(R.string.tidy_up))
            SecaGroupItem(index = 0, count = 1, onClick = { viewModel.open(DuplicatesRoute) }) {
                SecaSettingRow(
                    icon = SecaIcons.Contacts,
                    title = stringResource(R.string.duplicates),
                    subtitle = stringResource(R.string.duplicates_row_hint),
                )
            }

            SecaSectionLabel(stringResource(R.string.protection))
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
