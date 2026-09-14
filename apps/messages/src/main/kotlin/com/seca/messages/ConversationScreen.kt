package com.seca.messages

import android.telephony.SmsMessage
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seca.core.design.SecaIcons
import com.seca.core.model.Profile
import com.seca.messages.link.LinkTyping
import com.seca.messages.sms.Message
import com.seca.messages.sms.MessageStatus
import com.seca.messages.sms.OneTimeCode
import com.seca.messages.sms.ScheduledMessage
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Two messages closer than this, from the same side, read as one block. */
private const val JOIN_MILLIS = 2 * 60 * 1000L

/** How long "écrit…" stays after the contact's last typing notice. */
private const val TYPING_SHOWN_MILLIS = 6_000L

@Composable
internal fun ConversationScreen(
    screen: MessagesScreen.Conversation,
    ui: MessagesUi,
    viewModel: MessagesViewModel,
    isDefaultApp: Boolean,
) {
    val context = LocalContext.current
    val messages by produceState(initialValue = emptyList<Message>(), screen.threadId, screen.address) {
        viewModel.messagesOf(screen.threadId, screen.address).collect { value = it }
    }
    // Opening the conversation, and every message arriving while it is open, counts as read.
    LaunchedEffect(screen.threadId, messages.size) { viewModel.markRead(screen.threadId, screen.address) }
    var text by rememberSaveable(screen.address) { mutableStateOf(screen.draft) }
    var confirmDelete by remember { mutableStateOf(false) }
    var scheduling by remember { mutableStateOf(false) }
    // The list grows upwards, so the latest to leave comes first and sits at the very bottom.
    val scheduled = remember(ui.scheduled, screen.address) { ui.scheduledFor(screen.address).asReversed() }

    val peer = rememberLinkPeer(screen.address)
    // Once the contact is connected, messages go encrypted through Seca Link, which needs no SMS role.
    val linked = ui.link.enabled && peer?.ready == true
    val typingNotices by LinkTyping.typing.collectAsState()
    val typingAt = peer?.number?.let { typingNotices[it] }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(typingAt) {
        now = System.currentTimeMillis()
        if (typingAt != null) {
            delay(TYPING_SHOWN_MILLIS)
            now = System.currentTimeMillis()
        }
    }
    val typing = linked && typingAt != null && now - typingAt < TYPING_SHOWN_MILLIS

    if (scheduling) {
        ScheduleDialog(
            onDismiss = { scheduling = false },
            onSchedule = { at ->
                viewModel.schedule(screen.address, text, at)
                text = ""
                scheduling = false
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column {
                val openSafetyNumber = { viewModel.open(SafetyNumberRoute(screen.address)) }
                ConversationTopBar(
                    address = screen.address,
                    ui = ui,
                    linked = peer?.ready == true,
                    verified = peer?.verified == true,
                    typing = typing,
                    onBack = { viewModel.back() },
                    onDelete = { confirmDelete = true },
                    onSafetyNumber = openSafetyNumber,
                )
                if (peer != null && peer.keyChangedAt > 0) {
                    KeyChangedBanner(screen.address, ui.nameOf(screen.address), onVerify = openSafetyNumber)
                }
            }
        },
        bottomBar = {
            Composer(
                text = text,
                onText = {
                    text = it
                    if (linked && it.isNotBlank()) viewModel.typing(screen.address)
                },
                enabled = isDefaultApp || linked,
                encrypted = linked,
                onSend = {
                    viewModel.send(screen.address, text)
                    text = ""
                },
                onSchedule = { scheduling = true },
            )
        },
    ) { padding ->
        // Newest at the bottom, where the eye and the keyboard are; the list grows upwards.
        val newestFirst = remember(messages) { messages.asReversed() }
        LazyColumn(
            reverseLayout = true,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // What is waiting to leave sits below the last message sent.
            items(scheduled, key = { "scheduled-${it.id}" }, contentType = { "scheduled" }) { message ->
                ScheduledBubble(
                    message = message,
                    onSendNow = { viewModel.sendScheduledNow(message) },
                    onEdit = {
                        viewModel.cancelScheduled(message.id)
                        text = message.body
                    },
                    onCancel = { viewModel.cancelScheduled(message.id) },
                )
            }
            itemsIndexed(newestFirst, key = { _, message -> message.id }, contentType = { _, _ -> "message" }) { index, message ->
                val older = newestFirst.getOrNull(index + 1)
                val newer = newestFirst.getOrNull(index - 1)
                Column {
                    if (older == null || !sameDay(older.date, message.date)) DaySeparator(message.date)
                    Bubble(
                        message = message,
                        joinedAbove = older != null && joined(older, message),
                        joinedBelow = newer != null && joined(message, newer),
                        isLatestOutgoing = message.outgoing && (newer == null || !newer.outgoing),
                        onRetry = { viewModel.retry(message) },
                        onSendAsSms = { viewModel.sendAsSms(message) },
                        onCopy = { copyText(context, "Message", message.body) },
                        onDelete = { viewModel.deleteMessage(message) },
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(SecaIcons.Delete, contentDescription = null) },
            title = { Text("Supprimer la conversation ?") },
            text = { Text("Tous les messages avec ${ui.nameOf(screen.address)} seront supprimés de ce téléphone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteConversation(screen.threadId, screen.address)
                        viewModel.back()
                    },
                ) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

private fun joined(first: Message, second: Message): Boolean =
    first.outgoing == second.outgoing && abs(second.date - first.date) < JOIN_MILLIS && sameDay(first.date, second.date)

/** Back, who the conversation is with and their profile, a call button and the rest in a menu. */
@Composable
private fun ConversationTopBar(
    address: String,
    ui: MessagesUi,
    linked: Boolean,
    verified: Boolean,
    typing: Boolean,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onSafetyNumber: () -> Unit,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val contact = ui.contactOf(address)
    val subtitle = if (contact != null) {
        listOfNotNull(
            ui.profileOf(contact).takeIf { it.id != Profile.Principal.id }?.name,
            ui.numbers.display(address),
        ).joinToString(" · ")
    } else {
        ui.numbers.describe(address)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .height(64.dp)
            .padding(horizontal = 4.dp),
    ) {
        IconButton(onClick = onBack) { Icon(SecaIcons.Back, contentDescription = "Retour") }
        PersonAvatar(contact, ui, size = 40.dp)
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = ui.nameOf(address),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when {
                typing -> Text(
                    text = "écrit…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                linked -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        SecaIcons.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = if (verified) "Chiffré · Vérifié" else "Chiffré par Seca Link",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                else -> subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        IconButton(onClick = { call(context, address) }) { Icon(SecaIcons.Phone, contentDescription = "Appeler") }
        Box {
            IconButton(onClick = { menuOpen = true }) { Icon(SecaIcons.MoreVert, contentDescription = "Plus d'options") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (linked) {
                    MenuEntry("Numéro de sécurité", SecaIcons.Shield) {
                        menuOpen = false
                        onSafetyNumber()
                    }
                }
                if (contact != null) {
                    MenuEntry("Voir la fiche", SecaIcons.Contacts) {
                        menuOpen = false
                        openContact(context, contact.id, contact.lookupKey)
                    }
                } else {
                    MenuEntry("Ajouter aux contacts", SecaIcons.PersonAdd) {
                        menuOpen = false
                        addContact(context, address)
                    }
                }
                MenuEntry("Copier le numéro", SecaIcons.ContentCopy) {
                    menuOpen = false
                    copyText(context, "Numéro", address)
                }
                MenuEntry("Supprimer la conversation", SecaIcons.Delete) {
                    menuOpen = false
                    onDelete()
                }
            }
        }
    }
}

@Composable
private fun DaySeparator(date: Long) {
    Text(
        text = dayTitleOf(date),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp),
    )
}

/**
 * One message. Sent ones sit on the right in the accent, received ones on the
 * left; a block of messages shares its inner corners, and the latest sent one
 * says whether it went through, and for Seca Link whether it was received and
 * read. A lock marks what travelled encrypted.
 */
@Composable
private fun Bubble(
    message: Message,
    joinedAbove: Boolean,
    joinedBelow: Boolean,
    isLatestOutgoing: Boolean,
    onRetry: () -> Unit,
    onSendAsSms: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val mine = message.outgoing
    val failed = message.status == MessageStatus.Failed
    val round = 20.dp
    val tight = 6.dp
    val shape = if (mine) {
        RoundedCornerShape(
            topStart = round,
            topEnd = if (joinedAbove) tight else round,
            bottomEnd = if (joinedBelow) tight else round,
            bottomStart = round,
        )
    } else {
        RoundedCornerShape(
            topStart = if (joinedAbove) tight else round,
            topEnd = round,
            bottomEnd = round,
            bottomStart = if (joinedBelow) tight else round,
        )
    }
    val colors = MaterialTheme.colorScheme
    val container = when {
        failed -> colors.errorContainer
        mine -> colors.primaryContainer
        else -> colors.surfaceContainerHigh
    }
    val content = when {
        failed -> colors.onErrorContainer
        mine -> colors.onPrimaryContainer
        else -> colors.onSurface
    }
    val time = timeOf(context, message.date)
    val caption = when {
        failed && message.encrypted -> "Non envoyé · Toucher pour réessayer"
        failed -> "Échec de l'envoi · Toucher pour réessayer"
        message.status == MessageStatus.Sending -> "Envoi…"
        isLatestOutgoing && message.status == MessageStatus.Read -> "Lu · $time"
        isLatestOutgoing && message.status == MessageStatus.Delivered -> "Reçu · $time"
        isLatestOutgoing -> "Envoyé · $time"
        !joinedBelow -> time
        else -> null
    }

    Column(
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (joinedAbove) 2.dp else 8.dp),
    ) {
        Box {
            Text(
                text = message.body,
                style = MaterialTheme.typography.bodyLarge,
                color = content,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(shape)
                    .background(container)
                    .combinedClickable(
                        onClick = { if (failed) onRetry() },
                        onLongClickLabel = "Plus d'actions",
                        onLongClick = { menuOpen = true },
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                MenuEntry("Copier", SecaIcons.ContentCopy) {
                    menuOpen = false
                    onCopy()
                }
                if (failed) {
                    MenuEntry("Réessayer", SecaIcons.Send) {
                        menuOpen = false
                        onRetry()
                    }
                    if (message.encrypted) {
                        MenuEntry("Envoyer en SMS non chiffré", SecaIcons.Messages) {
                            menuOpen = false
                            onSendAsSms()
                        }
                    }
                }
                MenuEntry("Supprimer", SecaIcons.Delete) {
                    menuOpen = false
                    onDelete()
                }
            }
        }
        // A verification code can be copied straight from the message.
        val code = remember(message.body) { if (mine) null else OneTimeCode.find(message.body) }
        code?.let {
            TextButton(onClick = { copySensitive(context, it) }) {
                Icon(SecaIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("Copier le code $it", modifier = Modifier.padding(start = 6.dp))
            }
        }
        caption?.let {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                if (message.encrypted) {
                    Icon(
                        SecaIcons.Lock,
                        contentDescription = "Chiffré",
                        tint = if (failed) colors.error else colors.onSurfaceVariant,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(12.dp),
                    )
                }
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (failed) colors.error else colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** A message waiting for its time: outlined rather than filled, with when it leaves. */
@Composable
private fun ScheduledBubble(message: ScheduledMessage, onSendNow: () -> Unit, onEdit: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    // Past its time, the alarm is about to fire, or the phone could not send it and said so.
    val waiting = message.at <= System.currentTimeMillis()
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Box {
            Text(
                text = message.body,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .border(1.5.dp, colors.primary, shape)
                    .clip(shape)
                    .combinedClickable(
                        onClick = { menuOpen = true },
                        onLongClickLabel = "Plus d'actions",
                        onLongClick = { menuOpen = true },
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                MenuEntry("Envoyer maintenant", SecaIcons.Send) {
                    menuOpen = false
                    onSendNow()
                }
                MenuEntry("Modifier", SecaIcons.Edit) {
                    menuOpen = false
                    onEdit()
                }
                MenuEntry("Annuler l'envoi", SecaIcons.Delete) {
                    menuOpen = false
                    onCancel()
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            Icon(SecaIcons.Schedule, contentDescription = null, tint = colors.primary, modifier = Modifier.size(14.dp))
            Text(
                text = if (waiting) "En attente d'envoi" else "Programmé · ${scheduleTimeOf(context, message.at)}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/**
 * The text field, the schedule and send buttons, lifted above the keyboard.
 * With Seca Link, it says the message leaves encrypted, and SMS counting and
 * scheduling, which belong to SMS, step aside.
 */
@Composable
private fun Composer(
    text: String,
    onText: (String) -> Unit,
    enabled: Boolean,
    encrypted: Boolean,
    onSend: () -> Unit,
    onSchedule: () -> Unit,
) {
    // How many SMS the text takes: past 160 plain characters, or 70 with accents or emoji, it is split.
    val parts = remember(text, encrypted) { if (text.isEmpty() || encrypted) 0 else SmsMessage.calculateLength(text, false)[0] }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (!enabled) {
            Text(
                text = "Activez Seca Messages comme application SMS pour envoyer.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            TextField(
                value = text,
                onValueChange = onText,
                placeholder = { Text(if (encrypted) "Message chiffré" else "Message") },
                leadingIcon = if (encrypted) {
                    { Icon(SecaIcons.Lock, contentDescription = "Chiffré par Seca Link", modifier = Modifier.size(18.dp)) }
                } else {
                    null
                },
                // Once there is something to send, an SMS can also leave later.
                trailingIcon = if (enabled && !encrypted && text.isNotBlank()) {
                    {
                        IconButton(onClick = onSchedule) {
                            Icon(SecaIcons.Schedule, contentDescription = "Programmer l'envoi")
                        }
                    }
                } else {
                    null
                },
                supportingText = if (parts > 1) {
                    { Text("$parts SMS") }
                } else {
                    null
                },
                shape = RoundedCornerShape(28.dp),
                maxLines = 6,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f),
            )
            FilledIconButton(
                onClick = onSend,
                enabled = enabled && text.isNotBlank(),
                modifier = Modifier
                    .padding(start = 8.dp, bottom = if (parts > 1) 24.dp else 0.dp)
                    .size(56.dp),
            ) {
                Icon(SecaIcons.Send, contentDescription = if (encrypted) "Envoyer chiffré" else "Envoyer")
            }
        }
    }
}
