package com.seca.phone

import android.content.ClipboardManager
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seca.core.contacts.NumberMatch
import com.seca.core.design.SecaIcons
import com.seca.core.design.component.SecaAvatar
import com.seca.core.design.component.SecaGroupItem
import com.seca.core.design.component.SecaTopBar
import com.seca.core.design.component.rememberContactThumbnail
import com.seca.core.design.secaCallColors
import com.seca.core.model.Profile

private val Keys = listOf(
    '1' to "", '2' to "ABC", '3' to "DEF",
    '4' to "GHI", '5' to "JKL", '6' to "MNO",
    '7' to "PQRS", '8' to "TUV", '9' to "WXYZ",
    '*' to "", '0' to "+", '#' to "",
)

private const val TONE_VOLUME = 80
private const val TONE_MILLIS = 120

@Composable
internal fun DialerScreen(
    initial: String,
    ui: PhoneUi,
    viewModel: PhoneViewModel,
    onCall: (String) -> Unit,
    onVoicemail: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    var typed by rememberSaveable(initial) { mutableStateOf(initial.filter { it.isDigit() || it in "+*#" }) }
    val playTone = rememberKeyTones()
    val suggestions = remember(typed, ui.contacts) { keypadSuggestions(typed, ui.contacts, ui.numbers, limit = 3) }
    val typedKey = ui.numbers.key(typed)
    val exact = suggestions.firstOrNull()?.takeIf { typed.isNotEmpty() && ui.numbers.key(it.number.raw) == typedKey }
    val lastDialled = remember(ui.calls) { ui.calls.firstOrNull { it.type == CallType.Outgoing && it.callable }?.number }

    val press: (Char) -> Unit = { key ->
        typed += key
        playTone(key)
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
    val longPress: (Char) -> Unit = { key ->
        when {
            key == '0' -> {
                typed += '+'
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
            key == '1' && typed.isEmpty() -> onVoicemail()
            else -> press(key)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { SecaTopBar(title = "", onBack = { viewModel.back() }, navigationIcon = SecaIcons.Close) },
    ) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(bottom = 16.dp),
        ) {
            // Suggestions sit just above the number, closest to where the eye already is.
            Box(
                contentAlignment = Alignment.BottomCenter,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                Column(Modifier.padding(bottom = 8.dp)) {
                    suggestions.forEachIndexed { index, match ->
                        SecaGroupItem(index = index, count = suggestions.size, onClick = { onCall(match.number.raw) }) {
                            SuggestionRow(match, ui)
                        }
                    }
                    if (typed.count(Char::isDigit) >= 3 && exact == null) {
                        TextButton(
                            onClick = { addContact(context, typed) },
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                        ) {
                            Icon(SecaIcons.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Créer un contact", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }

            NumberDisplay(typed, ui, exact, onPaste = { typed = it })
            Keypad(onKey = press, onLongPress = longPress)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                Box(Modifier.weight(1f))
                // With nothing typed, the button brings back the last number dialled, as dialers do.
                CallButton(
                    onClick = {
                        if (typed.any(Char::isDigit)) onCall(typed) else lastDialled?.let { typed = it }
                    },
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (typed.isNotEmpty()) {
                        BackspaceButton(onErase = { typed = typed.dropLast(1) }, onClear = { typed = "" })
                    }
                }
            }
        }
    }
}

/**
 * The number, formatted as it is typed; under it, the contact it belongs to or
 * the country it was read as. A long press pastes a copied number.
 */
@Composable
private fun NumberDisplay(typed: String, ui: PhoneUi, exact: NumberMatch?, onPaste: (String) -> Unit) {
    val context = LocalContext.current
    val formatted = if (typed.isEmpty()) "" else ui.numbers.formatTyping(typed)
    val line = when {
        exact != null -> listOfNotNull(
            exact.contact.displayName,
            ui.profileOf(exact.contact).takeIf { it.id != Profile.Principal.id }?.name,
        ).joinToString(" · ")
        else -> ui.numbers.describe(typed)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(
                onClick = {},
                onLongClickLabel = "Coller un numéro",
                onLongClick = { pastedNumber(context)?.let(onPaste) },
            )
            .padding(vertical = 8.dp),
    ) {
        if (formatted.isEmpty()) {
            Text(
                text = "Saisir un numéro",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        } else {
            Text(
                text = formatted,
                style = if (formatted.length > 14) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = line ?: " ",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Keypad(onKey: (Char) -> Unit, onLongPress: (Char) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp),
    ) {
        Keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (key, letters) ->
                    Key(key, letters, onKey, onLongPress, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Key(
    key: Char,
    letters: String,
    onKey: (Char) -> Unit,
    onLongPress: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .height(64.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(role = Role.Button, onLongClick = { onLongPress(key) }, onClick = { onKey(key) }),
    ) {
        Text(key.toString(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        when {
            letters.isNotEmpty() -> Text(
                letters,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Long press on 1 calls the voicemail; the symbol says so.
            key == '1' -> Icon(
                SecaIcons.Voicemail,
                contentDescription = "Messagerie vocale",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun CallButton(onClick: () -> Unit) {
    val (container, content) = secaCallColors()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 96.dp, height = 64.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Icon(SecaIcons.Phone, contentDescription = "Appeler", tint = content)
    }
}

@Composable
private fun BackspaceButton(onErase: () -> Unit, onClear: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .combinedClickable(
                role = Role.Button,
                onLongClickLabel = "Tout effacer",
                onLongClick = onClear,
                onClick = onErase,
            ),
    ) {
        Icon(SecaIcons.Backspace, contentDescription = "Effacer", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SuggestionRow(match: NumberMatch, ui: PhoneUi) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        SecaAvatar(
            initials = match.contact.initials,
            photoUri = match.contact.photoUri,
            size = 40.dp,
            tone = ui.toneOf(match.contact),
            seed = match.contact.displayName,
            photo = rememberContactThumbnail(match.contact.photoUri),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(
                text = match.contact.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = ui.numbers.display(match.number.raw),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(SecaIcons.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

/**
 * Plays the key's tone, unless the user turned dialing tones off or the phone
 * is on silent or vibrate — the same rules as the system dialer.
 */
@Composable
private fun rememberKeyTones(): (Char) -> Unit {
    val context = LocalContext.current
    val generator = remember {
        val wanted = Settings.System.getInt(context.contentResolver, Settings.System.DTMF_TONE_WHEN_DIALING, 1) == 1
        if (wanted) runCatching { ToneGenerator(AudioManager.STREAM_DTMF, TONE_VOLUME) }.getOrNull() else null
    }
    DisposableEffect(generator) { onDispose { generator?.release() } }
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    return remember(generator) {
        { key: Char ->
            val tone = toneFor(key)
            if (tone != null && audio?.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                generator?.startTone(tone, TONE_MILLIS)
            }
        }
    }
}

private fun toneFor(key: Char): Int? = when (key) {
    '0' -> ToneGenerator.TONE_DTMF_0
    '1' -> ToneGenerator.TONE_DTMF_1
    '2' -> ToneGenerator.TONE_DTMF_2
    '3' -> ToneGenerator.TONE_DTMF_3
    '4' -> ToneGenerator.TONE_DTMF_4
    '5' -> ToneGenerator.TONE_DTMF_5
    '6' -> ToneGenerator.TONE_DTMF_6
    '7' -> ToneGenerator.TONE_DTMF_7
    '8' -> ToneGenerator.TONE_DTMF_8
    '9' -> ToneGenerator.TONE_DTMF_9
    '*' -> ToneGenerator.TONE_DTMF_S
    '#' -> ToneGenerator.TONE_DTMF_P
    else -> null
}

/** The digits of the copied text, read only when the user asks to paste. */
private fun pastedNumber(context: Context): String? {
    val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context).toString().filter { it.isDigit() || it in "+*#" }.ifEmpty { null }
}
