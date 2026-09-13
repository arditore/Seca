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

@Composable
internal fun SettingsScreen(
    ui: MessagesUi,
    viewModel: MessagesViewModel,
    isDefaultApp: Boolean,
    onBecomeDefault: () -> Unit,
) {
    val context = LocalContext.current
    val dynamic = ui.palette == null
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
                text = "Paramètres",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
            )

            SecaSectionLabel("Application SMS")
            SecaGroupItem(
                index = 0,
                count = 1,
                onClick = { if (isDefaultApp) openDefaultAppsSettings(context) else onBecomeDefault() },
            ) {
                SecaSettingRow(
                    icon = SecaIcons.Messages,
                    title = if (isDefaultApp) "Seca Messages gère vos SMS" else "Utiliser Seca Messages par défaut",
                    subtitle = if (isDefaultApp) {
                        "Réception, envoi et notifications"
                    } else {
                        "Seule l'application SMS par défaut reçoit et envoie les messages"
                    },
                    trailing = if (isDefaultApp) {
                        { Icon(SecaIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    },
                )
            }

            SecaSectionLabel("Notifications")
            SecaGroupItem(index = 0, count = 1, onClick = { openNotificationSettings(context) }) {
                SecaSettingRow(
                    icon = SecaIcons.Bell,
                    title = "Notifications",
                    subtitle = "Son, vibration et affichage sur l'écran verrouillé",
                )
            }

            SecaSectionLabel("Couleurs")
            if (ui.profiles.connected) {
                SecaGroupItem(index = 0, count = 2) {
                    SecaSettingRow(
                        icon = SecaIcons.AutoAwesome,
                        title = "Couleurs dynamiques",
                        subtitle = "Assorties au fond d'écran du téléphone",
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
                SecaHint("Les couleurs sont communes à toutes les applications Seca. Le mode sombre suit le téléphone.")
            } else {
                SecaHint("Installez Seca Contacts pour choisir les couleurs de la suite.")
            }

            SecaSectionLabel("Sauvegarde")
            SecaGroupItem(index = 0, count = 2, onClick = { backupLauncher.launch("seca-messages-${LocalDate.now()}.seca") }) {
                SecaSettingRow(
                    icon = SecaIcons.Lock,
                    title = "Sauvegarde chiffrée",
                    subtitle = "Tous les SMS, protégés par un mot de passe",
                )
            }
            SecaGroupItem(
                index = 1,
                count = 2,
                onClick = {
                    if (isDefaultApp) {
                        restoreLauncher.launch(arrayOf("*/*"))
                    } else {
                        Toast.makeText(context, "Activez d'abord Seca Messages comme application SMS", Toast.LENGTH_LONG).show()
                    }
                },
            ) {
                SecaSettingRow(
                    icon = SecaIcons.Download,
                    title = "Restaurer une sauvegarde",
                    subtitle = "Remet les messages absents de ce téléphone",
                )
            }
            SecaHint("Rien n'est synchronisé : gardez la sauvegarde ailleurs que sur ce téléphone.")

            SecaSectionLabel("Protection")
            SecaPrivacySettingsGroup("Seca Messages")

            SecaSectionLabel("Confidentialité")
            SecaGroupItem(index = 0, count = 1) {
                SecaSettingRow(
                    icon = SecaIcons.Shield,
                    title = "Tout reste sur ce téléphone",
                    subtitle = "Seca Messages n'a pas accès à Internet. Les SMS restent là où Android les garde : " +
                        "rien n'est copié ailleurs.",
                )
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
            onConfirm = {
                viewModel.importBackup(uri, it)
                restoreSource = null
            },
        )
    }
}
