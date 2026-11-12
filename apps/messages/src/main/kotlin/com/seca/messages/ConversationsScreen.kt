package com.seca.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seca.core.design.SecaAppIdentity
import com.seca.core.design.SecaIcons
import com.seca.core.design.component.SecaEmptyState
import com.seca.core.design.component.SecaGroupItem
import com.seca.core.design.component.SecaSearchField
import com.seca.core.design.component.SecaSectionLabel
import com.seca.core.design.component.SecaSwipeAway
import com.seca.core.design.component.SecaSettingRow
import com.seca.core.design.component.SecaSuiteBar
import com.seca.core.design.component.SecaTopBar
import com.seca.messages.sms.Conversation
import com.seca.messages.sms.Message
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import com.seca.core.design.component.SecaChoiceAction
import com.seca.core.design.component.SecaChoiceBar

/** How many characters of a message to keep before the searched words in a result. */
private const val EXCERPT_LEAD = 24

/** The conversations filed as advertising. */
data object SpamRoute : MessagesScreen

@Composable
internal fun ConversationsScreen(
    ui: MessagesUi,
    viewModel: MessagesViewModel,
    listState: LazyListState,
    isDefaultApp: Boolean,
    onBecomeDefault: () -> Unit,
) {
    val context = LocalContext.current
    var bannerDismissed by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Conversation?>(null) }
    // Chosen conversations, by the thread they are. Held here rather than in the view model:
    // it is a state of the screen, and leaving the screen is the end of it.
    var chosen by remember { mutableStateOf(emptySet<Long>()) }
    var deletingChosen by remember { mutableStateOf(false) }
    val choosing = chosen.isNotEmpty()
    // Conversations still on the list, so one that has just gone does not stay chosen.
    LaunchedEffect(ui.conversations) {
        if (choosing) chosen = chosen intersect ui.conversations.map { it.threadId }.toSet()
    }
    BackHandler(enabled = choosing) { chosen = emptySet() }
    // The button carries its label at the top of the list and shrinks to an icon once scrolled.
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            if (choosing) {
                // Worked out once rather than twice on every frame, and only when the list it
                // is drawn from has actually changed.
                val shown = remember(ui.conversations, ui.archived, ui.spam) { shownThreads(ui) }
                ChoiceBar(
                    ui = ui,
                    viewModel = viewModel,
                    chosen = chosen,
                    // Archiving and filing take the rows off the list. Keeping them chosen
                    // afterwards leaves a count above a list with nothing ticked in it.
                    onDone = { chosen = emptySet() },
                    onClear = { chosen = emptySet() },
                    // Everything the list is showing, which is not always everything there is:
                    // a search narrows it, and so do the archive and the advertising.
                    onChooseAll = { chosen = shown },
                    allChosen = chosen == shown,
                    // A search has narrowed the list to something this function cannot see,
                    // so "all" could not be told the truth and is not offered.
                    canChooseAll = ui.query.isBlank(),
                    onDelete = { deletingChosen = true },
                )
                return@Scaffold
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .padding(top = 8.dp, bottom = 8.dp),
            ) {
                SecaSearchField(
                    query = ui.query,
                    onQueryChange = viewModel::setQuery,
                    placeholder = stringResource(R.string.search_messages),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    IconButton(onClick = { viewModel.open(MessagesScreen.Settings) }) {
                        Icon(SecaIcons.Settings, contentDescription = stringResource(R.string.settings))
                    }
                }
            }
        },
        bottomBar = { SecaSuiteBar(current = SecaAppIdentity.Messages, onSelect = { openSibling(context, it) }) },
        floatingActionButton = {
            if (choosing) return@Scaffold
            ExtendedFloatingActionButton(
                text = { Text(stringResource(R.string.new_message)) },
                icon = { Icon(SecaIcons.Edit, contentDescription = if (fabExpanded) null else stringResource(R.string.new_message)) },
                onClick = { viewModel.open(MessagesScreen.NewMessage) },
                expanded = fabExpanded,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (ui.loaded) {
                ConversationList(
                    ui = ui,
                    viewModel = viewModel,
                    listState = listState,
                    showBanner = !isDefaultApp && !bannerDismissed && !choosing,
                    onBecomeDefault = onBecomeDefault,
                    onDismissBanner = { bannerDismissed = true },
                    onDelete = { deleting = it },
                    chosen = chosen,
                    onChoose = { threadId ->
                        chosen = if (threadId in chosen) chosen - threadId else chosen + threadId
                    },
                )
            }
        }
    }

    if (deletingChosen) {
        val going = ui.conversations.filter { it.threadId in chosen }
        DeleteChosenDialog(
            count = going.size,
            onDismiss = { deletingChosen = false },
            onConfirm = {
                going.forEach { viewModel.deleteConversation(it.threadId, it.address) }
                chosen = emptySet()
                deletingChosen = false
            },
        )
    }

    deleting?.let { conversation ->
        DeleteConversationDialog(
            name = ui.nameOf(conversation.address),
            onDismiss = { deleting = null },
            onConfirm = {
                viewModel.deleteConversation(conversation.threadId, conversation.address)
                deleting = null
            },
        )
    }
}

/**
 * The conversations the list is showing, which is what "all" can honestly mean.
 *
 * Not every conversation on the phone: the archive and the advertising are put away on purpose,
 * and a search has narrowed it to what was asked for. Choosing all of something the owner
 * cannot see would be the one mistake this button must not make.
 */
private fun shownThreads(ui: MessagesUi): Set<Long> = ui.conversations
    .filter { it.threadId !in ui.archived && it.threadId !in ui.spam }
    .map { it.threadId }
    .toSet()

/**
 * The bar that stands in for the search field while conversations are chosen.
 *
 * It carries the count and the things that make sense done to several at once. A toggle acts
 * on the whole lot the way the lot leans: if any of them is not pinned, all of them are
 * pinned, and only once every one is pinned does the button unpin them. Anything that is about
 * one person rather than several — their card, calling them — stays where it was, behind the
 * long press, and only while a single conversation is chosen.
 */
@Composable
private fun ChoiceBar(
    ui: MessagesUi,
    viewModel: MessagesViewModel,
    chosen: Set<Long>,
    onDone: () -> Unit,
    onClear: () -> Unit,
    onChooseAll: () -> Unit,
    allChosen: Boolean,
    canChooseAll: Boolean,
    onDelete: () -> Unit,
) {
    val picked = ui.conversations.filter { it.threadId in chosen }
    val allPinned = picked.isNotEmpty() && picked.all { it.threadId in ui.pinned }
    val allArchived = picked.isNotEmpty() && picked.all { it.threadId in ui.archived }
    val allSpam = picked.isNotEmpty() && picked.all { it.threadId in ui.spam }
    // Marking read what is already read writes to the store once per conversation and changes
    // nothing anybody can see. An action that cannot do anything is not offered.
    val anyUnread = picked.any { it.unread > 0 }
    // Pinning is singling something out. Pin everything and the pinned list is the list, the
    // dates that grouped it are gone, and nothing has been singled out from anything — which
    // is why the button disappears once the whole list is chosen rather than quietly doing it.
    val pinnable = !allChosen || allPinned
    SecaChoiceBar(
        count = chosen.size,
        onClear = onClear,
        // "Delete every message" was being asked for by e-mail, marked urgent, because there
        // was no way to do it short of opening each conversation in turn. A search has
        // narrowed the list to something this bar cannot see, so there "all" is not offered.
        onChooseAll = onChooseAll.takeIf { canChooseAll },
        allChosen = allChosen,
        // Putting away and deleting come first and stay put, whatever else is on the list:
        // buttons that move as the chosen set changes are buttons pressed by mistake.
        actions = buildList {
            add(
                SecaChoiceAction(
                    icon = if (allArchived) SecaIcons.Unarchive else SecaIcons.Archive,
                    label = stringResource(if (allArchived) R.string.unarchive else R.string.archive),
                ) {
                    picked.forEach { viewModel.setArchived(it.threadId, !allArchived) }
                    onDone()
                },
            )
            add(SecaChoiceAction(SecaIcons.Delete, stringResource(R.string.delete), onDelete))
            if (pinnable) {
                add(
                    SecaChoiceAction(
                        icon = SecaIcons.PushPin,
                        label = stringResource(if (allPinned) R.string.unpin else R.string.pin),
                    ) {
                        picked.forEach { viewModel.setPinned(it.threadId, !allPinned) }
                    },
                )
            }
            add(
                SecaChoiceAction(
                    icon = SecaIcons.Block,
                    label = stringResource(if (allSpam) R.string.not_spam else R.string.mark_spam),
                ) {
                    picked.forEach { viewModel.setSpam(it.threadId, !allSpam) }
                    onDone()
                },
            )
            if (anyUnread) {
                add(SecaChoiceAction(SecaIcons.Check, stringResource(R.string.mark_read)) {
                    picked.forEach { viewModel.markRead(it.threadId) }
                })
            }
        },
    )
}

/**
 * Pinned conversations first, then the others by period, then the ways into
 * the archive and the advertising. While searching: the conversations that
 * match, then the messages whose text matches, whatever conversation they are in.
 */
@Composable
private fun ConversationList(
    ui: MessagesUi,
    viewModel: MessagesViewModel,
    listState: LazyListState,
    showBanner: Boolean,
    onBecomeDefault: () -> Unit,
    onDismissBanner: () -> Unit,
    onDelete: (Conversation) -> Unit,
    chosen: Set<Long>,
    onChoose: (Long) -> Unit,
) {
    val context = LocalContext.current
    val conversationsLabel = stringResource(R.string.conversations)
    val query = ui.query.trim()
    val searching = query.isNotEmpty()
    val matching = remember(ui.conversations, query, ui.index) {
        if (!searching) {
            ui.conversations
        } else {
            val wanted = searchable(query)
            ui.conversations.filter { conversation ->
                searchable(ui.nameOf(conversation.address)).contains(wanted) ||
                    searchable(conversation.snippet).contains(wanted) ||
                    (query.count(Char::isDigit) >= 2 && ui.numbers.matchesDigits(conversation.address, query))
            }
        }
    }
    val pinned = remember(matching, ui.pinned, searching) {
        if (searching) emptyList() else ui.pinned.mapNotNull { id -> matching.firstOrNull { it.threadId == id } }
    }
    val sections = remember(matching, ui.pinned, ui.archived, ui.spam, searching) {
        if (searching) {
            if (matching.isEmpty()) emptyMap() else mapOf(conversationsLabel to matching)
        } else {
            matching
                .filter { it.threadId !in ui.pinned && it.threadId !in ui.archived && it.threadId !in ui.spam }
                .groupBy { periodOf(context, it.date) }
        }
    }
    val hits = if (searching) ui.searchHits else emptyList()
    val archivedCount = if (searching) 0 else ui.conversations.count { it.threadId in ui.archived && it.threadId !in ui.spam }
    val spamCount = if (searching) 0 else ui.conversations.count { it.threadId in ui.spam }
    val nothing = pinned.isEmpty() && sections.isEmpty() && hits.isEmpty() && archivedCount == 0 && spamCount == 0

    val archiveLabel = stringResource(R.string.archive)
    val row: @Composable (Conversation) -> Unit = { conversation ->
        // Swiping a conversation aside archives it (#51). Not deletes: a swipe is the gesture
        // people make by accident, and the archive is a tap away while a deleted conversation
        // is gone from Android's shared store for good. While rows are being chosen the swipe
        // is off, because the finger is already doing something else.
        SecaSwipeAway(
            onAway = { viewModel.setArchived(conversation.threadId, true) },
            icon = SecaIcons.Archive,
            label = archiveLabel,
        ) {
            ConversationRow(
                conversation = conversation,
                ui = ui,
                pinned = conversation.threadId in ui.pinned,
                // While some are chosen, touching a row chooses it too: nobody expects a list
                // half in one mode and half in another.
                chosen = conversation.threadId in chosen,
                choosing = chosen.isNotEmpty(),
                onChoose = { onChoose(conversation.threadId) },
                onOpen = { viewModel.openConversation(conversation.address) },
            )
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        // Room at the bottom so the last conversation clears the floating button.
        contentPadding = PaddingValues(bottom = 112.dp),
    ) {
        if (showBanner) {
            item(key = "banner", contentType = "banner") { DefaultAppBanner(onBecomeDefault, onDismissBanner) }
        }
        if (nothing) {
            item(key = "empty", contentType = "empty") {
                SecaEmptyState(
                    icon = if (searching) SecaIcons.Search else SecaIcons.Messages,
                    title = stringResource(if (searching) R.string.no_results else R.string.no_conversations),
                    description = if (searching) {
                        stringResource(R.string.nothing_matches, query)
                    } else {
                        stringResource(R.string.no_conversations_hint)
                    },
                    modifier = Modifier.fillParentMaxHeight(0.6f),
                )
            }
        }
        if (pinned.isNotEmpty()) {
            item(key = "pinned-label", contentType = "label") { SecaSectionLabel(stringResource(R.string.pinned)) }
            itemsIndexed(pinned, key = { _, c -> "pinned-${c.threadId}" }, contentType = { _, _ -> "conversation" }) { index, c ->
                SecaGroupItem(index = index, count = pinned.size) { row(c) }
            }
        }
        sections.forEach { (title, group) ->
            item(key = "section-$title", contentType = "label") { SecaSectionLabel(title) }
            // A shared content type lets the list reuse rows it scrolled past instead of building new ones.
            itemsIndexed(group, key = { _, c -> c.threadId }, contentType = { _, _ -> "conversation" }) { index, c ->
                SecaGroupItem(index = index, count = group.size) { row(c) }
            }
        }
        if (hits.isNotEmpty()) {
            item(key = "hits-label", contentType = "label") { SecaSectionLabel(stringResource(R.string.messages)) }
            itemsIndexed(hits, key = { _, m -> "hit-${m.id}" }, contentType = { _, _ -> "hit" }) { index, message ->
                SecaGroupItem(index = index, count = hits.size) {
                    MessageHit(message, ui, query, onOpen = { viewModel.openConversation(message.address) })
                }
            }
        }
        val folders = (if (archivedCount > 0) 1 else 0) + (if (spamCount > 0) 1 else 0)
        if (archivedCount > 0) {
            item(key = "archived", contentType = "folder") {
                SecaGroupItem(
                    index = 0,
                    count = folders,
                    modifier = Modifier.padding(top = 16.dp),
                    onClick = { viewModel.open(MessagesScreen.Archived) },
                ) {
                    SecaSettingRow(
                        icon = SecaIcons.Archive,
                        title = stringResource(R.string.archived_conversations),
                        subtitle = pluralStringResource(R.plurals.conversations_count, archivedCount, archivedCount),
                    )
                }
            }
        }
        if (spamCount > 0) {
            item(key = "spam", contentType = "folder") {
                SecaGroupItem(
                    index = folders - 1,
                    count = folders,
                    modifier = if (folders == 1) Modifier.padding(top = 16.dp) else Modifier,
                    onClick = { viewModel.open(SpamRoute) },
                ) {
                    SecaSettingRow(
                        icon = SecaIcons.Block,
                        title = stringResource(R.string.spam),
                        subtitle = pluralStringResource(R.plurals.spam_count, spamCount, spamCount),
                    )
                }
            }
        }
    }
}

/** The archive: conversations put away, which come back by themselves when a new message arrives. */
@Composable
internal fun ArchivedScreen(ui: MessagesUi, viewModel: MessagesViewModel) = FolderScreen(
    ui = ui,
    viewModel = viewModel,
    title = stringResource(R.string.archived),
    ids = ui.archived - ui.spam,
    icon = SecaIcons.Archive,
    emptyTitle = stringResource(R.string.no_archived),
    emptyDescription = stringResource(R.string.no_archived_hint),
    takeOut = SecaIcons.Unarchive to stringResource(R.string.unarchive),
    onTakeOut = { viewModel.setArchived(it.threadId, false) },
)

/** Advertising, kept out of the way and silent until the owner takes a conversation back out. */
@Composable
internal fun SpamScreen(ui: MessagesUi, viewModel: MessagesViewModel) = FolderScreen(
    ui = ui,
    viewModel = viewModel,
    title = stringResource(R.string.spam),
    ids = ui.spam,
    icon = SecaIcons.Block,
    emptyTitle = stringResource(R.string.no_spam),
    emptyDescription = stringResource(R.string.no_spam_hint),
    takeOut = SecaIcons.Check to stringResource(R.string.not_spam),
    onTakeOut = { viewModel.setSpam(it.threadId, false) },
)

/**
 * The archive and the advertising: one list, one way back out.
 *
 * [takeOut] is the icon and the words for taking a conversation out of this folder, which is
 * the whole point of being able to open it. Without it the folder was a one-way door, and
 * archiving a conversation lost it as surely as deleting it.
 */
@Composable
private fun FolderScreen(
    ui: MessagesUi,
    viewModel: MessagesViewModel,
    title: String,
    ids: Set<Long>,
    icon: ImageVector,
    emptyTitle: String,
    emptyDescription: String,
    takeOut: Pair<ImageVector, String>,
    onTakeOut: (Conversation) -> Unit,
) {
    val shown = remember(ui.conversations, ids) { ui.conversations.filter { it.threadId in ids } }
    var deleting by remember { mutableStateOf<Conversation?>(null) }
    var deletingChosen by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf(emptySet<Long>()) }
    // Taking a conversation out of the folder takes its row off this list, so a tick kept for
    // a row that has gone would be a count above nothing.
    val picked = shown.filter { it.threadId in chosen }
    val choosing = picked.isNotEmpty()
    LaunchedEffect(shown) { chosen = chosen intersect shown.map { it.threadId }.toSet() }
    BackHandler(enabled = choosing) { chosen = emptySet() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            if (choosing) {
                val all = shown.map { it.threadId }.toSet()
                SecaChoiceBar(
                    count = picked.size,
                    onClear = { chosen = emptySet() },
                    onChooseAll = { chosen = all },
                    allChosen = chosen == all,
                    actions = listOf(
                        SecaChoiceAction(takeOut.first, takeOut.second) {
                            picked.forEach(onTakeOut)
                            chosen = emptySet()
                        },
                        SecaChoiceAction(SecaIcons.Delete, stringResource(R.string.delete)) {
                            deletingChosen = true
                        },
                    ),
                )
            } else {
                SecaTopBar(title = title, onBack = { viewModel.back() })
            }
        },
    ) { padding ->
        if (shown.isEmpty()) {
            SecaEmptyState(
                icon = icon,
                title = emptyTitle,
                description = emptyDescription,
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
            ) {
                itemsIndexed(shown, key = { _, c -> c.threadId }) { index, conversation ->
                    SecaGroupItem(index = index, count = shown.size) {
                        ConversationRow(
                            conversation = conversation,
                            ui = ui,
                            // Pinning puts a conversation at the top of the main list, which
                            // is the opposite of being in a folder: nothing here is pinned.
                            pinned = false,
                            chosen = conversation.threadId in chosen,
                            choosing = choosing,
                            onChoose = {
                                chosen = if (conversation.threadId in chosen) {
                                    chosen - conversation.threadId
                                } else {
                                    chosen + conversation.threadId
                                }
                            },
                            onOpen = { viewModel.openConversation(conversation.address) },
                        )
                    }
                }
            }
        }
    }

    if (deletingChosen) {
        DeleteChosenDialog(
            count = picked.size,
            onDismiss = { deletingChosen = false },
            onConfirm = {
                picked.forEach { viewModel.deleteConversation(it.threadId, it.address) }
                chosen = emptySet()
                deletingChosen = false
            },
        )
    }

    deleting?.let { conversation ->
        DeleteConversationDialog(
            name = ui.nameOf(conversation.address),
            onDismiss = { deleting = null },
            onConfirm = {
                viewModel.deleteConversation(conversation.threadId, conversation.address)
                deleting = null
            },
        )
    }
}

/** Deleting a chosen set, which cannot be undone and so is asked about once, for all of them. */
@Composable
private fun DeleteChosenDialog(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(SecaIcons.Delete, contentDescription = null) },
        title = { Text(pluralStringResource(R.plurals.delete_conversations, count, count)) },
        text = { Text(stringResource(R.string.delete_conversations_text)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun DeleteConversationDialog(name: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(SecaIcons.Delete, contentDescription = null) },
        title = { Text(stringResource(R.string.delete_conversation_title)) },
        text = { Text(stringResource(R.string.delete_conversation_text, name)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Who, the latest message, when; unread conversations in bold with their count. */
@Composable
private fun ConversationRow(
    conversation: Conversation,
    ui: MessagesUi,
    pinned: Boolean,
    chosen: Boolean,
    choosing: Boolean,
    onChoose: () -> Unit,
    onOpen: () -> Unit,
) {
    val context = LocalContext.current
    val contact = ui.contactOf(conversation.address)
    val unread = conversation.unread > 0
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(if (chosen) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                .combinedClickable(
                    onClick = { if (choosing) onChoose() else onOpen() },
                    onLongClickLabel = stringResource(R.string.choose),
                    // Holding a row down chooses it, which is where every hand reaches for
                    // this, and it says the same thing whether or not rows are chosen
                    // already. Everything a single conversation can be put through is on the
                    // bar above once it is chosen, or inside the conversation itself.
                    onLongClick = onChoose,
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // A lock on the photo: this conversation travels through Seca Link, not as plain SMS.
            val peer = rememberLinkPeer(conversation.address)
            if (chosen) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                ) {
                    Icon(
                        imageVector = SecaIcons.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            } else {
                PersonAvatarWithLink(contact, ui, size = 48.dp, linked = ui.link.enabled && peer?.active == true)
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = ui.nameOf(conversation.address),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (unread) FontWeight.Bold else null,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (ui.scheduledFor(conversation.address).isNotEmpty()) {
                        Icon(
                            SecaIcons.Schedule,
                            contentDescription = stringResource(R.string.scheduled_message),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(14.dp),
                        )
                    }
                    if (pinned) {
                        Icon(
                            SecaIcons.PushPin,
                            contentDescription = stringResource(R.string.pinned_one),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(14.dp),
                        )
                    }
                    Text(
                        text = shortDateOf(context, conversation.date),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (conversation.outgoing) stringResource(R.string.you_prefix, conversation.snippet) else conversation.snippet,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (unread) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        ) {
                            Text(
                                text = if (conversation.unread > 99) "99+" else conversation.unread.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A message whose text matches the search, shown from just before the words found. */
@Composable
private fun MessageHit(message: Message, ui: MessagesUi, query: String, onOpen: () -> Unit) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        PersonAvatar(ui.contactOf(message.address), ui, size = 40.dp)
        Column(
            Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = ui.nameOf(message.address),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = shortDateOf(context, message.date),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                text = if (message.outgoing) stringResource(R.string.you_prefix, excerptOf(message.body, query)) else excerptOf(message.body, query),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The message from a little before the searched words, so they show even in a long text. */
private fun excerptOf(body: String, query: String): String {
    val index = body.indexOf(query, ignoreCase = true)
    return if (index <= EXCERPT_LEAD) body else "…" + body.substring(index - EXCERPT_LEAD)
}

@Composable
internal fun MenuEntry(label: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

/** Offers to make Seca the SMS app, which alone can receive and send. */
@Composable
private fun DefaultAppBanner(onActivate: () -> Unit, onDismiss: () -> Unit) {
    SecaGroupItem(index = 0, count = 1, modifier = Modifier.padding(top = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(SecaIcons.Messages, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = stringResource(R.string.default_banner_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Text(
                text = stringResource(R.string.default_banner_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onActivate) { Text(stringResource(R.string.turn_on)) }
                TextButton(onClick = onDismiss, modifier = Modifier.padding(start = 8.dp)) { Text(stringResource(R.string.later)) }
            }
        }
    }
}
