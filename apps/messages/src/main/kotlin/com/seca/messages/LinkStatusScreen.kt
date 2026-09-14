package com.seca.messages

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.seca.core.design.SecaIcons
import com.seca.core.design.component.SecaGroupItem
import com.seca.core.design.component.SecaHint
import com.seca.core.design.component.SecaSectionLabel
import com.seca.core.design.component.SecaSettingRow
import com.seca.core.design.component.SecaTopBar
import com.seca.core.link.LinkPeer
import com.seca.core.link.relay.RelayClock
import com.seca.messages.link.LinkListening
import kotlinx.coroutines.delay
import kotlin.math.abs
import androidx.lifecycle.viewmodel.compose.viewModel as screenViewModel

/** How Seca Link stands on this phone, relay by relay and contact by contact. */
data object LinkStatusRoute : MessagesScreen

private const val MINUTE = 60_000L
private const val REFRESH_MILLIS = 30_000L
private const val TRUSTED_DRIFT_SECONDS = 3L

/**
 * What Seca Link is doing right now, to see where a conversation gets stuck:
 * whether each relay is listened to, whether the clock was corrected, and how
 * far each contact's connection went, with the way to try again.
 */
@Composable
internal fun LinkStatusScreen(ui: MessagesUi, viewModel: MessagesViewModel) {
    val context = LocalContext.current
    val links: LinkPeersViewModel = screenViewModel()
    val peers by links.peers.collectAsState()
    val listening by LinkListening.states.collectAsState()
    val link = ui.link
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(REFRESH_MILLIS)
            now = System.currentTimeMillis()
        }
    }
    val contacts = remember(peers) { peers.values.sortedWith(compareByDescending<LinkPeer> { it.ready }.thenBy { it.number }) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { SecaTopBar(title = "État de Seca Link", onBack = { viewModel.back() }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item(key = "phone-label") { SecaSectionLabel("Ce téléphone") }
            item(key = "enabled") {
                SecaGroupItem(index = 0, count = 4) {
                    SecaSettingRow(
                        icon = SecaIcons.Link,
                        title = if (link.enabled) "Seca Link activé" else "Seca Link désactivé",
                        subtitle = if (link.enabled) link.fingerprint?.let { "Empreinte $it" } else "Activez-le dans les réglages",
                    )
                }
            }
            item(key = "network") {
                SecaGroupItem(index = 1, count = 4) {
                    SecaSettingRow(
                        icon = SecaIcons.Wifi,
                        title = "Réseau",
                        subtitle = when {
                            link.offline -> "Pas d'accès : vérifiez la connexion et l'autorisation « Réseau »"
                            link.useTor && link.orbotRunning == false -> "Tor choisi, mais Orbot ne répond pas"
                            link.useTor -> "Par Tor, avec Orbot"
                            else -> "Connexion directe aux relais"
                        },
                    )
                }
            }
            item(key = "background") {
                SecaGroupItem(index = 2, count = 4) {
                    SecaSettingRow(
                        icon = SecaIcons.Battery,
                        title = "Réception en arrière-plan",
                        subtitle = if (BackgroundAccess.granted(context)) "Autorisée, sans notification" else "Avec une notification discrète",
                    )
                }
            }
            item(key = "clock") {
                val offset = RelayClock.offset
                SecaGroupItem(index = 3, count = 4) {
                    SecaSettingRow(
                        icon = SecaIcons.Schedule,
                        title = "Horloge",
                        subtitle = when {
                            abs(offset) < TRUSTED_DRIFT_SECONDS -> "À l'heure des relais"
                            offset > 0 -> "Le téléphone retarde de $offset s : corrigé avec l'heure des relais"
                            else -> "Le téléphone avance de ${-offset} s : corrigé avec l'heure des relais"
                        },
                    )
                }
            }

            item(key = "relays-label") { SecaSectionLabel("Écoute des relais") }
            itemsIndexed(link.relays, key = { _, url -> "relay-$url" }) { index, url ->
                val state = listening[url]
                SecaGroupItem(index = index, count = link.relays.size) {
                    SecaSettingRow(
                        icon = if (state?.state == LinkListening.State.Connected) SecaIcons.Check else SecaIcons.Wifi,
                        title = url.removePrefix("wss://"),
                        subtitle = when (state?.state) {
                            null -> if (link.enabled) "Pas encore écouté" else "Seca Link est désactivé"
                            LinkListening.State.Connecting -> "Connexion…"
                            LinkListening.State.Disconnected -> "Déconnecté ${ago(state.since, now)}, nouvel essai bientôt"
                            LinkListening.State.Connected -> if (state.lastEnvelopeAt > 0) {
                                "À l'écoute · dernière enveloppe ${ago(state.lastEnvelopeAt, now)}"
                            } else {
                                "À l'écoute ${ago(state.since, now)}"
                            }
                        },
                    )
                }
            }

            item(key = "contacts-label") { SecaSectionLabel("Contacts") }
            if (contacts.isEmpty()) {
                item(key = "no-contacts") {
                    SecaHint(
                        "Aucun contact Seca Link pour l'instant. Dans une conversation, touchez ⋮ puis « Chiffrer avec " +
                            "Seca Link », ou « Connecter en face à face ».",
                    )
                }
            }
            itemsIndexed(contacts, key = { _, peer -> "peer-${peer.number}" }) { index, peer ->
                SecaGroupItem(index = index, count = contacts.size, onClick = { viewModel.openConversation(peer.number) }) {
                    SecaSettingRow(
                        icon = if (peer.ready) SecaIcons.Lock else SecaIcons.Link,
                        title = ui.nameOf(peer.number),
                        subtitle = peerState(peer, now),
                        trailing = when {
                            peer.ready || !link.enabled -> null
                            peer.nostrPublicKey != null -> {
                                { TextButton(onClick = { links.retry(peer.number) }) { Text("Réessayer") } }
                            }
                            else -> {
                                { TextButton(onClick = { links.invite(peer.number) }) { Text("Renvoyer") } }
                            }
                        },
                    )
                }
            }
            item(key = "hint") {
                SecaHint(
                    "Deux téléphones se connectent en échangeant une invitation, par SMS ou par QR code en face à face. " +
                        "Tant que ce n'est pas fait, les messages partent en SMS, sans chiffrement.",
                )
            }
        }
    }
}

private fun peerState(peer: LinkPeer, now: Long): String = when {
    peer.ready && peer.keyChangedAt > 0 -> "Chiffré · sa clé a changé, à vérifier"
    peer.ready && peer.verified -> "Chiffré · vérifié"
    peer.ready -> "Chiffré"
    peer.nostrPublicKey != null && peer.attemptedAt > 0 ->
        "Invitation reçue, connexion pas encore ouverte · dernier essai ${ago(peer.attemptedAt, now)}"
    peer.nostrPublicKey != null -> "Invitation reçue, connexion pas encore ouverte"
    peer.invitedAt > 0 -> "Invitation envoyée ${ago(peer.invitedAt, now)}, pas encore de réponse"
    else -> "Aucun échange pour l'instant"
}

/** "à l'instant", "il y a 5 min", "il y a 3 h", "il y a 2 j". */
private fun ago(millis: Long, now: Long): String {
    val minutes = (now - millis).coerceAtLeast(0) / MINUTE
    return when {
        minutes < 1 -> "à l'instant"
        minutes < 60 -> "il y a $minutes min"
        minutes < 24 * 60 -> "il y a ${minutes / 60} h"
        else -> "il y a ${minutes / (24 * 60)} j"
    }
}
