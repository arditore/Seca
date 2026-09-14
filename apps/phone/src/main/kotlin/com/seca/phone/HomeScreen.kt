package com.seca.phone

import android.provider.BlockedNumberContract
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seca.core.design.SecaAppIdentity
import com.seca.core.design.SecaIcons
import com.seca.core.design.component.SecaAvatar
import com.seca.core.design.component.SecaChoicePill
import com.seca.core.design.component.SecaContactRow
import com.seca.core.design.component.SecaEmptyState
import com.seca.core.design.component.SecaGroupItem
import com.seca.core.design.component.SecaProfileBadge
import com.seca.core.design.component.SecaSearchField
import com.seca.core.design.component.SecaSectionLabel
import com.seca.core.design.component.SecaSuiteBar
import com.seca.core.design.component.rememberContactThumbnail
import com.seca.core.model.Profile
import com.seca.core.model.SecaContact
import java.text.Normalizer

@Composable
internal fun HomeScreen(
    ui: PhoneUi,
    viewModel: PhoneViewModel,
    listState: LazyListState,
    onCall: (String) -> Unit,
    onDeleteCalls: (List<Long>) -> Unit,
    isDefaultDialer: Boolean,
    onBecomeDefault: () -> Unit,
) {
    val context = LocalContext.current
    var bannerDismissed by remember { mutableStateOf(false) }
    // The button carries its label at the top of the list and shrinks to an icon once scrolled.
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val actions = RowActions(
        onCall = onCall,
        onOpen = { viewModel.open(PhoneScreen.CallDetail(it)) },
        onDelete = onDeleteCalls,
    )
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
                // Left the call screen during a call: the way back, and hanging up, stay at the top.
                OngoingCallBanner(Modifier.padding(bottom = 8.dp))
                SecaSearchField(
                    query = ui.query,
                    onQueryChange = viewModel::setQuery,
                    placeholder = "Rechercher un contact ou un numéro",
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    IconButton(onClick = { viewModel.open(PhoneScreen.Settings) }) {
                        Icon(SecaIcons.Settings, contentDescription = "Paramètres")
                    }
                }
                if (ui.query.isBlank()) FilterRow(ui, onSelect = viewModel::setFilter)
            }
        },
        bottomBar = { SecaSuiteBar(current = SecaAppIdentity.Phone, onSelect = { openSibling(context, it) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Clavier") },
                icon = { Icon(SecaIcons.Dialpad, contentDescription = if (fabExpanded) null else "Clavier") },
                onClick = { viewModel.openDialer("") },
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
            if (ui.query.isNotBlank()) {
                SearchResults(ui, actions)
            } else {
                Recents(
                    ui = ui,
                    listState = listState,
                    actions = actions,
                    showBanner = !isDefaultDialer && !bannerDismissed,
                    onBecomeDefault = onBecomeDefault,
                    onDismissBanner = { bannerDismissed = true },
                )
            }
        }
    }
}

/** What a history row can do; gathered so every row gets the same. */
private class RowActions(
    val onCall: (String) -> Unit,
    val onOpen: (String) -> Unit,
    val onDelete: (List<Long>) -> Unit,
)

/** The history's filters: how calls went, then — with more than one — the profiles. */
@Composable
private fun FilterRow(ui: PhoneUi, onSelect: (CallFilter) -> Unit) {
    val profiles = if (ui.profiles.connected && ui.profiles.profiles.size > 1) ui.profiles.profiles else emptyList()
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 12.dp),
    ) {
        items(CallFilter.ByType, key = { filterLabel(it) }) { filter ->
            SecaChoicePill(filterLabel(filter), selected = ui.filter == filter, onClick = { onSelect(filter) })
        }
        items(profiles, key = { "profile-${it.id}" }) { profile ->
            val filter = CallFilter.ByProfile(profile.id)
            SecaChoicePill(
                label = profile.name,
                selected = ui.filter == filter,
                onClick = { onSelect(filter) },
                leading = { SecaProfileBadge(profile.name, tone = ui.profiles.toneOf(profile), size = 28.dp) },
            )
        }
    }
}

private fun filterLabel(filter: CallFilter): String = when (filter) {
    CallFilter.All -> "Tous"
    CallFilter.Missed -> "Manqués"
    CallFilter.Incoming -> "Entrants"
    CallFilter.Outgoing -> "Sortants"
    CallFilter.Rejected -> "Refusés"
    CallFilter.Voicemail -> "Messagerie"
    is CallFilter.ByProfile -> "Profil"
}

@Composable
private fun Recents(
    ui: PhoneUi,
    listState: LazyListState,
    actions: RowActions,
    showBanner: Boolean,
    onBecomeDefault: () -> Unit,
    onDismissBanner: () -> Unit,
) {
    if (!ui.loaded) return
    val favorites = remember(ui.contacts) { ui.contacts.filter { it.isFavorite && it.phoneNumbers.isNotEmpty() } }
    val showFavorites = favorites.isNotEmpty() && ui.filter == CallFilter.All
    if (ui.groups.isEmpty() && !showFavorites) {
        Column {
            if (showBanner) DefaultDialerBanner(onBecomeDefault, onDismissBanner)
            EmptyHistory(ui.filter)
        }
        return
    }
    val byDay = remember(ui.groups) { ui.groups.groupBy { dayOf(it.latest.date) } }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        // Room at the bottom so the last call clears the floating button.
        contentPadding = PaddingValues(bottom = 112.dp),
    ) {
        if (showBanner) {
            item(key = "default-banner", contentType = "banner") { DefaultDialerBanner(onBecomeDefault, onDismissBanner) }
        }
        if (showFavorites) {
            item(key = "favorites-label", contentType = "label") { SecaSectionLabel("Favoris") }
            item(key = "favorites", contentType = "favorites") { FavoritesRow(favorites, ui, actions.onCall) }
        }
        byDay.values.forEach { rows ->
            item(key = "day-${rows.first().latest.id}", contentType = "label") {
                SecaSectionLabel(dayLabel(rows.first().latest.date))
            }
            // A shared content type lets the list reuse rows it scrolled past instead of building new ones.
            itemsIndexed(rows, key = { _, group -> group.latest.id }, contentType = { _, _ -> "call" }) { index, group ->
                SecaGroupItem(index = index, count = rows.size) {
                    CallRow(group, ui, actions)
                }
            }
        }
    }
}

/** Offers to make Seca the phone app, which is what lets it show incoming calls. */
@Composable
private fun DefaultDialerBanner(onActivate: () -> Unit, onDismiss: () -> Unit) {
    SecaGroupItem(index = 0, count = 1, modifier = Modifier.padding(top = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(SecaIcons.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = "Répondre aux appels avec Seca",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Text(
                text = "Vous verrez qui appelle et son profil, et vous pourrez répondre depuis l'écran verrouillé.",
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

@Composable
private fun EmptyHistory(filter: CallFilter) {
    val (title, icon) = when (filter) {
        CallFilter.All -> "Aucun appel" to SecaIcons.Phone
        CallFilter.Missed -> "Aucun appel manqué" to SecaIcons.CallMissed
        CallFilter.Incoming -> "Aucun appel reçu" to SecaIcons.CallReceived
        CallFilter.Outgoing -> "Aucun appel émis" to SecaIcons.CallMade
        CallFilter.Rejected -> "Aucun appel refusé ni bloqué" to SecaIcons.Block
        CallFilter.Voicemail -> "Aucun message vocal" to SecaIcons.Voicemail
        is CallFilter.ByProfile -> "Aucun appel avec ce profil" to SecaIcons.Label
    }
    SecaEmptyState(
        icon = icon,
        title = title,
        description = "Les appels passés et reçus sur ce téléphone apparaîtront ici.",
    )
}

/**
 * One row of the history: who, how many times, how and when. A contact's
 * profile other than Principal is named, so "Travail" calls stand out. A long
 * press offers the rest: copy, message, the contact, deletion.
 */
@Composable
private fun CallRow(group: CallGroup, ui: PhoneUi, actions: RowActions) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val call = group.latest
    val contact = group.match?.contact
    val title = contact?.displayName ?: callerLabel(call, ui.numbers)
    val details = buildList {
        add(timeOf(context, call.date))
        ui.simLabels[call.accountId]?.let { add(it) }
        if (contact != null) {
            ui.profileOf(contact).takeIf { it.id != Profile.Principal.id }?.let { add(it.name) }
        } else if (call.callable) {
            ui.numbers.countryOf(call.number)?.takeIf { !it.isHome }?.let { add("${it.flag} ${it.displayName}") }
        }
    }.joinToString(" · ")
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { if (call.callable) actions.onOpen(call.number) },
                    onLongClickLabel = "Plus d'actions",
                    onLongClick = { menuOpen = true },
                )
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        ) {
            if (contact != null) {
                SecaAvatar(
                    initials = contact.initials,
                    photoUri = contact.photoUri,
                    size = 44.dp,
                    tone = ui.toneOf(contact),
                    seed = contact.displayName,
                    photo = rememberContactThumbnail(contact.photoUri),
                )
            } else {
                UnknownAvatar()
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            ) {
                Text(
                    text = if (group.calls.size > 1) "$title (${group.calls.size})" else title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (call.type == CallType.Missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CallTypeIcon(call.type, Modifier.size(16.dp))
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            if (call.callable) {
                FilledTonalIconButton(onClick = { actions.onCall(call.number) }) {
                    Icon(SecaIcons.Phone, contentDescription = "Appeler $title")
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (call.callable) {
                MenuEntry("Copier le numéro", SecaIcons.ContentCopy) {
                    menuOpen = false
                    copyNumber(context, call.number)
                }
                MenuEntry("Envoyer un message", SecaIcons.Messages) {
                    menuOpen = false
                    sms(context, call.number)
                }
                if (contact != null) {
                    MenuEntry("Voir la fiche", SecaIcons.Contacts) {
                        menuOpen = false
                        openContact(context, contact.id, contact.lookupKey)
                    }
                } else {
                    MenuEntry("Ajouter aux contacts", SecaIcons.PersonAdd) {
                        menuOpen = false
                        addContact(context, call.number)
                    }
                }
            }
            // Blocking is Android's own list, which only the default phone app may change.
            if (call.callable && BlockedNumberContract.canCurrentUserBlockNumbers(context)) {
                MenuEntry("Bloquer ce numéro", SecaIcons.Block) {
                    menuOpen = false
                    val blocked = blockNumber(context, call.number)
                    Toast.makeText(
                        context,
                        if (blocked) "Numéro bloqué" else "Blocage impossible",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            MenuEntry(
                if (group.calls.size > 1) "Supprimer ces ${group.calls.size} appels" else "Supprimer de l'historique",
                SecaIcons.Delete,
            ) {
                menuOpen = false
                actions.onDelete(group.calls.map { it.id })
            }
        }
    }
}

@Composable
private fun MenuEntry(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

/** Favourites as large avatars; one tap calls, as on a speed dial. */
@Composable
private fun FavoritesRow(favorites: List<SecaContact>, ui: PhoneUi, onCall: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(favorites, key = { it.id }) { contact ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(88.dp)
                    .clip(MaterialTheme.shapes.large)
                    .clickable(onClickLabel = "Appeler") { onCall(contact.phoneNumbers.first().raw) }
                    .padding(vertical = 8.dp),
            ) {
                SecaAvatar(
                    initials = contact.initials,
                    photoUri = contact.photoUri,
                    size = 64.dp,
                    tone = ui.toneOf(contact),
                    seed = contact.displayName,
                    expressive = true,
                    photo = rememberContactThumbnail(contact.photoUri),
                )
                Text(
                    text = contact.displayName.substringBefore(' '),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
                )
            }
        }
    }
}

/** The typed number itself, contacts matching by name or number, and matching calls from the history. */
@Composable
private fun SearchResults(ui: PhoneUi, actions: RowActions) {
    val query = ui.query.trim()
    val digits = query.count(Char::isDigit)
    val contacts = remember(ui.contacts, query) {
        val name = searchable(query)
        ui.contacts.filter { contact ->
            contact.phoneNumbers.isNotEmpty() &&
                (searchable(contact.displayName).contains(name) ||
                    (digits >= 2 && contact.phoneNumbers.any { ui.numbers.matchesDigits(it.raw, query) }))
        }
    }
    val calls = remember(ui.groups, query) {
        val name = searchable(query)
        ui.groups.filter { group ->
            group.match?.contact?.displayName?.let { searchable(it).contains(name) } == true ||
                (digits >= 2 && group.latest.callable && ui.numbers.matchesDigits(group.latest.number, query))
        }.take(MAX_CALL_RESULTS)
    }
    val isNumber = digits >= 3 && query.all { it.isDigit() || it in "+ ()-.*#" }
    if (!isNumber && contacts.isEmpty() && calls.isEmpty()) {
        SecaEmptyState(
            icon = SecaIcons.Search,
            title = "Aucun résultat",
            description = "Rien ne correspond à « $query ».",
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 112.dp)) {
        if (isNumber) {
            item(key = "number-label", contentType = "label") { SecaSectionLabel("Numéro") }
            item(key = "number") {
                SecaGroupItem(index = 0, count = 1, onClick = { actions.onCall(query) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                        Icon(SecaIcons.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(
                                text = "Appeler ${ui.numbers.display(query)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            ui.numbers.describe(query)?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        if (contacts.isNotEmpty()) {
            item(key = "contacts-label", contentType = "label") { SecaSectionLabel("Contacts") }
            itemsIndexed(contacts, key = { _, contact -> "contact-${contact.id}" }, contentType = { _, _ -> "contact" }) { index, contact ->
                val number = contact.phoneNumbers.first().raw
                SecaGroupItem(index = index, count = contacts.size) {
                    SecaContactRow(
                        contact = contact,
                        onClick = { actions.onOpen(number) },
                        supportingText = ui.numbers.display(number),
                        tone = ui.toneOf(contact),
                        photo = rememberContactThumbnail(contact.photoUri),
                    )
                }
            }
        }
        if (calls.isNotEmpty()) {
            item(key = "calls-label", contentType = "label") { SecaSectionLabel("Historique") }
            itemsIndexed(calls, key = { _, group -> "call-${group.latest.id}" }, contentType = { _, _ -> "call" }) { index, group ->
                SecaGroupItem(index = index, count = calls.size) { CallRow(group, ui, actions) }
            }
        }
    }
}

private const val MAX_CALL_RESULTS = 30

private val Accents = Regex("\\p{M}+")

private fun searchable(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(Accents, "").lowercase()
