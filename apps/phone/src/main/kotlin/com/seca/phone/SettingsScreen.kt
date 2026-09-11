package com.seca.phone

import android.content.Intent
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

/** A way into one of the system's own call screens; [intents] are tried in order. */
private data class SystemEntry(val icon: ImageVector, val title: String, val subtitle: String, val intents: List<Intent>)

@Composable
internal fun SettingsScreen(ui: PhoneUi, viewModel: PhoneViewModel, withCallLogWrite: (() -> Unit) -> Unit) {
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    val dynamic = ui.palette == null
    val calls = listOf(
        SystemEntry(SecaIcons.Wifi, "Appels Wi-Fi", "Appeler par le Wi-Fi quand le réseau mobile est faible", wifiCallingSettings()),
        SystemEntry(
            SecaIcons.Phone,
            "Réglages de l'opérateur",
            "Transfert, double appel, présentation du numéro",
            listOf(callSettings()),
        ),
        SystemEntry(SecaIcons.Voicemail, "Messagerie vocale", "Numéro et réglages de la messagerie", listOf(voicemailSettings())),
        SystemEntry(
            SecaIcons.Block,
            "Numéros bloqués",
            "La liste d'Android, commune à toutes les applications",
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
                text = "Paramètres",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
            )

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

            SecaSectionLabel("Appels")
            calls.forEachIndexed { index, entry ->
                SecaGroupItem(
                    index = index,
                    count = calls.size,
                    onClick = {
                        if (!openSystemScreen(context, entry.intents)) {
                            Toast.makeText(context, "Indisponible sur ce téléphone", Toast.LENGTH_SHORT).show()
                        }
                    },
                ) {
                    SecaSettingRow(icon = entry.icon, title = entry.title, subtitle = entry.subtitle)
                }
            }

            SecaSectionLabel("Historique")
            SecaGroupItem(index = 0, count = 1, onClick = { confirmClear = true }) {
                SecaSettingRow(
                    icon = SecaIcons.Delete,
                    title = "Effacer l'historique des appels",
                    subtitle = "Sur ce téléphone, pour toutes les applications",
                )
            }

            SecaSectionLabel("Confidentialité")
            SecaGroupItem(index = 0, count = 1) {
                SecaSettingRow(
                    icon = SecaIcons.Lock,
                    title = "Tout reste sur ce téléphone",
                    subtitle = "Seca Téléphone n'a pas accès à Internet. L'historique reste celui d'Android : " +
                        "rien n'est copié ailleurs.",
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { Icon(SecaIcons.Delete, contentDescription = null) },
            title = { Text("Effacer tout l'historique ?") },
            text = { Text("Tous les appels passés et reçus seront retirés de ce téléphone. C'est définitif.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        withCallLogWrite { viewModel.clearHistory() }
                    },
                ) { Text("Effacer") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annuler") } },
        )
    }
}
