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
import com.seca.core.model.Profile
import com.seca.core.suite.SuiteBackupSection
import java.time.LocalDate

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
                text = "Paramètres",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
            )

            SecaSectionLabel("Couleurs")
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
                            text = "Couleurs dynamiques",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Assorties au fond d'écran du téléphone",
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
                                text = "Palette Seca",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = if (dynamic) {
                                    "Pour remplacer les couleurs du fond d'écran"
                                } else {
                                    "Chaque application Seca en prend sa propre nuance"
                                },
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
            SecaHint("Le mode clair ou sombre suit celui du téléphone.")

            SecaSectionLabel("Profils")
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
                        text = "Ajouter un profil",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
            SecaHint("Rangez vos contacts par profil, comme Travail ou Famille. Supprimer un profil ne supprime aucun contact.")

            SecaSectionLabel("Sauvegarde")
            SecaGroupItem(index = 0, count = 4, onClick = { backupLauncher.launch("seca-contacts-${LocalDate.now()}.seca") }) {
                SecaSettingRow(
                    icon = SecaIcons.Lock,
                    title = "Sauvegarde chiffrée",
                    subtitle = "Contacts, profils et Ma fiche, protégés par un mot de passe",
                )
            }
            SecaGroupItem(index = 1, count = 4, onClick = { restoreLauncher.launch(arrayOf("*/*")) }) {
                SecaSettingRow(
                    icon = SecaIcons.Download,
                    title = "Restaurer une sauvegarde",
                    subtitle = "Remet les contacts manquants, chacun dans son profil",
                )
            }
            SecaGroupItem(index = 2, count = 4, onClick = { exportLauncher.launch("contacts-seca-${LocalDate.now()}.vcf") }) {
                SecaSettingRow(
                    icon = SecaIcons.Upload,
                    title = "Exporter les contacts",
                    subtitle = "Un fichier .vcf, gardé où vous voulez",
                )
            }
            SecaGroupItem(index = 3, count = 4, onClick = { importLauncher.launch(VCardTypes) }) {
                SecaSettingRow(
                    icon = SecaIcons.Download,
                    title = "Importer des contacts",
                    subtitle = "Depuis un fichier .vcf ; ceux déjà présents sont ignorés",
                )
            }
            SecaHint("Seca ne synchronise rien : gardez une copie de vos contacts ailleurs que sur ce téléphone.")

            SuiteBackupSection()

            SecaSectionLabel("Rangement")
            SecaGroupItem(index = 0, count = 1, onClick = { viewModel.open(DuplicatesRoute) }) {
                SecaSettingRow(
                    icon = SecaIcons.Contacts,
                    title = "Doublons",
                    subtitle = "Réunir les fiches d'une même personne",
                )
            }

            SecaSectionLabel("Protection")
            SecaPrivacySettingsGroup("Seca Contacts")

            SecaSectionLabel("Confidentialité")
            SecaGroupItem(index = 0, count = 1) {
                Row(Modifier.padding(16.dp)) {
                    IconBadge(SecaIcons.Lock)
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = 16.dp),
                    ) {
                        Text(
                            text = "Tout reste sur ce téléphone",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Seca Contacts n'a pas accès à Internet. Vos contacts, vos profils et vos " +
                                "réglages ne quittent jamais l'appareil, et les contacts créés ici ne sont " +
                                "synchronisés avec aucun compte.",
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
            title = "Chiffrer la sauvegarde",
            confirmLabel = "Chiffrer",
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
            title = "Ouvrir la sauvegarde",
            confirmLabel = "Restaurer",
            creating = false,
            onDismiss = { restoreSource = null },
            onConfirm = { passphrase ->
                restoreSource = null
                withWrite { viewModel.importBackup(uri, passphrase) }
            },
        )
    }

    if (adding) {
        ProfileNameDialog(
            title = "Nouveau profil",
            initial = "",
            confirmLabel = "Créer",
            onDismiss = { adding = false },
            onConfirm = {
                viewModel.addProfile(it)
                adding = false
            },
        )
    }
    renaming?.let { profile ->
        ProfileNameDialog(
            title = "Renommer le profil",
            initial = profile.name,
            confirmLabel = "Renommer",
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
            title = { Text("Supprimer « ${profile.name} » ?") },
            text = { Text("Ses contacts reviennent dans Principal. Aucun contact n'est supprimé.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProfile(profile.id)
                        deleting = null
                    },
                ) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annuler") } },
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
        SecaProfileBadge(profile.name, tone = tone, size = 40.dp)
        Column(
            Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(profile.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = if (count == 1) "1 contact" else "$count contacts",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (editable) {
            IconButton(onClick = onRename) {
                Icon(SecaIcons.Edit, contentDescription = "Renommer ${profile.name}")
            }
            IconButton(onClick = onDelete) {
                Icon(SecaIcons.Delete, contentDescription = "Supprimer ${profile.name}")
            }
        }
    }
}
