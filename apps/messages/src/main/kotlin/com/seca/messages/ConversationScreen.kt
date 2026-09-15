package com.seca.messages

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.provider.MediaStore
import android.telephony.SmsMessage
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.seca.core.design.SecaIcons
import com.seca.core.design.secaToneColors
import com.seca.core.link.handshake.Handshake
import com.seca.core.model.Profile
import com.seca.messages.link.LinkTimers
import com.seca.messages.link.LinkTyping
import com.seca.messages.sms.LinkSms
import com.seca.messages.sms.Message
import com.seca.messages.sms.MessageStatus
import com.seca.messages.sms.OneTimeCode
import com.seca.messages.sms.ScheduledMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalResources
import com.seca.core.contacts.describe
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

/** Two messages closer than this, from the same side, read as one block. */
private const val JOIN_MILLIS = 2 * 60 * 1000L

/** How many messages up the list still counts as reading the latest, which a new message then joins. */
private const val NEAR_BOTTOM_ITEMS = 2

/** How long "typing…" stays after the contact's last typing notice. */
private const val TYPING_SHOWN_MILLIS = 6_000L

/** A photo in a bubble is decoded no larger than this; the viewer takes a larger one. */
private const val PREVIEW_EDGE_PX = 900
private const val VIEWER_EDGE_PX = 2400

private const val TICK_MILLIS = 200L

/** The reactions a long press offers, as the most used ones. */
private val QuickReactions = listOf("❤️", "👍", "😂", "😮", "😢", "🙏")

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
    // While it is on screen, what arrives in it is shown here rather than notified.
    val shownKey = remember(screen.address) { LinkSms.keyOf(context, screen.address) ?: screen.address }
    LifecycleResumeEffect(shownKey) {
        ActiveConversation.show(shownKey)
        onPauseOrDispose { ActiveConversation.hide(shownKey) }
    }
    ConversationContent(screen, ui, viewModel, isDefaultApp, messages)
}

/** The conversation as drawn from its [messages], oldest first; apart from their loading, so any can be drawn. */
@Composable
internal fun ConversationContent(
    screen: MessagesScreen.Conversation,
    ui: MessagesUi,
    viewModel: MessagesViewModel,
    isDefaultApp: Boolean,
    messages: List<Message>,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by rememberSaveable(screen.address) { mutableStateOf(screen.draft) }
    var confirmDelete by remember { mutableStateOf(false) }
    var scheduling by remember { mutableStateOf(false) }
    var choosingTimer by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<String?>(null) }
    var replyingTo by remember { mutableStateOf<Message?>(null) }
    // The list grows upwards, so the latest to leave comes first and sits at the very bottom.
    val scheduled = remember(ui.scheduled, screen.address) { ui.scheduledFor(screen.address).asReversed() }
    val byLinkId = remember(messages) { messages.filter { it.linkId != null }.associateBy { it.linkId } }
    val name = ui.nameOf(screen.address)

    val peer = rememberLinkPeer(screen.address)
    // Once the contact is connected, messages go encrypted through Seca Link, which needs no SMS role.
    val linked = ui.link.enabled && peer?.ready == true
    val canInvite = ui.link.enabled && peer?.ready != true && remember(screen.address) { LinkSms.keyOf(context, screen.address) != null }
    val invitationPending = canInvite && (peer?.invitedAt ?: 0L) > 0L
    val timers by LinkTimers.of(context).timers.collectAsState()
    val timer = peer?.number?.let { timers[it] } ?: 0
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

    // A message whose time is up leaves the screen as soon as it goes.
    val nextExpiry = remember(messages) { messages.filter { it.expiresAt > 0 }.minOfOrNull { it.expiresAt } }
    LaunchedEffect(nextExpiry) {
        val at = nextExpiry ?: return@LaunchedEffect
        delay((at - System.currentTimeMillis()).coerceAtLeast(0))
        viewModel.sweepExpired()
    }

    // Photos come from the system's photo picker: the app never reads the gallery itself.
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.sendPhoto(screen.address, uri)
    }

    // Voice messages: the microphone is asked for the first time one is recorded.
    val recorder = remember { VoiceRecorder(context) }
    val micUnavailable = stringResource(R.string.mic_unavailable)
    val photoSavedText = stringResource(R.string.photo_saved)
    val saveFailedText = stringResource(R.string.save_failed)
    var recording by remember { mutableStateOf(false) }
    var recordingMillis by remember { mutableLongStateOf(0L) }
    val finishRecording: () -> Unit = {
        recording = false
        recorder.stop()?.let { viewModel.sendVoice(screen.address, it.absolutePath) }
    }
    LaunchedEffect(recording) {
        while (recording) {
            recordingMillis = recorder.elapsedMillis
            if (recordingMillis >= VoiceRecorder.MAX_MILLIS) finishRecording()
            delay(TICK_MILLIS)
        }
    }
    DisposableEffect(Unit) { onDispose { recorder.cancel() } }
    val startRecording = {
        if (recorder.start()) {
            recordingMillis = 0L
            recording = true
        } else {
            Toast.makeText(context, micUnavailable, Toast.LENGTH_SHORT).show()
        }
    }
    val askMicrophone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording()
    }

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
    if (choosingTimer) {
        TimerDialog(
            current = timer,
            onDismiss = { choosingTimer = false },
            onChoose = { seconds ->
                viewModel.setTimer(screen.address, seconds)
                choosingTimer = false
            },
        )
    }

    // Every conversation gets a faint light from the top: the colour of the contact's profile, or the
    // app's own accent for a contact without a profile and for an unknown number, as on the call screen.
    val contact = ui.contactOf(screen.address)
    val (wash, _) = secaToneColors(contact?.let { ui.toneOf(it) } ?: 0)
    val surface = MaterialTheme.colorScheme.surface
    Box(
        Modifier
            .fillMaxSize()
            .background(surface)
            .background(Brush.verticalGradient(0f to wash.copy(alpha = 0.55f), 0.32f to surface)),
    ) {
        Scaffold(
            // Transparent over the light; the icons keep the theme's colour rather than black.
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            topBar = {
                Column {
                    val openSafetyNumber = { viewModel.open(SafetyNumberRoute(screen.address)) }
                    ConversationTopBar(
                        address = screen.address,
                        ui = ui,
                        linked = peer?.ready == true,
                        verified = peer?.verified == true,
                        typing = typing,
                        invitationPending = invitationPending,
                        timerSeconds = if (linked) timer else 0,
                        onBack = { viewModel.back() },
                        onDelete = { confirmDelete = true },
                        onSafetyNumber = openSafetyNumber,
                        onInvite = if (canInvite) {
                            { viewModel.inviteToLink(screen.address) }
                        } else {
                            null
                        },
                        onTimer = if (linked) {
                            { choosingTimer = true }
                        } else {
                            null
                        },
                    )
                    if (peer != null && peer.keyChangedAt > 0) {
                        KeyChangedBanner(screen.address, name, onVerify = openSafetyNumber)
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
                        viewModel.send(screen.address, text, replyingTo?.linkId)
                        text = ""
                        replyingTo = null
                    },
                    onSchedule = { scheduling = true },
                    onAttach = if (linked) {
                        { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    } else {
                        null
                    },
                    replyingTo = replyingTo,
                    replyName = name,
                    onCancelReply = { replyingTo = null },
                    recording = recording,
                    recordingMillis = recordingMillis,
                    onStartVoice = if (linked) {
                        {
                            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                startRecording()
                            } else {
                                askMicrophone.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    } else {
                        null
                    },
                    onCancelVoice = {
                        recording = false
                        recorder.cancel()
                    },
                    onSendVoice = finishRecording,
                )
            },
        ) { padding ->
            // Newest at the bottom, where the eye and the keyboard are; the list grows upwards.
            val newestFirst = remember(messages) { messages.asReversed() }
            val listState = rememberLazyListState()
            // The list keeps its place by message, so one arriving at the bottom would slide in below the
            // screen, unseen: it is brought into view. Someone reading further up stays put, unless it is theirs.
            val newest = messages.lastOrNull()
            var followed by remember { mutableStateOf<Long?>(null) }
            LaunchedEffect(newest?.id) {
                val before = followed
                followed = newest?.id
                // The first load already opens at the bottom.
                if (before == null || newest == null) return@LaunchedEffect
                if (newest.outgoing || listState.firstVisibleItemIndex <= scheduled.size + NEAR_BOTTOM_ITEMS) {
                    listState.animateScrollToItem(0)
                }
            }
            LazyColumn(
                state = listState,
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
                            quoted = message.replyTo?.let { byLinkId[it] },
                            contactName = name,
                            joinedAbove = older != null && joined(older, message),
                            joinedBelow = newer != null && joined(message, newer),
                            isLatestOutgoing = message.outgoing && (newer == null || !newer.outgoing),
                            canReact = linked && message.linkId != null &&
                                message.status != MessageStatus.Failed && message.status != MessageStatus.Sending,
                            onRetry = { viewModel.retry(message) },
                            onSendAsSms = { viewModel.sendAsSms(message) },
                            onCopy = { copyText(context, "Message", message.body) },
                            onDelete = { viewModel.deleteMessage(message) },
                            onOpenPhoto = { viewing = it },
                            onSavePhoto = { path ->
                                scope.launch {
                                    val saved = saveToGallery(context, path)
                                    Toast.makeText(
                                        context,
                                        if (saved) photoSavedText else saveFailedText,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                            onReply = { replyingTo = message },
                            onReact = { emoji -> viewModel.react(message, emoji) },
                        )
                    }
                }
            }
        }
    }

    viewing?.let { path -> PhotoViewer(path, onDismiss = { viewing = null }) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(SecaIcons.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.delete_conversation_title)) },
            text = { Text(stringResource(R.string.delete_conversation_text, name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteConversation(screen.threadId, screen.address)
                        viewModel.back()
                    },
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun joined(first: Message, second: Message): Boolean =
    first.outgoing == second.outgoing && abs(second.date - first.date) < JOIN_MILLIS && sameDay(first.date, second.date)

/** A message as a quote or a notice reads it: a photo and a voice message by what they are. */
@Composable
private fun previewOf(message: Message): String = when {
    message.audio != null -> stringResource(R.string.preview_voice)
    message.image != null -> stringResource(R.string.preview_photo)
    else -> message.body.ifEmpty { stringResource(R.string.preview_photo) }
}

/** Back, who the conversation is with and their profile, a call button and the rest in a menu. */
@Composable
private fun ConversationTopBar(
    address: String,
    ui: MessagesUi,
    linked: Boolean,
    verified: Boolean,
    typing: Boolean,
    invitationPending: Boolean,
    timerSeconds: Int,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onSafetyNumber: () -> Unit,
    onInvite: (() -> Unit)?,
    onTimer: (() -> Unit)?,
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
        ui.numbers.describe(address, LocalResources.current)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(64.dp)
            .padding(horizontal = 4.dp),
    ) {
        IconButton(onClick = onBack) { Icon(SecaIcons.Back, contentDescription = stringResource(R.string.back)) }
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
                    text = stringResource(R.string.typing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                linked || invitationPending -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (linked && timerSeconds > 0) SecaIcons.Timer else SecaIcons.Lock,
                        contentDescription = null,
                        tint = if (linked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = when {
                            !linked -> stringResource(R.string.invitation_sent)
                            timerSeconds > 0 -> stringResource(R.string.encrypted_disappearing, LinkTimers.label(context, timerSeconds))
                            verified -> stringResource(R.string.encrypted_verified)
                            else -> stringResource(R.string.encrypted_by_link)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (linked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
        IconButton(onClick = { call(context, address) }) { Icon(SecaIcons.Phone, contentDescription = stringResource(R.string.call)) }
        Box {
            IconButton(onClick = { menuOpen = true }) { Icon(SecaIcons.MoreVert, contentDescription = stringResource(R.string.more_options)) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (onInvite != null) {
                    MenuEntry(stringResource(if (invitationPending) R.string.resend_invitation else R.string.encrypt_with_link), SecaIcons.Lock) {
                        menuOpen = false
                        onInvite()
                    }
                    // The safety number screen offers the QR codes while no session is open.
                    MenuEntry(stringResource(R.string.connect_in_person), SecaIcons.Link) {
                        menuOpen = false
                        onSafetyNumber()
                    }
                }
                if (onTimer != null) {
                    MenuEntry(stringResource(R.string.disappearing_messages), SecaIcons.Timer) {
                        menuOpen = false
                        onTimer()
                    }
                }
                if (linked) {
                    MenuEntry(stringResource(R.string.safety_number), SecaIcons.Shield) {
                        menuOpen = false
                        onSafetyNumber()
                    }
                }
                if (contact != null) {
                    MenuEntry(stringResource(R.string.view_contact), SecaIcons.Contacts) {
                        menuOpen = false
                        openContact(context, contact.id, contact.lookupKey)
                    }
                } else {
                    MenuEntry(stringResource(R.string.add_to_contacts), SecaIcons.PersonAdd) {
                        menuOpen = false
                        addContact(context, address)
                    }
                }
                MenuEntry(stringResource(R.string.copy_number), SecaIcons.ContentCopy) {
                    menuOpen = false
                    copyText(context, "Number", address)
                }
                MenuEntry(stringResource(R.string.delete_conversation), SecaIcons.Delete) {
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
        text = dayTitleOf(LocalContext.current, date),
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
 * read. A lock marks what travelled encrypted, a timer what will go. A Seca
 * Link handshake sent as text reads as a short notice rather than its code.
 */
@Composable
private fun Bubble(
    message: Message,
    quoted: Message?,
    contactName: String,
    joinedAbove: Boolean,
    joinedBelow: Boolean,
    isLatestOutgoing: Boolean,
    canReact: Boolean,
    onRetry: () -> Unit,
    onSendAsSms: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onOpenPhoto: (String) -> Unit,
    onSavePhoto: (String) -> Unit,
    onReply: () -> Unit,
    onReact: (String) -> Unit,
) {
    val handshake = remember(message.body) { if (message.encrypted) null else Handshake.fromText(message.body) }
    if (handshake != null) {
        HandshakeNotice(handshake, mine = message.outgoing)
        return
    }
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
        failed && message.encrypted -> stringResource(R.string.not_sent_retry)
        failed -> stringResource(R.string.send_failed_retry)
        message.status == MessageStatus.Sending -> stringResource(R.string.sending)
        isLatestOutgoing && message.status == MessageStatus.Read -> stringResource(R.string.read_at, time)
        isLatestOutgoing && message.status == MessageStatus.Delivered -> stringResource(R.string.delivered_at, time)
        isLatestOutgoing -> stringResource(R.string.sent_at, time)
        !joinedBelow -> time
        else -> null
    }
    val image = message.image
    val audio = message.audio

    Column(
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (joinedAbove) 2.dp else 8.dp),
    ) {
        Box {
            val touch = Modifier.combinedClickable(
                onClick = {
                    when {
                        failed -> onRetry()
                        image != null -> onOpenPhoto(image)
                    }
                },
                onLongClickLabel = stringResource(R.string.more_actions),
                onLongClick = { menuOpen = true },
            )
            when {
                image != null -> PhotoBubble(image, shape, container, modifier = touch)
                audio != null -> VoiceBubble(audio, shape, container, content, modifier = touch)
                else -> Column(
                    Modifier
                        .widthIn(max = 300.dp)
                        .clip(shape)
                        .background(container)
                        .then(touch)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    if (message.replyTo != null) QuoteBox(quoted, contactName, content)
                    Text(
                        text = message.body.ifEmpty { stringResource(R.string.preview_photo) },
                        style = MaterialTheme.typography.bodyLarge,
                        color = content,
                    )
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (canReact) {
                    Row(Modifier.padding(horizontal = 8.dp)) {
                        QuickReactions.forEach { emoji ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (message.myReaction == emoji) colors.primaryContainer else Color.Transparent)
                                    .clickable(onClickLabel = stringResource(R.string.react_named, emoji)) {
                                        menuOpen = false
                                        onReact(emoji)
                                    },
                            ) { Text(emoji, style = MaterialTheme.typography.titleLarge) }
                        }
                    }
                    MenuEntry(stringResource(R.string.reply), SecaIcons.Reply) {
                        menuOpen = false
                        onReply()
                    }
                }
                when {
                    image != null -> MenuEntry(stringResource(R.string.save_to_gallery), SecaIcons.Download) {
                        menuOpen = false
                        onSavePhoto(image)
                    }
                    audio == null -> MenuEntry(stringResource(R.string.copy), SecaIcons.ContentCopy) {
                        menuOpen = false
                        onCopy()
                    }
                }
                if (failed) {
                    MenuEntry(stringResource(R.string.retry), SecaIcons.Send) {
                        menuOpen = false
                        onRetry()
                    }
                    if (message.encrypted && image == null && audio == null) {
                        MenuEntry(stringResource(R.string.send_as_sms), SecaIcons.Messages) {
                            menuOpen = false
                            onSendAsSms()
                        }
                    }
                }
                MenuEntry(stringResource(R.string.delete), SecaIcons.Delete) {
                    menuOpen = false
                    onDelete()
                }
            }
        }
        // Reactions sit on the bubble's lower edge, as in every messaging app.
        val reactions = listOfNotNull(message.theirReaction, message.myReaction).distinct()
        if (reactions.isNotEmpty()) {
            Text(
                text = reactions.joinToString(" "),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .offset(y = (-6).dp)
                    .padding(horizontal = 8.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceContainerHighest)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        // A verification code can be copied straight from the message.
        val code = remember(message.body) { if (mine) null else OneTimeCode.find(message.body) }
        code?.let {
            TextButton(onClick = { copySensitive(context, it) }) {
                Icon(SecaIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.copy_code_full, it), modifier = Modifier.padding(start = 6.dp))
            }
        }
        caption?.let {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                if (message.expiresAt > 0) {
                    Icon(
                        SecaIcons.Timer,
                        contentDescription = stringResource(R.string.disappearing),
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(12.dp),
                    )
                }
                if (message.encrypted) {
                    Icon(
                        SecaIcons.Lock,
                        contentDescription = stringResource(R.string.encrypted),
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

/** The message a reply answers, above the reply, in the bubble's own colour. */
@Composable
private fun QuoteBox(quoted: Message?, contactName: String, content: Color) {
    Column(
        Modifier
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(content.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = when {
                quoted == null -> stringResource(R.string.message)
                quoted.outgoing -> stringResource(R.string.you)
                else -> contactName
            },
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = quoted?.let { previewOf(it) } ?: stringResource(R.string.message_deleted),
            style = MaterialTheme.typography.bodyMedium,
            color = content.copy(alpha = 0.8f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A photo in the conversation, at its own proportions within the bubble's width. */
@Composable
private fun PhotoBubble(path: String, shape: RoundedCornerShape, container: Color, modifier: Modifier = Modifier) {
    val photo = rememberPhoto(path, PREVIEW_EDGE_PX)
    val frame = Modifier
        .widthIn(max = 260.dp)
        .clip(shape)
        .background(container)
        .then(modifier)
    if (photo == null) {
        Box(frame.size(width = 220.dp, height = 160.dp))
    } else {
        Image(
            bitmap = photo,
            contentDescription = stringResource(R.string.photo),
            contentScale = ContentScale.Crop,
            modifier = frame
                .heightIn(max = 360.dp)
                .aspectRatio(photo.width.toFloat() / photo.height, matchHeightConstraintsFirst = photo.height > photo.width),
        )
    }
}

/** A voice message: play and pause, how far it has played, and how long it lasts. */
@Composable
private fun VoiceBubble(path: String, shape: RoundedCornerShape, container: Color, content: Color, modifier: Modifier = Modifier) {
    var player by remember(path) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(path) { mutableStateOf(false) }
    var progress by remember(path) { mutableFloatStateOf(0f) }
    val duration by produceState(initialValue = 0L, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(path)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                }
            }.getOrNull() ?: 0L
        }
    }
    DisposableEffect(path) {
        onDispose {
            player?.release()
            player = null
        }
    }
    LaunchedEffect(playing) {
        while (playing) {
            player?.let { if (it.duration > 0) progress = it.currentPosition.toFloat() / it.duration }
            delay(TICK_MILLIS / 2)
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .widthIn(min = 220.dp, max = 280.dp)
            .clip(shape)
            .background(container)
            .then(modifier)
            .padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
    ) {
        IconButton(
            onClick = {
                val current = player ?: runCatching {
                    MediaPlayer().apply {
                        setDataSource(path)
                        setOnCompletionListener {
                            playing = false
                            progress = 0f
                        }
                        prepare()
                    }
                }.getOrNull()?.also { player = it }
                when {
                    current == null -> Unit
                    playing -> {
                        current.pause()
                        playing = false
                    }
                    else -> {
                        current.start()
                        playing = true
                    }
                }
            },
        ) { Icon(if (playing) SecaIcons.Pause else SecaIcons.Play, contentDescription = stringResource(if (playing) R.string.pause else R.string.listen), tint = content) }
        LinearProgressIndicator(
            progress = { progress },
            color = content,
            trackColor = content.copy(alpha = 0.25f),
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(CircleShape),
        )
        Text(
            text = durationText(duration),
            style = MaterialTheme.typography.labelMedium,
            color = content,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

/** "0:42", "3:05". */
private fun durationText(millis: Long): String {
    val seconds = millis / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

/** Seca Link's handshake as the conversation shows it: a short centred notice. */
@Composable
private fun HandshakeNotice(handshake: Handshake, mine: Boolean) {
    val label = stringResource(
        when {
            handshake.type == Handshake.Type.Accept -> R.string.conversation_encrypted
            mine -> R.string.invitation_sent
            else -> R.string.invitation_received
        },
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Icon(SecaIcons.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

/** How long new messages are kept, on both phones. */
@Composable
private fun TimerDialog(current: Int, onDismiss: () -> Unit, onChoose: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(SecaIcons.Timer, contentDescription = null) },
        title = { Text(stringResource(R.string.disappearing_messages)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.timer_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                LinkTimers.Choices.forEach { seconds ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .selectable(selected = seconds == current, role = Role.RadioButton) { onChoose(seconds) }
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = seconds == current, onClick = null)
                        Text(
                            text = LinkTimers.label(LocalContext.current, seconds),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

/** A photo opened on its own, on black, the whole screen; a touch closes it. */
@Composable
private fun PhotoViewer(path: String, onDismiss: () -> Unit) {
    val photo = rememberPhoto(path, VIEWER_EDGE_PX)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClickLabel = stringResource(R.string.close), onClick = onDismiss),
        ) {
            photo?.let { Image(bitmap = it, contentDescription = stringResource(R.string.photo), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(8.dp),
            ) { Icon(SecaIcons.Close, contentDescription = stringResource(R.string.close), tint = Color.White) }
        }
    }
}

/** The photo at [path], decoded off the main thread no larger than [maxEdge] pixels; null until it is ready. */
@Composable
private fun rememberPhoto(path: String, maxEdge: Int): ImageBitmap? {
    val photo by produceState<ImageBitmap?>(initialValue = null, path, maxEdge) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(File(path))) { decoder, info, _ ->
                    val scale = min(1f, maxEdge.toFloat() / max(info.size.width, info.size.height))
                    if (scale < 1f) decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
                }.asImageBitmap()
            }.getOrNull()
        }
    }
    return photo
}

/** Copies a Seca Link photo into the phone's gallery, under Pictures/Seca, only when the owner asks. */
private suspend fun saveToGallery(context: Context, path: String): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Seca-${System.currentTimeMillis()}.webp")
            put(MediaStore.Images.Media.MIME_TYPE, "image/webp")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Seca")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
        resolver.openOutputStream(uri)?.use { out -> File(path).inputStream().use { it.copyTo(out) } }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        true
    }.getOrDefault(false)
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
                        onLongClickLabel = stringResource(R.string.more_actions),
                        onLongClick = { menuOpen = true },
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                MenuEntry(stringResource(R.string.send_now), SecaIcons.Send) {
                    menuOpen = false
                    onSendNow()
                }
                MenuEntry(stringResource(R.string.edit), SecaIcons.Edit) {
                    menuOpen = false
                    onEdit()
                }
                MenuEntry(stringResource(R.string.cancel_send), SecaIcons.Delete) {
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
                text = if (waiting) stringResource(R.string.waiting_to_send) else stringResource(R.string.scheduled_at, scheduleTimeOf(context, message.at)),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/**
 * The text field and its buttons, lifted above the keyboard: photo, schedule,
 * send, or with nothing typed in an encrypted conversation, the microphone.
 * With Seca Link it says the message leaves encrypted, a reply shows what it
 * answers, and SMS counting and scheduling, which belong to SMS, step aside.
 */
@Composable
private fun Composer(
    text: String,
    onText: (String) -> Unit,
    enabled: Boolean,
    encrypted: Boolean,
    onSend: () -> Unit,
    onSchedule: () -> Unit,
    onAttach: (() -> Unit)?,
    replyingTo: Message?,
    replyName: String,
    onCancelReply: () -> Unit,
    recording: Boolean,
    recordingMillis: Long,
    onStartVoice: (() -> Unit)?,
    onCancelVoice: () -> Unit,
    onSendVoice: () -> Unit,
) {
    // How many SMS the text takes: past 160 plain characters, or 70 with accents or emoji, it is split.
    val parts = remember(text, encrypted) { if (text.isEmpty() || encrypted) 0 else SmsMessage.calculateLength(text, false)[0] }
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (!enabled) {
            Text(
                text = stringResource(R.string.enable_to_send),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
            )
        }
        replyingTo?.let { target ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceContainerHigh)
                    .padding(start = 12.dp),
            ) {
                Icon(SecaIcons.Reply, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = if (target.outgoing) stringResource(R.string.reply_to_yourself) else stringResource(R.string.reply_to, replyName),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = previewOf(target),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onCancelReply) { Icon(SecaIcons.Close, contentDescription = stringResource(R.string.cancel_reply)) }
            }
        }
        if (recording) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                IconButton(onClick = onCancelVoice) {
                    Icon(SecaIcons.Delete, contentDescription = stringResource(R.string.cancel_recording), tint = colors.error)
                }
                Box(
                    Modifier
                        .padding(start = 4.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(colors.error),
                )
                Text(
                    text = stringResource(R.string.recording, durationText(recordingMillis)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                )
                FilledIconButton(onClick = onSendVoice, modifier = Modifier.size(56.dp)) {
                    Icon(SecaIcons.Send, contentDescription = stringResource(R.string.send_voice))
                }
            }
            return@Column
        }
        Row(verticalAlignment = Alignment.Bottom) {
            if (onAttach != null) {
                IconButton(
                    onClick = onAttach,
                    modifier = Modifier
                        .padding(end = 4.dp, bottom = 4.dp)
                        .size(48.dp),
                ) { Icon(SecaIcons.Photo, contentDescription = stringResource(R.string.send_encrypted_photo)) }
            }
            TextField(
                value = text,
                onValueChange = onText,
                placeholder = { Text(stringResource(if (encrypted) R.string.encrypted_message else R.string.message)) },
                leadingIcon = if (encrypted) {
                    { Icon(SecaIcons.Lock, contentDescription = stringResource(R.string.encrypted_by_link), modifier = Modifier.size(18.dp)) }
                } else {
                    null
                },
                // Once there is something to send, an SMS can also leave later.
                trailingIcon = if (enabled && !encrypted && text.isNotBlank()) {
                    {
                        IconButton(onClick = onSchedule) {
                            Icon(SecaIcons.Schedule, contentDescription = stringResource(R.string.schedule_send))
                        }
                    }
                } else {
                    null
                },
                supportingText = if (parts > 1) {
                    { Text(pluralStringResource(R.plurals.sms_count, parts, parts)) }
                } else {
                    null
                },
                shape = RoundedCornerShape(28.dp),
                maxLines = 6,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.surfaceContainerHigh,
                    unfocusedContainerColor = colors.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f),
            )
            val startVoice = onStartVoice.takeIf { text.isBlank() }
            FilledIconButton(
                onClick = startVoice ?: onSend,
                enabled = startVoice != null || (enabled && text.isNotBlank()),
                modifier = Modifier
                    .padding(start = 8.dp, bottom = if (parts > 1) 24.dp else 0.dp)
                    .size(56.dp),
            ) {
                Icon(
                    if (startVoice != null) SecaIcons.Mic else SecaIcons.Send,
                    contentDescription = stringResource(
                        when {
                            startVoice != null -> R.string.record_voice
                            encrypted -> R.string.send_encrypted
                            else -> R.string.send
                        },
                    ),
                )
            }
        }
    }
}
