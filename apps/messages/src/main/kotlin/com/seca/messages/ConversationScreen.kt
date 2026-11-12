package com.seca.messages

import android.Manifest
import android.content.ContentResolver
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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
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
import com.seca.messages.sms.SendingSim
import com.seca.core.design.SecaIcons
import com.seca.core.design.secaToneColors
import com.seca.core.link.handshake.Handshake
import com.seca.core.model.Profile
import com.seca.messages.link.LinkTimers
import com.seca.messages.link.LinkTyping
import com.seca.messages.mms.CardFace
import com.seca.messages.mms.SharedPart
import com.seca.messages.mms.VCardPeek
import com.seca.messages.sms.LinkSms
import com.seca.messages.sms.ConversationNotice
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
import androidx.core.net.toUri
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged

/** Two messages closer than this, from the same side, read as one block. */
private const val JOIN_MILLIS = 2 * 60 * 1000L

/** How many messages up the list still counts as reading the latest, which a new message then joins. */
private const val NEAR_BOTTOM_ITEMS = 2

/** How long "typing…" stays after the contact's last typing notice. */
private const val TYPING_SHOWN_MILLIS = 6_000L

/** A photo in a bubble is decoded no larger than this; the viewer takes a larger one. */
private const val PREVIEW_EDGE_PX = 900
private const val VIEWER_EDGE_PX = 2400

/**
 * How many pictures one message may carry.
 *
 * The room is shared out between them, so past a handful each one is smaller than it is worth
 * sending. Four still look like photographs.
 */
private const val MAX_ATTACHMENTS = 4

/** As far in as a photo goes, and where a double tap lands. */
private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

/** What a content address begins with, as a picture message's photo is named. */
private const val CONTENT_SCHEME = "content:"

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
    // Opening the conversation, and every message arriving while it is on screen, counts as read.
    // Only while it really is on screen: a conversation left open behind another app must not take
    // away the notification of what just arrived.
    LifecycleResumeEffect(screen.threadId, messages.size) {
        viewModel.markRead(screen.threadId, screen.address)
        onPauseOrDispose { }
    }
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
    val linked = ui.link.enabled && peer?.active == true
    val canInvite = ui.link.enabled && peer?.active != true && remember(screen.address) { LinkSms.keyOf(context, screen.address) != null }
    // A contact Seca Link has not reached yet is offered it on opening, discreetly: a data SMS a phone
    // without Seca never shows, at most once a day. Two phones that both have Seca Link need nothing else.
    val known = remember(ui.contacts, messages, screen.address) {
        ui.contactOf(screen.address) != null || messages.any { it.outgoing }
    }
    LaunchedEffect(screen.address, canInvite, known) {
        if (canInvite && known) viewModel.offerLink(screen.address)
    }
    // Opening the conversation is also the moment to see whether the contact still has Seca Link.
    LaunchedEffect(screen.address, linked) {
        if (linked) viewModel.checkLink(screen.address)
    }
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
    // Attaching without Seca Link: a picture, a video or a file, sent as a multimedia message
    // through the carrier, so it reaches a phone that has never heard of Seca.
    // Several at a time: a message carries as many as fit, and picking one photo out of a
    // holiday was the one thing people kept going back to another app to do.
    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_ATTACHMENTS),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.sendAttachments(screen.address, uris, text)
            text = ""
        }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.sendAttachment(screen.address, uri, text)
            text = ""
        }
    }
    // The system's own contact picker: it hands back one row, and that is all that is read.
    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) {
            viewModel.sendContact(screen.address, uri, text)
            text = ""
        }
    }

    // Voice messages: the microphone is asked for the first time one is recorded.
    val recorder = remember { VoiceRecorder(context) }
    val micUnavailable = stringResource(R.string.mic_unavailable)
    val photoSavedText = stringResource(R.string.photo_saved)
    val soundSavedText = stringResource(R.string.sound_saved)
    val saveFailedText = stringResource(R.string.save_failed)
    var recording by remember { mutableStateOf(false) }
    var recordingMillis by remember { mutableLongStateOf(0L) }
    // Over the link where there is one, by carrier where there is not: the microphone is there
    // either way, and only the format and the length allowed change underneath.
    val finishRecording: () -> Unit = {
        recording = false
        recorder.stop()?.let { recorded ->
            if (linked) {
                viewModel.sendVoice(screen.address, recorded.absolutePath)
            } else {
                viewModel.sendVoiceByCarrier(screen.address, recorded.absolutePath)
            }
        }
    }
    LaunchedEffect(recording, linked) {
        val longest = VoiceRecorder.maxMillis(carrier = !linked)
        while (recording) {
            recordingMillis = recorder.elapsedMillis
            if (recordingMillis >= longest) finishRecording()
            delay(TICK_MILLIS)
        }
    }
    DisposableEffect(Unit) { onDispose { recorder.cancel() } }
    val startRecording = {
        if (recorder.start(carrier = !linked)) {
            recordingMillis = 0L
            recording = true
        } else {
            Toast.makeText(context, micUnavailable, Toast.LENGTH_SHORT).show()
        }
    }
    // Asked once per conversation, off the main thread: the SIMs do not change while it is
    // open, and the screen draws without them until they arrive. Held here rather than in the
    // composer, because the messages name their card too.
    val sims by produceState(initialValue = emptyList<SendingSim>()) { value = viewModel.sims() }
    val askMicrophone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Granted mid-press, so there is nothing being held any more: the next hold records.
        if (!granted) Toast.makeText(context, micUnavailable, Toast.LENGTH_SHORT).show()
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
                        linked = peer?.active == true,
                        verified = peer?.verified == true,
                        typing = typing,
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
                    sims = sims,
                    onSendFrom = { subscriptionId ->
                        viewModel.sendFrom(screen.address, text, subscriptionId)
                        text = ""
                        replyingTo = null
                    },
                    // Encrypted where the conversation is, by carrier where it is not: the
                    // button is there either way, which is the whole point of it.
                    attach = if (linked) {
                        Attaching.Encrypted {
                            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    } else {
                        Attaching.ByCarrier(
                            media = {
                                pickMedia.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                                )
                            },
                            file = { pickFile.launch(arrayOf("*/*")) },
                            contact = { pickContact.launch(null) },
                            // Nothing written is nothing to send later, so the row says so by
                            // being there and greyed rather than by coming and going.
                            schedule = { scheduling = true }.takeIf { text.isNotBlank() },
                        )
                    },
                    replyingTo = replyingTo,
                    replyName = name,
                    onCancelReply = { replyingTo = null },
                    recording = recording,
                    recordingMillis = recordingMillis,
                    onStartVoice = {
                        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            startRecording()
                        } else {
                            askMicrophone.launch(Manifest.permission.RECORD_AUDIO)
                        }
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
                    val messageLabel = stringResource(R.string.message)
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
                            onCopy = { copyText(context, messageLabel, message.body) },
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
                            onSaveAudio = { path ->
                                scope.launch {
                                    val saved = saveToMusic(context, path)
                                    Toast.makeText(
                                        context,
                                        if (saved) soundSavedText else saveFailedText,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                            // The slot as the tray prints it, and the carrier's own name where
                            // it says anything the slot does not. The slot already counts from
                            // one; adding one again made a phone with two SIMs report that a
                            // text had gone out on SIM 3 (#53).
                            simName = { id ->
                                sims.firstOrNull { it.subscriptionId == id }?.let { sim ->
                                    val slot = "SIM ${sim.slot}"
                                    if (sim.label.equals("SIM", ignoreCase = true)) {
                                        slot
                                    } else {
                                        "${sim.label} · $slot"
                                    }
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

    viewing?.let { path ->
        PhotoViewer(
            path = path,
            onSave = {
                scope.launch {
                    val saved = saveToGallery(context, path)
                    Toast.makeText(
                        context,
                        if (saved) photoSavedText else saveFailedText,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            onDismiss = { viewing = null },
        )
    }

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

/** A message as a quote or a notice reads it: whatever it carries, named for what it is. */
@Composable
private fun previewOf(message: Message): String = when {
    message.audio != null -> stringResource(R.string.preview_voice)
    // Something attached, with words: the words. On its own, it says what it is.
    message.images.isNotEmpty() -> message.body.ifBlank { stringResource(R.string.preview_photo) }
    message.attachment != null -> message.body.ifBlank { stringResource(carried(message.attachment)) }
    else -> message.body.ifEmpty { stringResource(R.string.preview_photo) }
}

/**
 * What to call a part that is neither words, a picture nor a sound.
 *
 * Everything used to be called a photograph, which was true until it was not: a voice message
 * and a contact card both arrived announcing themselves as pictures.
 */
private fun carried(contentType: String): Int = when {
    contentType.startsWith("video/") -> R.string.preview_video
    contentType.endsWith("vcard") -> R.string.preview_contact
    else -> R.string.preview_file
}

/** Back, who the conversation is with and their profile, a call button and the rest in a menu. */
@Composable
private fun ConversationTopBar(
    address: String,
    ui: MessagesUi,
    linked: Boolean,
    verified: Boolean,
    typing: Boolean,
    timerSeconds: Int,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onSafetyNumber: () -> Unit,
    onInvite: (() -> Unit)?,
    onTimer: (() -> Unit)?,
) {
    val context = LocalContext.current
    val numberLabel = stringResource(R.string.number)
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
        // The face and the name open the person, which is where everybody reaches for it. It
        // stays in the menu as well: that is the one with a word on it, for anyone who cannot
        // see that a picture is a button.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(28.dp))
                .clickable(
                    onClickLabel = stringResource(
                        if (contact != null) R.string.view_contact else R.string.add_to_contacts,
                    ),
                ) {
                    if (contact != null) openContact(context, contact.id, contact.lookupKey) else addContact(context, address)
                }
                .padding(end = 8.dp),
        ) {
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
                    linked -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (timerSeconds > 0) SecaIcons.Timer else SecaIcons.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = when {
                                timerSeconds > 0 -> stringResource(R.string.encrypted_disappearing, LinkTimers.label(context, timerSeconds))
                                verified -> stringResource(R.string.encrypted_verified)
                                else -> stringResource(R.string.encrypted_by_link)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
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
        }
        // Nothing to ring on a sender written in letters, and a button that cannot work is
        // worse than no button: it reads as the app failing rather than as the thing it is.
        if (dialable(address)) {
            IconButton(onClick = { call(context, address) }) {
                Icon(SecaIcons.Phone, contentDescription = stringResource(R.string.call))
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) { Icon(SecaIcons.MoreVert, contentDescription = stringResource(R.string.more_options)) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (onInvite != null) {
                    MenuEntry(stringResource(R.string.encrypt_with_link), SecaIcons.Lock) {
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
                // "Copy number" on a sender called McDonalds copies a word and calls it a
                // number. There is nothing to do with it afterwards.
                if (dialable(address)) {
                    MenuEntry(stringResource(R.string.copy_number), SecaIcons.ContentCopy) {
                        menuOpen = false
                        copyText(context, numberLabel, address)
                    }
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
    onSaveAudio: (String) -> Unit,
    /** "SIM 1" for the card this one came in or went out on; null where there is only one. */
    simName: (Int) -> String?,
    onReply: () -> Unit,
    onReact: (String) -> Unit,
) {
    // Not a message: what happened to the conversation itself.
    message.notice?.let { notice ->
        Notice(
            stringResource(
                when (notice) {
                    ConversationNotice.LinkStarted -> R.string.link_conversation_started
                    ConversationNotice.LinkStopped -> R.string.link_conversation_stopped
                },
            ),
        )
        return
    }
    val handshake = remember(message.body) { if (message.encrypted) null else Handshake.fromText(message.body) }
    if (handshake != null) {
        HandshakeNotice(handshake, mine = message.outgoing)
        return
    }
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val mine = message.outgoing
    val failed = message.status == MessageStatus.Failed
    // Why, where the carrier said something worth repeating.
    val why = message.failure.takeIf { failed && it != 0 }?.let(::mmsFailureReason)
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
        // The carrier's own reason where it gave one, because half of them are something to
        // put right rather than something to retry.
        why != null -> stringResource(R.string.send_failed_because, stringResource(why))
        failed -> stringResource(R.string.send_failed_retry)
        message.status == MessageStatus.Sending -> stringResource(R.string.sending)
        isLatestOutgoing && message.status == MessageStatus.Read -> stringResource(R.string.read_at, time)
        isLatestOutgoing && message.status == MessageStatus.Delivered -> stringResource(R.string.delivered_at, time)
        isLatestOutgoing -> stringResource(R.string.sent_at, time)
        !joinedBelow -> time
        else -> null
    }
    // Which card carried it, where there is a choice. Said next to the time rather than on the
    // bubble: it answers "which number was this?", which is a question asked after the fact.
    val sim = simName(message.subscriptionId)
    val images = message.images
    // Tapping a bubble opens a picture, and with several there is no single one to open, so
    // the tap belongs to each picture rather than to the bubble.
    val image = images.firstOrNull()
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
            val carried = message.attachment
            val carriedAt = message.attachmentAt
            when {
                images.isNotEmpty() ->
                    PhotoBubble(images, shape, container, content, message.body, onOpen = onOpenPhoto, modifier = touch)
                audio != null -> VoiceBubble(audio, shape, container, content, modifier = touch)
                carried != null && carriedAt != null ->
                    AttachmentBubble(carried, carriedAt, message.body, shape, container, content, modifier = touch)
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
                        text = when {
                            message.body.isNotEmpty() -> linkedMessage(message.body)
                            message.attachment != null -> AnnotatedString(stringResource(carried(message.attachment)))
                            else -> AnnotatedString(stringResource(R.string.preview_photo))
                        },
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
                // Keeping and copying are not alternatives. A picture with words under it had
                // only "Download", so the words could not be copied at all; a message with no
                // words at all offered "Copy", which put nothing on the clipboard.
                if (image != null) {
                    MenuEntry(stringResource(R.string.save_to_gallery), SecaIcons.Download) {
                        menuOpen = false
                        onSavePhoto(image)
                    }
                }
                if (audio != null) {
                    MenuEntry(stringResource(R.string.save_sound), SecaIcons.Download) {
                        menuOpen = false
                        onSaveAudio(audio)
                    }
                }
                if (message.body.isNotBlank()) {
                    MenuEntry(stringResource(R.string.copy), SecaIcons.ContentCopy) {
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
                    text = if (sim == null) it else stringResource(R.string.caption_on_sim, it, sim),
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

/**
 * The pictures of a message, at their own proportions within the bubble's width.
 *
 * A message may carry several. One alone keeps its shape; several are stacked, each one
 * opening on its own tap, because a bubble with four pictures in it has no single picture for
 * a tap on the bubble to mean.
 */
@Composable
private fun PhotoBubble(
    paths: List<String>,
    shape: RoundedCornerShape,
    container: Color,
    content: Color,
    caption: String,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val frame = Modifier
        .widthIn(max = 260.dp)
        .clip(shape)
        .background(container)
        .then(modifier)
    // A picture message usually carries words with the photo, and they belong to it: one bubble,
    // the photos above and what was written below, rather than the words thrown away.
    Column(frame) {
        paths.forEach { path ->
            val photo = rememberMessagePicture(path, PREVIEW_EDGE_PX)
            if (photo == null || photo.width <= 0 || photo.height <= 0) {
                Box(Modifier.size(width = 220.dp, height = 160.dp))
            } else {
                MessagePictureImage(
                    picture = photo,
                    description = stringResource(R.string.photo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .aspectRatio(
                            ratio = photo.width.toFloat() / photo.height,
                            matchHeightConstraintsFirst = photo.height > photo.width,
                        )
                        .clickable(onClickLabel = stringResource(R.string.photo)) { onOpen(path) },
                )
            }
        }
        if (caption.isNotBlank()) {
            Text(
                text = linkedMessage(caption),
                style = MaterialTheme.typography.bodyLarge,
                color = content,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

/** The same thing as [carried], without the little picture, for a bubble that draws its own. */
private fun plainly(contentType: String): Int = when {
    contentType.startsWith("video/") -> R.string.attach_media
    contentType.endsWith("vcard") -> R.string.attach_contact
    else -> R.string.attach_file
}

/**
 * Something attached that is not a picture or a sound: a contact card, or a file.
 *
 * A card is read far enough to put the person's name on the bubble, because "Contact" on its
 * own tells the owner nothing — a message saying only what kind of thing arrived is barely
 * better than one that arrived blank. Touching it hands the whole card to the contacts app,
 * which reads every field of it; touching any other file offers it to whatever can open it.
 */
@Composable
private fun AttachmentBubble(
    type: String,
    at: String,
    caption: String,
    shape: RoundedCornerShape,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val card = type.endsWith("vcard")
    val face by produceState<CardFace?>(initialValue = null, at, card) {
        if (!card) return@produceState
        value = withContext(Dispatchers.IO) {
            SharedPart.bytes(context, at)?.let { VCardPeek.read(String(it, Charsets.UTF_8)) }
        }
    }
    // A file says what it is called. "File" above a paperclip tells nobody anything.
    val filed by produceState<String?>(initialValue = null, at, card) {
        if (card) return@produceState
        value = SharedPart.nameOf(context, at)
    }
    val cannotOpen = stringResource(R.string.nothing_opens_this)
    // The bubble already draws an icon, so its words do not carry one as well.
    val fallback = stringResource(plainly(type))
    Column(
        Modifier
            .widthIn(min = 200.dp, max = 300.dp)
            .clip(shape)
            .background(container)
            .then(modifier)
            .clickable(onClickLabel = fallback) {
                scope.launch {
                    val name = when {
                        card -> face?.name?.takeIf { it.isNotBlank() }?.plus(".vcf") ?: "contact.vcf"
                        else -> filed ?: at.substringAfterLast('/')
                    }
                    if (!SharedPart.open(context, at, name, type)) {
                        Toast.makeText(context, cannotOpen, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (card) SecaIcons.PersonOutline else SecaIcons.Subject,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(32.dp),
            )
            Column(Modifier.padding(start = 12.dp)) {
                Text(
                    text = face?.name?.takeIf { it.isNotBlank() } ?: filed ?: fallback,
                    style = MaterialTheme.typography.titleSmall,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = face?.detail?.takeIf { it.isNotBlank() } ?: stringResource(R.string.open_attachment),
                    style = MaterialTheme.typography.bodyMedium,
                    color = content.copy(alpha = UNDER),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (caption.isNotBlank()) {
            Text(
                text = linkedMessage(caption),
                style = MaterialTheme.typography.bodyLarge,
                color = content,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** A voice message: play and pause, how fast, how far it has played, and how long it lasts. */
@Composable
private fun VoiceBubble(path: String, shape: RoundedCornerShape, container: Color, content: Color, modifier: Modifier = Modifier) {
    // A recording over Seca Link is a file of ours; one in a multimedia message is a row in the
    // store, and the two are opened differently.
    val context = LocalContext.current
    val stored = path.startsWith("content:")
    var player by remember(path) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(path) { mutableStateOf(false) }
    var progress by remember(path) { mutableFloatStateOf(0f) }
    // Kept across recordings rather than per bubble: somebody who listens faster listens
    // faster to the next one too, and resetting it every time is the annoying half of this.
    var speed by rememberSaveable { mutableFloatStateOf(SPEEDS.first()) }
    val duration by produceState(initialValue = 0L, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                MediaMetadataRetriever().use { retriever ->
                    if (stored) retriever.setDataSource(context, path.toUri()) else retriever.setDataSource(path)
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
    // Changed mid-sentence, it takes effect mid-sentence. Setting it on a player that is not
    // running would start it, so a paused one is left alone and told when it next starts.
    LaunchedEffect(speed, playing) {
        val current = player
        if (current != null && playing) {
            runCatching { current.playbackParams = current.playbackParams.setSpeed(speed) }
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
                        if (stored) setDataSource(context, path.toUri()) else setDataSource(path)
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
                        runCatching { current.playbackParams = current.playbackParams.setSpeed(speed) }
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
        Text(
            text = speedText(speed),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = content,
            modifier = Modifier
                .padding(start = 8.dp)
                .clip(CircleShape)
                .background(content.copy(alpha = if (speed == SPEEDS.first()) 0.12f else 0.28f))
                .clickable(onClickLabel = stringResource(R.string.listen_faster)) {
                    speed = SPEEDS[(SPEEDS.indexOf(speed) + 1) % SPEEDS.size]
                }
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** "1×", "1,5×", "2×" — the comma because a speed is read, not computed. */
@Composable
private fun speedText(speed: Float): String {
    val shown = if (speed % 1f == 0f) speed.toInt().toString() else {
        stringResource(R.string.decimal, speed.toInt(), ((speed % 1f) * 10).roundToInt())
    }
    return "$shown×"
}

/** What a voice message can be listened to at. Back round to the start after the fastest. */
private val SPEEDS = listOf(1f, 1.5f, 2f)

/** "0:42", "3:05". */
private fun durationText(millis: Long): String {
    val seconds = millis / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

/** Seca Link's handshake as the conversation shows it: a short centred notice. */
@Composable
private fun HandshakeNotice(handshake: Handshake, mine: Boolean) {
    Notice(
        stringResource(
            when {
                handshake.type == Handshake.Type.Accept -> R.string.conversation_encrypted
                mine -> R.string.invitation_sent
                else -> R.string.invitation_received
            },
        ),
    )
}

/** A short centred notice in the thread: what happened to the conversation, never a message. */
@Composable
private fun Notice(label: String) {
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

/**
 * A photo opened on its own, on black, the whole screen.
 *
 * Pinch to zoom, drag to move about, double tap to go in and out again. A single tap closes
 * the photo while it is whole and puts it back as it was once it has been zoomed, because a
 * tap that closed the screen while somebody was reading the small print off a photo would be
 * the only gesture they ever made here by mistake.
 *
 * Saving is on this screen, which is where somebody looking at a photo is when they decide
 * they want to keep it. It is still on the bubble as well, for a photo not worth opening.
 */
@Composable
private fun PhotoViewer(path: String, onSave: () -> Unit, onDismiss: () -> Unit) {
    val photo = rememberMessagePicture(path, VIEWER_EDGE_PX)
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var frame by remember { mutableStateOf(IntSize.Zero) }
    // How far a zoomed photo can be pushed: only as much of it as is hidden past each edge.
    // Without this it can be flicked away into the black and there is no way of knowing where.
    fun held(wanted: Offset, at: Float): Offset {
        val x = (frame.width * (at - 1f) / 2f).coerceAtLeast(0f)
        val y = (frame.height * (at - 1f) / 2f).coerceAtLeast(0f)
        return Offset(wanted.x.coerceIn(-x, x), wanted.y.coerceIn(-y, y))
    }
    fun whole() {
        scale = 1f
        offset = Offset.Zero
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { frame = it },
        ) {
            photo?.let {
                MessagePictureImage(
                    picture = it,
                    description = stringResource(R.string.photo),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val next = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                                offset = held(offset + pan, next)
                                scale = next
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { if (scale > 1f) whole() else onDismiss() },
                                onDoubleTap = { if (scale > 1f) whole() else scale = DOUBLE_TAP_ZOOM },
                            )
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                        ),
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(8.dp),
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(SecaIcons.Close, contentDescription = stringResource(R.string.close), tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSave) {
                    Icon(
                        imageVector = SecaIcons.Download,
                        contentDescription = stringResource(R.string.save_to_gallery),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

/** Copies a photo into the phone's gallery, under Pictures/Seca, only when the owner asks. */
private suspend fun saveToGallery(context: Context, path: String): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        val stored = path.startsWith(CONTENT_SCHEME)
        // A picture message brings its own kind with it; a Seca Link photo is always a WebP.
        val type = (if (stored) resolver.getType(path.toUri()) else null) ?: "image/webp"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Seca-${System.currentTimeMillis()}.${type.substringAfterLast('/')}")
            put(MediaStore.Images.Media.MIME_TYPE, type)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Seca")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
        resolver.openOutputStream(uri)?.use { out ->
            val source = if (stored) resolver.openInputStream(path.toUri()) else File(path).inputStream()
            source?.use { it.copyTo(out) }
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        true
    }.getOrDefault(false)
}

/** Copies a recording into the phone's music, under Music/Seca, only when the owner asks. */
private suspend fun saveToMusic(context: Context, path: String): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        val stored = path.startsWith(CONTENT_SCHEME)
        // A message's recording brings its own kind with it; a Seca Link one is always Opus.
        val type = (if (stored) resolver.getType(path.toUri()) else null) ?: "audio/ogg"
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "Seca-${System.currentTimeMillis()}.${soundSuffix(type)}")
            put(MediaStore.Audio.Media.MIME_TYPE, type)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Seca")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
        resolver.openOutputStream(uri)?.use { out ->
            val source = if (stored) resolver.openInputStream(path.toUri()) else File(path).inputStream()
            source?.use { it.copyTo(out) }
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        true
    }.getOrDefault(false)
}

/** What to call the file, so the phone's music player knows what it is holding. */
private fun soundSuffix(type: String): String = when (val kind = type.substringAfterLast('/').lowercase()) {
    "mpeg", "mpeg3", "x-mpeg" -> "mp3"
    "mp4", "aac", "x-m4a", "mp4a-latm" -> "m4a"
    "3gpp", "3gpp2" -> "3ga"
    "x-wav", "wave", "vnd.wave" -> "wav"
    "opus" -> "ogg"
    else -> kind.takeIf { it.isNotBlank() && it.all(Char::isLetterOrDigit) } ?: "ogg"
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
    attach: Attaching?,
    replyingTo: Message?,
    replyName: String,
    onCancelReply: () -> Unit,
    recording: Boolean,
    recordingMillis: Long,
    onStartVoice: () -> Unit,
    onCancelVoice: () -> Unit,
    onSendVoice: () -> Unit,
    /** Holding Send down: which SIM to send this one from. Empty where there is no choice. */
    sims: List<SendingSim>,
    onSendFrom: (Int) -> Unit,
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
        // One row, whatever is happening in it. The microphone has to be the same button from
        // one frame to the next or the finger holding it loses its grip halfway through: a
        // gesture belongs to a composable, and a composable that is swapped out never hears
        // the finger lift. So what changes is everything to its left, and never the button.
        Row(verticalAlignment = Alignment.Bottom) {
            if (recording) {
                RecordingStrip(recordingMillis, Modifier.weight(1f))
            } else {
                if (attach != null) AttachButton(attach)
                TextField(
                    value = text,
                    onValueChange = onText,
                    placeholder = { Text(stringResource(if (encrypted) R.string.encrypted_message else R.string.message)) },
                    leadingIcon = if (encrypted) {
                        { Icon(SecaIcons.Lock, contentDescription = stringResource(R.string.encrypted_by_link), modifier = Modifier.size(18.dp)) }
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
            }
            val speaking = text.isBlank()
            // Only an ordinary SMS has a SIM to choose: an encrypted message goes over the
            // Internet, and a voice note is not a text at all.
            val chooseSim = sims.isNotEmpty() && !encrypted && !speaking && enabled
            var choosingSim by remember { mutableStateOf(false) }
            if (choosingSim) {
                SendingSimDialog(
                    sims = sims,
                    onDismiss = { choosingSim = false },
                    onPick = { sim ->
                        choosingSim = false
                        onSendFrom(sim.subscriptionId)
                    },
                )
            }
            val buttonPadding = Modifier.padding(start = 8.dp, bottom = if (parts > 1) 24.dp else 0.dp)
            if (speaking) {
                VoiceButton(
                    recording = recording,
                    enabled = enabled,
                    onStart = onStartVoice,
                    onFinish = onSendVoice,
                    onCancel = onCancelVoice,
                    modifier = buttonPadding,
                )
            } else {
                FilledIconButton(
                    onClick = onSend,
                    enabled = enabled,
                    modifier = buttonPadding
                        .size(56.dp)
                        .then(
                            if (!chooseSim) {
                                Modifier
                            } else {
                                Modifier.combinedClickable(
                                    role = Role.Button,
                                    onClick = onSend,
                                    onLongClickLabel = stringResource(R.string.send_from_other_sim),
                                    onLongClick = { choosingSim = true },
                                )
                            },
                        ),
                ) {
                    Icon(
                        SecaIcons.Send,
                        contentDescription = stringResource(if (encrypted) R.string.send_encrypted else R.string.send),
                    )
                }
            }
        }
    }
}

/**
 * Which SIM sends this one text.
 *
 * Reached by holding the send button down, and it changes nothing afterwards: the next message
 * goes from whichever SIM the phone's own settings name, as this one would have.
 */
@Composable
private fun SendingSimDialog(sims: List<SendingSim>, onDismiss: () -> Unit, onPick: (SendingSim) -> Unit) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(SecaIcons.Send, contentDescription = null) },
        title = { Text(stringResource(R.string.which_sim_once)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                sims.forEach { sim ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .background(colors.surfaceContainerHighest)
                            .clickable(onClickLabel = sim.label) { onPick(sim) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Text(
                            text = sim.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text("SIM ${sim.slot}", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** What the button to the left of the text has to offer, which is not the same in both places. */
private sealed interface Attaching {

    /** An encrypted conversation: the picture goes over the link, and there is nothing to choose. */
    class Encrypted(val pick: () -> Unit) : Attaching

    /** By carrier, so it reaches any phone at all. Scheduling is null with nothing written yet. */
    class ByCarrier(
        val media: () -> Unit,
        val file: () -> Unit,
        val contact: () -> Unit,
        val schedule: (() -> Unit)?,
    ) : Attaching
}

/** One line of the card the plus opens: an icon, a word, and the whole row to touch. */
private class AttachChoice(
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

/**
 * The button left of the text, and what it opens.
 *
 * Where there is a single thing to do it does it. Where there is a choice the plus turns a
 * quarter turn into a cross — the same stroke, so the eye follows the mark rather than reading
 * a new icon — and a card rises just over it. It wears the same colour as the text field beside
 * it and the card above it, so the three read as one piece of furniture rather than three.
 */
@Composable
private fun AttachButton(attach: Attaching) {
    var open by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val turn by animateFloatAsState(
        targetValue = if (open) QUARTER_TURN else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "attach-turn",
    )
    val container by animateColorAsState(
        targetValue = if (open) colors.secondaryContainer else colors.surfaceContainerHigh,
        label = "attach-container",
    )
    val ink by animateColorAsState(
        targetValue = if (open) colors.onSecondaryContainer else colors.onSurfaceVariant,
        label = "attach-ink",
    )
    Box {
        FilledIconButton(
            onClick = {
                when (attach) {
                    is Attaching.Encrypted -> attach.pick()
                    is Attaching.ByCarrier -> open = true
                }
            },
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = container, contentColor = ink),
            modifier = Modifier
                .padding(end = 6.dp, bottom = 2.dp)
                .size(52.dp),
        ) {
            Icon(
                imageVector = if (attach is Attaching.Encrypted) SecaIcons.Photo else SecaIcons.Add,
                contentDescription = stringResource(
                    when {
                        attach is Attaching.Encrypted -> R.string.send_encrypted_photo
                        open -> R.string.close_attach
                        else -> R.string.attach
                    },
                ),
                modifier = Modifier.rotate(turn),
            )
        }
        if (attach is Attaching.ByCarrier) {
            val shut = { open = false }
            AttachMenu(
                open = open,
                onDismiss = shut,
                choices = listOf(
                    AttachChoice(SecaIcons.Photo, stringResource(R.string.attach_media), true) {
                        shut()
                        attach.media()
                    },
                    AttachChoice(SecaIcons.Subject, stringResource(R.string.attach_file), true) {
                        shut()
                        attach.file()
                    },
                    AttachChoice(SecaIcons.PersonOutline, stringResource(R.string.attach_contact), true) {
                        shut()
                        attach.contact()
                    },
                    AttachChoice(
                        icon = SecaIcons.Schedule,
                        label = stringResource(R.string.schedule_send),
                        enabled = attach.schedule != null,
                    ) {
                        shut()
                        attach.schedule?.invoke()
                    },
                ),
            )
        }
    }
}

/**
 * The card the plus opens on, placed by hand rather than by the menu's own rules.
 *
 * A dropdown asked to flip above its anchor lands a long way off it, because it measures from
 * the top of a button that is itself as tall as the text field. Here the card's bottom edge is
 * put a hair above the plus and lined up with its left side, and it grows out of that corner,
 * so what the eye sees is the button opening rather than a panel arriving.
 */
@Composable
private fun AttachMenu(open: Boolean, onDismiss: () -> Unit, choices: List<AttachChoice>) {
    val colors = MaterialTheme.colorScheme
    val showing = remember { MutableTransitionState(false) }
    showing.targetState = open
    if (!showing.currentState && !showing.targetState) return
    val gap = with(LocalDensity.current) { MENU_GAP.roundToPx() }
    Popup(
        popupPositionProvider = remember(gap) { JustAbove(gap) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        AnimatedVisibility(
            visibleState = showing,
            enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                scaleIn(
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
                    initialScale = 0.75f,
                    transformOrigin = FROM_THE_BUTTON,
                ),
            exit = fadeOut(tween(MENU_CLOSING_MILLIS)) +
                scaleOut(tween(MENU_CLOSING_MILLIS), targetScale = 0.9f, transformOrigin = FROM_THE_BUTTON),
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = colors.surfaceContainerHigh,
                shadowElevation = 6.dp,
            ) {
                // As wide as its longest word and no wider. A popup is handed the whole window
                // to measure against, so rows told to fill their width would fill the screen.
                Column(
                    Modifier
                        .width(IntrinsicSize.Max)
                        .widthIn(min = 184.dp)
                        .padding(vertical = 8.dp),
                ) {
                    choices.forEach { choice -> AttachRow(choice) }
                }
            }
        }
    }
}

/** One row of the card: the icon, the word, and the whole width to touch. */
@Composable
private fun AttachRow(choice: AttachChoice) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = choice.enabled, onClickLabel = choice.label, onClick = choice.onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = choice.icon,
            contentDescription = null,
            tint = if (choice.enabled) colors.primary else colors.onSurfaceVariant.copy(alpha = GREYED),
        )
        Text(
            text = choice.label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (choice.enabled) colors.onSurface else colors.onSurface.copy(alpha = GREYED),
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

/** Puts a popup's bottom edge [gap] pixels above its anchor, lined up with the anchor's start. */
private class JustAbove(private val gap: Int) : PopupPositionProvider {

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = if (layoutDirection == LayoutDirection.Ltr) {
            anchorBounds.left
        } else {
            anchorBounds.right - popupContentSize.width
        }
        val y = anchorBounds.top - popupContentSize.height - gap
        return IntOffset(
            x = x.coerceIn(0, max(0, windowSize.width - popupContentSize.width)),
            y = y.coerceAtLeast(0),
        )
    }
}

/** What is going on while the microphone is held down, where the text field usually is. */
@Composable
private fun RecordingStrip(millis: Long, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(56.dp)
            .padding(start = 12.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(colors.error),
        )
        Text(
            text = durationText(millis),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            modifier = Modifier.padding(start = 12.dp),
        )
        Text(
            text = stringResource(R.string.slide_to_cancel),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 8.dp),
        )
    }
}

/**
 * The microphone, which is held down rather than pressed.
 *
 * Press and it starts; let go and it leaves; slide a thumb's width off the button and it is
 * thrown away instead. Recording on one tap and sending on a second is two decisions where
 * there is only one, and it leaves a phone quietly recording in a pocket when the second tap
 * never comes.
 *
 * A press too short to be speech is discarded by the recorder itself, so a fumbled tap costs
 * nothing and sends nothing.
 */
@Composable
private fun VoiceButton(
    recording: Boolean,
    enabled: Boolean,
    onStart: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val swell by animateFloatAsState(
        targetValue = if (recording) 1.15f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
        label = "mic-swell",
    )
    val container by animateColorAsState(
        targetValue = if (recording) colors.error else colors.primary,
        label = "mic-container",
    )
    val held by rememberUpdatedState(Triple(onStart, onFinish, onCancel))
    val label = stringResource(R.string.record_voice)
    val escape = with(LocalDensity.current) { ESCAPE.toPx() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(56.dp)
            .scale(swell)
            .clip(CircleShape)
            .background(if (enabled) container else colors.surfaceContainerHighest)
            .semantics {
                role = Role.Button
                contentDescription = label
            }
            .then(
                if (!enabled) {
                    Modifier
                } else {
                    Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            val (start, finish, cancel) = held
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            start()
                            var gone = false
                            while (true) {
                                val moved = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                gone = (moved.position - down.position).getDistance() > escape
                                if (!moved.pressed) {
                                    moved.consume()
                                    break
                                }
                            }
                            if (gone) cancel() else finish()
                        }
                    }
                },
            ),
    ) {
        Icon(
            imageVector = SecaIcons.Mic,
            contentDescription = null,
            tint = when {
                !enabled -> colors.onSurfaceVariant.copy(alpha = GREYED)
                recording -> colors.onError
                else -> colors.onPrimary
            },
        )
    }
}

/** A plus turned this far is a cross. */
private const val QUARTER_TURN = 45f

/** How far the card sits above the plus: close enough to belong to it. */
private val MENU_GAP = 6.dp

/** A thumb's width off the button, and the recording is thrown away rather than sent. */
private val ESCAPE = 72.dp

/** The card grows out of its own bottom-left corner, which is where the plus is. */
private val FROM_THE_BUTTON = TransformOrigin(0f, 1f)

private const val MENU_CLOSING_MILLIS = 90
private const val GREYED = 0.38f

/** The second line of an attachment reads under the first rather than beside it. */
private const val UNDER = 0.75f
