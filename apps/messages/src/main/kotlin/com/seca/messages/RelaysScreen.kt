package com.seca.messages

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.seca.core.design.SecaIcons
import com.seca.core.design.component.SecaGroupItem
import com.seca.core.design.component.SecaHint
import com.seca.core.design.component.SecaSectionLabel
import com.seca.core.design.component.SecaSettingRow
import com.seca.core.design.component.SecaTopBar

/** A relay's answer to the latest publication of this phone's keys. */
enum class RelayState { Publishing, Published, Refused, Unreachable }

data class RelayStatus(val state: RelayState, val detail: String? = null)

data class LinkUi(
    val enabled: Boolean = false,
    val relays: List<String> = emptyList(),
    val statuses: Map<String, RelayStatus> = emptyMap(),
    /** Null until the keys exist. */
    val fingerprint: String? = null,
    val error: String? = null,
    /** No connection, or the app is not allowed on the network. */
    val offline: Boolean = false,
    val readReceipts: Boolean = true,
    val typingIndicator: Boolean = true,
)

/** The relays that receive for this phone: how each answered, and adding or removing one. */
@Composable
internal fun RelaysScreen(ui: MessagesUi, viewModel: MessagesViewModel) {
    val link = ui.link
    var input by rememberSaveable { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    val add = {
        if (viewModel.addRelay(input)) input = "" else invalid = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { SecaTopBar(title = "Relais", onBack = { viewModel.back() }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            if (link.enabled && link.offline) {
                item(key = "offline") { NetworkBlockedCard(Modifier.padding(top = 8.dp)) }
            }
            item(key = "label") { SecaSectionLabel("Relais de réception") }
            if (link.relays.isEmpty()) {
                item(key = "none") { SecaHint("Aucun relais : ajoutez-en un, ou revenez à la liste par défaut.") }
            }
            itemsIndexed(link.relays, key = { _, url -> url }) { index, url ->
                SecaGroupItem(index = index, count = link.relays.size) {
                    SecaSettingRow(
                        icon = SecaIcons.Wifi,
                        title = url.removePrefix("wss://"),
                        subtitle = statusText(link.statuses[url], link),
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusMark(link.statuses[url])
                                IconButton(onClick = { viewModel.removeRelay(url) }) {
                                    Icon(SecaIcons.Delete, contentDescription = "Retirer $url")
                                }
                            }
                        },
                    )
                }
            }

            item(key = "add-label") { SecaSectionLabel("Ajouter un relais") }
            item(key = "add") {
                SecaGroupItem(index = 0, count = 1) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = {
                                input = it
                                invalid = false
                            },
                            placeholder = { Text("relais.exemple.org") },
                            singleLine = true,
                            isError = invalid,
                            supportingText = if (invalid) {
                                { Text("Adresse de relais invalide") }
                            } else {
                                null
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { add() }),
                            modifier = Modifier.weight(1f),
                        )
                        FilledTonalButton(
                            onClick = add,
                            enabled = input.isNotBlank(),
                            modifier = Modifier.padding(start = 12.dp),
                        ) { Text("Ajouter") }
                    }
                }
            }

            item(key = "actions") {
                Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    TextButton(onClick = { viewModel.publishPrekeys() }, enabled = link.enabled) { Text("Publier à nouveau") }
                    TextButton(onClick = { viewModel.resetRelays() }) { Text("Liste par défaut") }
                }
            }
            item(key = "hint") {
                SecaHint(
                    "Les relais Nostr sont publics, gratuits et tenus par des bénévoles. Ils ne reçoivent que des clés " +
                        "publiques et des messages chiffrés, et voient l'adresse IP du téléphone. Seules les connexions " +
                        "chiffrées (wss) sont acceptées.",
                )
            }
        }
    }
}

/** Shown while Seca Link is on but the app cannot reach the Internet; a tap opens its settings. */
@Composable
internal fun NetworkBlockedCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    SecaGroupItem(index = 0, count = 1, modifier = modifier, onClick = { openAppSettings(context) }) {
        SecaSettingRow(
            icon = SecaIcons.Wifi,
            title = "Pas d'accès au réseau",
            subtitle = "Dans les réglages de Seca Messages, ouvrez Autorisations et activez « Réseau », " +
                "ou vérifiez la connexion. La publication reprend d'elle-même.",
        )
    }
}

/** Asked when Seca Link is turned on without network access: Android offers no prompt of its own for it. */
@Composable
internal fun NetworkAccessDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(SecaIcons.Wifi, contentDescription = null) },
        title = { Text("Autoriser l'accès au réseau") },
        text = {
            Text(
                "Seca Link joint les relais par Internet. Ouvrez les réglages de Seca Messages, puis Autorisations, " +
                    "et activez « Réseau ». La publication de vos clés reprendra dès que la connexion sera possible.",
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    openAppSettings(context)
                },
            ) { Text("Ouvrir les réglages") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Plus tard") } },
    )
}

private fun statusText(status: RelayStatus?, link: LinkUi): String = when {
    !link.enabled -> "Seca Link est désactivé"
    link.offline -> "En attente du réseau"
    status == null -> "Pas encore publié"
    status.state == RelayState.Publishing -> "Publication…"
    status.state == RelayState.Published -> "Clé publiée"
    status.state == RelayState.Refused -> if (status.detail.isNullOrBlank()) "Refusé par le relais" else "Refusé : ${status.detail}"
    else -> "Injoignable"
}

@Composable
private fun StatusMark(status: RelayStatus?) {
    when (status?.state) {
        RelayState.Publishing -> CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        RelayState.Published -> Icon(SecaIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        RelayState.Refused, RelayState.Unreachable ->
            Icon(SecaIcons.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        null -> Unit
    }
}
