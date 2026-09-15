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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

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
        topBar = { SecaTopBar(title = stringResource(R.string.link_status), onBack = { viewModel.back() }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item(key = "phone-label") { SecaSectionLabel(stringResource(R.string.this_phone)) }
            item(key = "enabled") {
                SecaGroupItem(index = 0, count = 4) {
                    SecaSettingRow(
                        icon = SecaIcons.Link,
                        title = stringResource(if (link.enabled) R.string.link_enabled else R.string.link_disabled),
                        subtitle = if (link.enabled) {
                            link.fingerprint?.let { stringResource(R.string.fingerprint_named, it) }
                        } else {
                            stringResource(R.string.enable_in_settings)
                        },
                    )
                }
            }
            item(key = "network") {
                SecaGroupItem(index = 1, count = 4) {
                    SecaSettingRow(
                        icon = SecaIcons.Wifi,
                        title = stringResource(R.string.network),
                        subtitle = when {
                            link.offline -> stringResource(R.string.network_blocked)
                            link.useTor && link.orbotRunning == false -> stringResource(R.string.tor_not_responding)
                            link.useTor -> stringResource(R.string.through_tor)
                            else -> stringResource(R.string.direct_connection)
                        },
                    )
                }
            }
            item(key = "background") {
                SecaGroupItem(index = 2, count = 4) {
                    SecaSettingRow(
                        icon = SecaIcons.Battery,
                        title = stringResource(R.string.background_reception),
                        subtitle = stringResource(
                            if (BackgroundAccess.granted(context)) R.string.background_allowed_quiet else R.string.background_with_notification,
                        ),
                    )
                }
            }
            item(key = "clock") {
                val offset = RelayClock.offset
                SecaGroupItem(index = 3, count = 4) {
                    SecaSettingRow(
                        icon = SecaIcons.Schedule,
                        title = stringResource(R.string.clock),
                        subtitle = when {
                            abs(offset) < TRUSTED_DRIFT_SECONDS -> stringResource(R.string.clock_on_time)
                            offset > 0 -> pluralStringResource(R.plurals.clock_behind, offset.toInt(), offset)
                            else -> pluralStringResource(R.plurals.clock_ahead, (-offset).toInt(), -offset)
                        },
                    )
                }
            }

            item(key = "relays-label") { SecaSectionLabel(stringResource(R.string.relays_listening)) }
            itemsIndexed(link.relays, key = { _, url -> "relay-$url" }) { index, url ->
                val state = listening[url]
                SecaGroupItem(index = index, count = link.relays.size) {
                    SecaSettingRow(
                        icon = if (state?.state == LinkListening.State.Connected) SecaIcons.Check else SecaIcons.Wifi,
                        title = url.removePrefix("wss://"),
                        subtitle = when (state?.state) {
                            null -> stringResource(if (link.enabled) R.string.not_listened_yet else R.string.link_is_off)
                            LinkListening.State.Connecting -> stringResource(R.string.connecting)
                            LinkListening.State.Disconnected -> stringResource(R.string.disconnected_ago, ago(state.since, now))
                            LinkListening.State.Connected -> if (state.lastEnvelopeAt > 0) {
                                stringResource(R.string.listening_last_envelope, ago(state.lastEnvelopeAt, now))
                            } else {
                                stringResource(R.string.listening_since, ago(state.since, now))
                            }
                        },
                    )
                }
            }

            item(key = "contacts-label") { SecaSectionLabel(stringResource(R.string.contacts)) }
            if (contacts.isEmpty()) {
                item(key = "no-contacts") {
                    SecaHint(stringResource(R.string.no_link_contacts))
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
                                { TextButton(onClick = { links.retry(peer.number) }) { Text(stringResource(R.string.retry)) } }
                            }
                            else -> {
                                { TextButton(onClick = { links.invite(peer.number) }) { Text(stringResource(R.string.resend)) } }
                            }
                        },
                    )
                }
            }
            item(key = "hint") {
                SecaHint(stringResource(R.string.link_status_footer))
            }
        }
    }
}

@Composable
private fun peerState(peer: LinkPeer, now: Long): String = when {
    peer.leftAt > 0 -> stringResource(R.string.peer_left, ago(peer.leftAt, now))
    peer.active && peer.keyChangedAt > 0 -> stringResource(R.string.peer_key_changed)
    peer.active && peer.verified -> stringResource(R.string.peer_verified)
    peer.active -> stringResource(R.string.peer_encrypted)
    peer.nostrPublicKey != null && peer.attemptedAt > 0 -> stringResource(R.string.peer_invited_retried, ago(peer.attemptedAt, now))
    peer.nostrPublicKey != null -> stringResource(R.string.peer_invited)
    peer.invitedAt > 0 -> stringResource(R.string.peer_invitation_sent, ago(peer.invitedAt, now))
    else -> stringResource(R.string.peer_nothing)
}

/** "just now", "5 min ago", "3 h ago", "2 days ago". */
@Composable
private fun ago(millis: Long, now: Long): String {
    val minutes = ((now - millis).coerceAtLeast(0) / MINUTE).toInt()
    return when {
        minutes < 1 -> stringResource(R.string.just_now)
        minutes < 60 -> pluralStringResource(R.plurals.minutes_ago, minutes, minutes)
        minutes < 24 * 60 -> pluralStringResource(R.plurals.hours_ago, minutes / 60, minutes / 60)
        else -> pluralStringResource(R.plurals.days_ago, minutes / (24 * 60), minutes / (24 * 60))
    }
}
