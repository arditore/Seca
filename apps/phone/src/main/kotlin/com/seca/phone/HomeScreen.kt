package com.seca.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.seca.core.design.component.SecaSearchField
import com.seca.core.design.component.SecaSectionLabel
import com.seca.core.design.component.SecaSuiteBar
import com.seca.core.model.Profile
import com.seca.core.model.SecaContact
import java.text.Normalizer

@Composable
internal fun HomeScreen(ui: PhoneUi, viewModel: PhoneViewModel, listState: LazyListState, onCall: (String) -> Unit) {
    val context = LocalContext.current
    // The button carries its label at the top of the list and shrinks to an icon once scrolled.
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val onOpen: (String) -> Unit = { viewModel.open(PhoneScreen.CallDetail(it)) }
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
                    placeholder = "Rechercher un contact ou un numéro",
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                if (ui.query.isBlank()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    ) {
                        SecaChoicePill("Tous", selected = ui.filter == CallFilter.All, onClick = { viewModel.setFilter(CallFilter.All) })
                        SecaChoicePill(
                            "Manqués",
                            selected = ui.filter == CallFilter.Missed,
                            onClick = { viewModel.setFilter(CallFilter.Missed) },
                        )
                    }
                }
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
                SearchResults(ui, onCall, onOpen)
            } else {
                Recents(ui, listState, onCall, onOpen)
            }
        }
    }
}

@Composable
private fun Recents(ui: PhoneUi, listState: LazyListState, onCall: (String) -> Unit, onOpen: (String) -> Unit) {
    if (!ui.loaded) return
    val favorites = remember(ui.contacts) { ui.contacts.filter { it.isFavorite && it.phoneNumbers.isNotEmpty() } }
    val showFavorites = favorites.isNotEmpty() && ui.filter == CallFilter.All
    if (ui.groups.isEmpty() && !showFavorites) {
        SecaEmptyState(
            icon = if (ui.filter == CallFilter.Missed) SecaIcons.CallMissed else SecaIcons.Phone,
            title = if (ui.filter == CallFilter.Missed) "Aucun appel manqué" else "Aucun appel",
            description = "Les appels passés et reçus sur ce téléphone apparaîtront ici.",
        )
        return
    }
    val byDay = remember(ui.groups) { ui.groups.groupBy { dayOf(it.latest.date) } }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        // Room at the bottom so the last call clears the floating button.
        contentPadding = PaddingValues(bottom = 112.dp),
    ) {
        if (showFavorites) {
            item(key = "favorites-label") { SecaSectionLabel("Favoris") }
            item(key = "favorites") { FavoritesRow(favorites, ui, onCall) }
        }
        byDay.values.forEach { rows ->
            item(key = "day-${rows.first().latest.id}", contentType = "label") {
                SecaSectionLabel(dayLabel(rows.first().latest.date))
            }
            // A shared content type lets the list reuse rows it scrolled past instead of building new ones.
            itemsIndexed(rows, key = { _, group -> group.latest.id }, contentType = { _, _ -> "call" }) { index, group ->
                SecaGroupItem(index = index, count = rows.size) {
                    CallRow(group, ui, onCall, onOpen)
                }
            }
        }
    }
}

/**
 * One row of the history: who, how many times, how and when. A contact's
 * profile other than Principal is named, so "Travail" calls stand out.
 */
@Composable
private fun CallRow(group: CallGroup, ui: PhoneUi, onCall: (String) -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val call = group.latest
    val contact = group.match?.contact
    val title = contact?.displayName ?: callerLabel(call, ui.numbers)
    val details = buildList {
        add(timeOf(context, call.date))
        if (contact != null) {
            ui.profileOf(contact).takeIf { it.id != Profile.Principal.id }?.let { add(it.name) }
        } else if (call.callable) {
            ui.numbers.countryOf(call.number)?.takeIf { !it.isHome }?.let { add("${it.flag} ${it.displayName}") }
        }
    }.joinToString(" · ")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = call.callable) { onOpen(call.number) }
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
    ) {
        if (contact != null) {
            SecaAvatar(
                initials = contact.initials,
                photoUri = contact.photoUri,
                size = 44.dp,
                tone = ui.toneOf(contact),
                seed = contact.displayName,
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
            FilledTonalIconButton(onClick = { onCall(call.number) }) {
                Icon(SecaIcons.Phone, contentDescription = "Appeler $title")
            }
        }
    }
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

/** Contacts matching the search by name or number, and the typed number itself when it is one. */
@Composable
private fun SearchResults(ui: PhoneUi, onCall: (String) -> Unit, onOpen: (String) -> Unit) {
    val query = ui.query.trim()
    val results = remember(ui.contacts, query) {
        val name = searchable(query)
        ui.contacts.filter { contact ->
            contact.phoneNumbers.isNotEmpty() &&
                (searchable(contact.displayName).contains(name) ||
                    (query.count(Char::isDigit) >= 2 && contact.phoneNumbers.any { ui.numbers.matchesDigits(it.raw, query) }))
        }
    }
    val isNumber = query.count(Char::isDigit) >= 3 && query.all { it.isDigit() || it in "+ ()-.*#" }
    if (!isNumber && results.isEmpty()) {
        SecaEmptyState(
            icon = SecaIcons.Search,
            title = "Aucun résultat",
            description = "Aucun contact ne correspond à « $query ».",
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 112.dp)) {
        if (isNumber) {
            item(key = "number-label") { SecaSectionLabel("Numéro") }
            item(key = "number") {
                SecaGroupItem(index = 0, count = 1, onClick = { onCall(query) }) {
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
        if (results.isNotEmpty()) {
            item(key = "contacts-label") { SecaSectionLabel("Contacts") }
            itemsIndexed(results, key = { _, contact -> contact.id }) { index, contact ->
                val number = contact.phoneNumbers.first().raw
                SecaGroupItem(index = index, count = results.size) {
                    SecaContactRow(
                        contact = contact,
                        onClick = { onOpen(number) },
                        supportingText = ui.numbers.display(number),
                        tone = ui.toneOf(contact),
                    )
                }
            }
        }
    }
}

private val Accents = Regex("\\p{M}+")

private fun searchable(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(Accents, "").lowercase()
