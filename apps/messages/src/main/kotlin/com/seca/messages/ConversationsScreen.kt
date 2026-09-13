package com.seca.messages

import androidx.compose.foundation.background
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
import com.seca.core.design.component.SecaSuiteBar
import com.seca.messages.sms.Conversation

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
    // The button carries its label at the top of the list and shrinks to an icon once scrolled.
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
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
                    placeholder = "Rechercher dans les messages",
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    IconButton(onClick = { viewModel.open(MessagesScreen.Settings) }) {
                        Icon(SecaIcons.Settings, contentDescription = "Paramètres")
                    }
                }
            }
        },
        bottomBar = { SecaSuiteBar(current = SecaAppIdentity.Messages, onSelect = { openSibling(context, it) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Nouveau message") },
                icon = { Icon(SecaIcons.Edit, contentDescription = if (fabExpanded) null else "Nouveau message") },
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
                    showBanner = !isDefaultApp && !bannerDismissed,
                    onBecomeDefault = onBecomeDefault,
                    onDismissBanner = { bannerDismissed = true },
                    onDelete = { deleting = it },
                )
            }
        }
    }

    deleting?.let { conversation ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { Icon(SecaIcons.Delete, contentDescription = null) },
            title = { Text("Supprimer la conversation ?") },
            text = { Text("Tous les messages avec ${ui.nameOf(conversation.address)} seront supprimés de ce téléphone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteConversation(conversation.threadId)
                        deleting = null
                    },
                ) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun ConversationList(
    ui: MessagesUi,
    viewModel: MessagesViewModel,
    listState: LazyListState,
    showBanner: Boolean,
    onBecomeDefault: () -> Unit,
    onDismissBanner: () -> Unit,
    onDelete: (Conversation) -> Unit,
) {
    val query = ui.query.trim()
    val shown = remember(ui.conversations, query, ui.index) {
        if (query.isEmpty()) {
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
    val sections = remember(shown) { shown.groupBy { periodOf(it.date) } }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        // Room at the bottom so the last conversation clears the floating button.
        contentPadding = PaddingValues(bottom = 112.dp),
    ) {
        if (showBanner) {
            item(key = "banner", contentType = "banner") { DefaultAppBanner(onBecomeDefault, onDismissBanner) }
        }
        if (shown.isEmpty()) {
            item(key = "empty", contentType = "empty") {
                SecaEmptyState(
                    icon = if (query.isEmpty()) SecaIcons.Messages else SecaIcons.Search,
                    title = if (query.isEmpty()) "Aucune conversation" else "Aucun résultat",
                    description = if (query.isEmpty()) {
                        "Les SMS de ce téléphone apparaîtront ici."
                    } else {
                        "Rien ne correspond à « $query »."
                    },
                    modifier = Modifier.fillParentMaxHeight(0.6f),
                )
            }
        }
        sections.forEach { (period, group) ->
            item(key = "period-$period", contentType = "label") { SecaSectionLabel(period) }
            // A shared content type lets the list reuse rows it scrolled past instead of building new ones.
            itemsIndexed(group, key = { _, c -> c.threadId }, contentType = { _, _ -> "conversation" }) { index, conversation ->
                SecaGroupItem(index = index, count = group.size) {
                    ConversationRow(
                        conversation = conversation,
                        ui = ui,
                        onOpen = { viewModel.openConversation(conversation.address) },
                        onMarkRead = { viewModel.markRead(conversation.threadId) },
                        onDelete = { onDelete(conversation) },
                    )
                }
            }
        }
    }
}

/** Who, the latest message, when; unread conversations in bold with their count. */
@Composable
private fun ConversationRow(
    conversation: Conversation,
    ui: MessagesUi,
    onOpen: () -> Unit,
    onMarkRead: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val contact = ui.contactOf(conversation.address)
    val unread = conversation.unread > 0
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onOpen, onLongClickLabel = "Plus d'actions", onLongClick = { menuOpen = true })
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            PersonAvatar(contact, ui, size = 48.dp)
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
                    Text(
                        text = shortDateOf(context, conversation.date),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = (if (conversation.outgoing) "Vous : " else "") + conversation.snippet,
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
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (unread) {
                MenuEntry("Marquer comme lu", SecaIcons.Check) {
                    menuOpen = false
                    onMarkRead()
                }
            }
            MenuEntry("Appeler", SecaIcons.Phone) {
                menuOpen = false
                call(context, conversation.address)
            }
            if (contact != null) {
                MenuEntry("Voir la fiche", SecaIcons.Contacts) {
                    menuOpen = false
                    openContact(context, contact.id, contact.lookupKey)
                }
            } else {
                MenuEntry("Ajouter aux contacts", SecaIcons.PersonAdd) {
                    menuOpen = false
                    addContact(context, conversation.address)
                }
            }
            MenuEntry("Supprimer la conversation", SecaIcons.Delete) {
                menuOpen = false
                onDelete()
            }
        }
    }
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
                    text = "Recevoir et envoyer avec Seca",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Text(
                text = "Android ne confie les SMS qu'à une seule application à la fois.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onActivate) { Text("Activer") }
                TextButton(onClick = onDismiss, modifier = Modifier.padding(start = 8.dp)) { Text("Plus tard") }
            }
        }
    }
}
