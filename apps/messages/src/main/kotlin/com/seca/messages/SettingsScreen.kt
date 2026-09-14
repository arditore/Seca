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

            CodeAndSpamSettings()

            SecaSectionLabel("Seca Link")
            val link = ui.link
            var askNetwork by remember { mutableStateOf(false) }
            val published = link.statuses.values.count { it.state == RelayState.Published }
            val linkRows = if (link.enabled) 3 else 1
            SecaGroupItem(index = 0, count = linkRows) {
                SecaSettingRow(
                    icon = SecaIcons.Link,
                    title = "Chiffrement Seca Link",
                    subtitle = when {
                        !link.enabled -> "Messages chiffrés de bout en bout entre téléphones Seca"
                        link.error != null -> link.error
                        link.offline -> "En attente du réseau"
                        link.statuses.values.any { it.state == RelayState.Publishing } -> "Publication de votre clé…"
                        published > 0 -> "Clé publiée sur $published relais"
                        link.statuses.isNotEmpty() -> "Aucun relais n'a accepté la clé"
                        else -> "Activé"
                    },
                    modifier = Modifier.toggleable(
                        value = link.enabled,
                        role = Role.Switch,
                        onValueChange = { on ->
                            viewModel.setLinkEnabled(on)
                            // Android has no prompt for network access, which GrapheneOS lets the owner take away.
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
                        title = "Relais",
                        subtitle = "${link.relays.size} relais Nostr publics et gratuits",
                    )
                }
                SecaGroupItem(index = 2, count = linkRows) {
                    SecaSettingRow(
                        icon = SecaIcons.Shield,
                        title = "Empreinte de ce téléphone",
                        subtitle = link.fingerprint ?: "Création des clés…",
                    )
                }
                SecaGroupItem(index = 0, count = 2, modifier = Modifier.padding(top = 8.dp)) {
                    SecaSettingRow(
                        icon = SecaIcons.Check,
                        title = "Accusés de lecture",
                        subtitle = "Vos contacts voient quand vous avez lu leurs messages",
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
                        title = "Indicateur d'écriture",
                        subtitle = "Vos contacts voient quand vous leur écrivez",
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
            SecaHint(
                "Avec un contact qui a aussi Seca, les messages partent chiffrés de bout en bout, sans passer par " +
                    "l'opérateur. Les clés restent dans la puce de sécurité du téléphone ; les relais ne voient ni qui " +
                    "écrit, ni ce qui est écrit.",
            )
            if (askNetwork) NetworkAccessDialog(onDismiss = { askNetwork = false })

            SecaSectionLabel("Protection")
            SecaPrivacySettingsGroup("Seca Messages")

            SecaSectionLabel("Confidentialité")
            SecaGroupItem(index = 0, count = 1) {
                SecaSettingRow(
                    icon = SecaIcons.Shield,
                    title = "Vos SMS restent sur ce téléphone",
                    subtitle = "Les SMS restent là où Android les garde : rien n'est copié ailleurs. Internet ne sert " +
                        "qu'à Seca Link, quand vous l'activez, et n'y passent que des clés publiques et du chiffré.",
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
