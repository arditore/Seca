package com.seca.phone.call

import android.telecom.Call
import android.telecom.CallEndpoint
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seca.core.contacts.SharedProfilesClient
import com.seca.core.design.SecaAppIdentity
import com.seca.core.design.SecaIcons
import com.seca.core.design.SecaPalette
import com.seca.core.design.SecaTheme
import com.seca.core.design.component.SecaAvatar
import com.seca.core.design.component.SecaGroupItem
import com.seca.core.design.component.SecaProfileBadge
import com.seca.core.design.component.SecaSettingRow
import com.seca.core.design.component.rememberContactThumbnail
import com.seca.core.design.secaCallColors
import com.seca.core.design.secaToneColors
import com.seca.core.model.Profile
import com.seca.core.model.initialsOf
import kotlinx.coroutines.delay

/** How long the screen stays to say how the call ended. */
private const val ENDED_MILLIS = 1500L

private val InCallKeys = listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '*', '0', '#')

private val QuickReplies = listOf(
    "Je vous rappelle.",
    "Je ne peux pas répondre pour le moment.",
    "Je suis en réunion.",
    "Écrivez-moi plutôt.",
)

/**
 * The call screen: who is calling and their Seca profile, then the buttons
 * the call allows. It closes on its own once the last call has ended.
 */
@Composable
internal fun InCallRoot(onScreenOffNearEar: (Boolean) -> Unit, onDone: () -> Unit) {
    val calls by CallSession.calls.collectAsState()
    val audio by CallSession.audio.collectAsState()
    val ended by CallSession.ended.collectAsState()
    val shown = calls.primary() ?: ended

    LaunchedEffect(calls.isEmpty(), ended) {
        if (calls.isEmpty()) {
            if (ended != null) delay(ENDED_MILLIS)
            CallSession.clearEnded()
            onDone()
        }
    }
    // The screen only darkens against the ear while talking through the earpiece.
    val talking = shown?.state == Call.STATE_ACTIVE
    val earpiece = audio.endpoint?.endpointType.let { it == null || it == CallEndpoint.TYPE_EARPIECE }
    LaunchedEffect(talking, earpiece) { onScreenOffNearEar(talking && earpiece) }

    SecaTheme(identity = SecaAppIdentity.Phone, palette = rememberSuitePalette()) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            if (shown != null) CallScreen(shown, calls, audio)
        }
    }
}

@Composable
private fun CallScreen(view: CallView, calls: List<CallView>, audio: AudioView) {
    val numbers = CallSession.numbers
    val title = view.title(numbers)
    val (tint, _) = secaToneColors(view.caller?.tone ?: 0)
    var keypad by remember { mutableStateOf(false) }
    var replies by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            // A wash of the profile colour, so the category is felt before it is read.
            .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.5f), MaterialTheme.colorScheme.surface))),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Badges(view)
            SecaAvatar(
                initials = initialsOf(title),
                photoUri = null,
                size = 148.dp,
                tone = view.caller?.tone ?: 0,
                seed = title,
                expressive = true,
                photo = rememberContactThumbnail(view.caller?.photoUri),
                modifier = Modifier.padding(top = 24.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 20.dp),
            )
            view.subtitle(numbers)?.let { ProfileLine(view, it) }
            StatusLine(view)
            OtherCall(view, calls)
            // Pushes the buttons to the bottom of the screen.
            Box(Modifier.weight(1f))
            when {
                view.accountsToChoose.isNotEmpty() -> AccountChooser(view)
                view.state == Call.STATE_RINGING || view.state == Call.STATE_SIMULATED_RINGING ->
                    IncomingControls(view, onReplies = { replies = true })
                view.state == Call.STATE_DISCONNECTED -> Box(Modifier.height(24.dp))
                else -> ActiveControls(view, calls, audio, onKeypad = { keypad = true })
            }
        }
        if (keypad) InCallKeypad(view, onClose = { keypad = false })
    }
    if (replies) {
        QuickReplyDialog(
            onDismiss = { replies = false },
            onSend = {
                CallSession.decline(view.call, it)
                replies = false
            },
        )
    }
}

@Composable
private fun Badges(view: CallView) {
    val badges = buildList<Pair<ImageVector?, String>> {
        if (view.wifi) add(SecaIcons.Wifi to "Appel Wi-Fi")
        if (view.hd) add(null to "HD")
        view.accountLabel?.let { add(null to it) }
    }
    if (badges.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        badges.forEach { (icon, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                icon?.let {
                    Icon(
                        it,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = if (icon != null) 6.dp else 0.dp),
                )
            }
        }
    }
}

/** The caller's profile: the category asked for when someone calls. */
@Composable
private fun ProfileLine(view: CallView, subtitle: String) {
    val caller = view.caller
    if (caller != null && caller.profile.id != Profile.Principal.id) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = 12.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        ) {
            SecaProfileBadge(caller.profile.name, tone = caller.tone, size = 28.dp)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    } else {
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun StatusLine(view: CallView) {
    val text = if (view.state == Call.STATE_ACTIVE && view.connectTime > 0) {
        val seconds by produceState(0L, view.connectTime) {
            while (true) {
                value = (System.currentTimeMillis() - view.connectTime) / 1000
                delay(1000)
            }
        }
        elapsed(seconds.coerceAtLeast(0))
    } else {
        view.status()
    }
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun OtherCall(view: CallView, calls: List<CallView>) {
    val other = calls.firstOrNull { it.call !== view.call && !it.isConferencePart } ?: return
    Text(
        text = "Autre appel : ${other.title(CallSession.numbers)} · ${other.status()}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun IncomingControls(view: CallView, onReplies: () -> Unit) {
    val (green, onGreen) = secaCallColors()
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onReplies) {
            Icon(SecaIcons.Messages, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Répondre par message", modifier = Modifier.padding(start = 8.dp))
        }
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 24.dp),
        ) {
            RoundAction(
                icon = SecaIcons.CallEnd,
                label = "Refuser",
                container = MaterialTheme.colorScheme.error,
                content = MaterialTheme.colorScheme.onError,
            ) { CallSession.decline(view.call) }
            RoundAction(icon = SecaIcons.Phone, label = "Répondre", container = green, content = onGreen) {
                CallSession.answer(view.call)
            }
        }
    }
}

@Composable
private fun ActiveControls(view: CallView, calls: List<CallView>, audio: AudioView, onKeypad: () -> Unit) {
    val another = calls.any { it.call !== view.call && !it.isConferencePart }
    val canMerge = view.canMerge || view.call.conferenceableCalls.isNotEmpty()
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            CallControl(SecaIcons.MicOff, "Muet", checked = audio.muted) { CallSession.setMuted(!audio.muted) }
            CallControl(SecaIcons.Dialpad, "Clavier", checked = false, onClick = onKeypad)
            AudioControl(audio)
        }
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            CallControl(
                icon = SecaIcons.Pause,
                label = "Attente",
                checked = view.state == Call.STATE_HOLDING,
                enabled = view.canHold,
            ) { CallSession.toggleHold(view) }
            if (another) CallControl(SecaIcons.SwapCalls, "Basculer", checked = false) { CallSession.swap() }
            if (canMerge) CallControl(SecaIcons.CallMerge, "Fusionner", checked = false) { CallSession.merge(view) }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 16.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(120.dp)
                    .height(68.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
                    .clickable(onClickLabel = "Raccrocher", role = Role.Button) { CallSession.hangUp(view.call) },
            ) {
                Icon(SecaIcons.CallEnd, contentDescription = "Raccrocher", tint = MaterialTheme.colorScheme.onError)
            }
        }
    }
}

/** Speaker, or the list of devices when a headset or a car is also connected. */
@Composable
private fun AudioControl(audio: AudioView) {
    var open by remember { mutableStateOf(false) }
    val type = audio.endpoint?.endpointType ?: CallEndpoint.TYPE_EARPIECE
    Box {
        CallControl(
            icon = iconFor(type),
            label = "Haut-parleur",
            checked = type == CallEndpoint.TYPE_SPEAKER || type == CallEndpoint.TYPE_BLUETOOTH,
        ) {
            if (audio.endpoints.size > 2) open = true else toggleSpeaker(audio)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            audio.endpoints.forEach { endpoint ->
                DropdownMenuItem(
                    text = { Text(endpoint.endpointName.toString()) },
                    leadingIcon = { Icon(iconFor(endpoint.endpointType), contentDescription = null) },
                    trailingIcon = if (endpoint == audio.endpoint) {
                        { Icon(SecaIcons.Check, contentDescription = "Sortie actuelle") }
                    } else {
                        null
                    },
                    onClick = {
                        open = false
                        CallSession.route(endpoint)
                    },
                )
            }
        }
    }
}

private fun iconFor(type: Int): ImageVector = when (type) {
    CallEndpoint.TYPE_BLUETOOTH -> SecaIcons.Bluetooth
    CallEndpoint.TYPE_WIRED_HEADSET -> SecaIcons.Headset
    else -> SecaIcons.VolumeUp
}

private fun toggleSpeaker(audio: AudioView) {
    val speaker = audio.endpoints.firstOrNull { it.endpointType == CallEndpoint.TYPE_SPEAKER }
    val quiet = audio.endpoints.firstOrNull { it.endpointType == CallEndpoint.TYPE_WIRED_HEADSET }
        ?: audio.endpoints.firstOrNull { it.endpointType == CallEndpoint.TYPE_EARPIECE }
    val target = if (audio.endpoint?.endpointType == CallEndpoint.TYPE_SPEAKER) quiet else speaker
    target?.let(CallSession::route)
}

@Composable
private fun CallControl(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    // A control in use fills with the accent and squares off, as M3 Expressive toggles do.
    val corner by animateDpAsState(if (checked) 20.dp else 36.dp, label = "corner")
    val container by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "container",
    )
    val content = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(corner))
                .background(if (enabled) container else MaterialTheme.colorScheme.surfaceContainer)
                .clickable(enabled = enabled, onClickLabel = label, role = Role.Switch, onClick = onClick),
        ) {
            Icon(icon, contentDescription = null, tint = if (enabled) content else content.copy(alpha = 0.38f))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, container: Color, content: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(container)
                .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        ) {
            Icon(icon, contentDescription = null, tint = content)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Android asks which SIM places the call; without an answer the call would wait forever. */
@Composable
private fun AccountChooser(view: CallView) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
    ) {
        view.accountsToChoose.forEachIndexed { index, account ->
            SecaGroupItem(
                index = index,
                count = view.accountsToChoose.size,
                onClick = { CallSession.chooseAccount(view.call, account) },
            ) {
                SecaSettingRow(
                    icon = SecaIcons.Phone,
                    title = CallSession.accountName(account),
                    subtitle = "Passer cet appel avec cette carte",
                )
            }
        }
        TextButton(
            onClick = { CallSession.hangUp(view.call) },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) { Text("Annuler") }
    }
}

/** The keypad during a call, for voice menus; each key is sent down the line. */
@Composable
private fun InCallKeypad(view: CallView, onClose: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
        ) {
            Text(
                text = view.title(CallSession.numbers),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = typed.ifEmpty { " " },
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            Box(Modifier.weight(1f))
            InCallKeys.chunked(3).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 12.dp),
                ) {
                    row.forEach { key ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable(role = Role.Button) {
                                    typed += key
                                    CallSession.dtmf(view.call, key)
                                },
                        ) {
                            Text(
                                text = key.toString(),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
            TextButton(onClick = onClose, modifier = Modifier.padding(top = 8.dp)) { Text("Fermer le clavier") }
        }
    }
}

@Composable
private fun QuickReplyDialog(onDismiss: () -> Unit, onSend: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(SecaIcons.Messages, contentDescription = null) },
        title = { Text("Refuser et répondre") },
        text = {
            Column {
                QuickReplies.forEach { reply ->
                    Text(
                        text = reply,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onSend(reply) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** The palette chosen in Seca Contacts, so the call screen matches the rest of the suite. */
@Composable
private fun rememberSuitePalette(): SecaPalette? {
    val context = LocalContext.current
    val palette by produceState<SecaPalette?>(initialValue = null, context) {
        val name = SharedProfilesClient(context.contentResolver).load().palette
        value = SecaPalette.entries.firstOrNull { it.name == name }
    }
    return palette
}
