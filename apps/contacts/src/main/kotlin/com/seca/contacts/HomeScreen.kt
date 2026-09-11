package com.seca.contacts

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seca.core.design.SecaAppIdentity
import com.seca.core.design.SecaIcons
import com.seca.core.design.component.SecaAvatar
import com.seca.core.design.component.SecaContactRow
import com.seca.core.design.component.SecaEmptyState
import com.seca.core.design.component.SecaFastScroller
import com.seca.core.design.component.SecaGroupItem
import com.seca.core.design.component.SecaProfileBadge
import com.seca.core.design.component.SecaSearchField
import com.seca.core.design.component.SecaSectionLabel
import com.seca.core.design.component.SecaSuiteBar
import com.seca.core.design.component.rememberContactThumbnail
import com.seca.core.model.SecaContact
import com.seca.core.model.initialsOf
import java.text.Normalizer
import kotlinx.coroutines.launch

@Composable
internal fun HomeScreen(
    ui: ContactsUi,
    viewModel: ContactsViewModel,
    listState: LazyListState,
    onOpenSibling: (SecaAppIdentity) -> Unit,
) {
    var addingProfile by remember { mutableStateOf(false) }
    // The button carries its label at the top of the list and shrinks to an icon once scrolled.
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            HomeTopBar(
                ui = ui,
                onQueryChange = viewModel::setQuery,
                onOpenSettings = { viewModel.open(Screen.Settings) },
                onSelectProfile = viewModel::selectProfile,
                onAddProfile = { addingProfile = true },
            )
        },
        bottomBar = { SecaSuiteBar(current = SecaAppIdentity.Contacts, onSelect = onOpenSibling) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Nouveau contact") },
                icon = {
                    Icon(SecaIcons.PersonAdd, contentDescription = if (fabExpanded) null else "Nouveau contact")
                },
                onClick = { viewModel.open(Screen.Edit(null)) },
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
            HomeContent(
                ui,
                listState,
                onOpen = { viewModel.open(Screen.Detail(it.id)) },
                onOpenMyCard = { viewModel.open(Screen.MyCard) },
            )
        }
    }
    if (addingProfile) {
        ProfileNameDialog(
            title = "Nouveau profil",
            initial = "",
            confirmLabel = "Créer",
            onDismiss = { addingProfile = false },
            onConfirm = {
                viewModel.addProfile(it)
                addingProfile = false
            },
        )
    }
}

/** Search on top; under it, one tab per profile and a button to add another. */
@Composable
private fun HomeTopBar(
    ui: ContactsUi,
    onQueryChange: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onAddProfile: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(top = 8.dp, bottom = 8.dp),
    ) {
        SecaSearchField(
            query = ui.query,
            onQueryChange = onQueryChange,
            placeholder = "Rechercher un contact",
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            IconButton(onClick = onOpenSettings) {
                Icon(SecaIcons.Settings, contentDescription = "Paramètres")
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            itemsIndexed(ui.profiles, key = { _, profile -> profile.id }) { index, profile ->
                ProfileTab(
                    name = profile.name,
                    tone = index,
                    count = ui.counts[profile.id] ?: 0,
                    selected = profile.id == ui.currentProfileId,
                    onClick = { onSelectProfile(profile.id) },
                )
            }
            item(key = "add-profile") { AddProfileTab(onClick = onAddProfile) }
        }
    }
}

@Composable
private fun ProfileTab(name: String, tone: Int, count: Int, selected: Boolean, onClick: () -> Unit) {
    // Selecting a profile morphs its pill toward a rounded square, as M3 Expressive toggles do.
    val corner by animateDpAsState(if (selected) 14.dp else 24.dp, label = "corner")
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "container",
    )
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(corner))
            .background(container)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .padding(start = 8.dp, end = 16.dp),
    ) {
        SecaProfileBadge(name = name, tone = tone, size = 32.dp)
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            modifier = Modifier.padding(start = 10.dp),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = content.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun AddProfileTab(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(48.dp)
            .clip(CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Icon(
            SecaIcons.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "Nouveau profil",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun HomeContent(
    ui: ContactsUi,
    listState: LazyListState,
    onOpen: (SecaContact) -> Unit,
    onOpenMyCard: () -> Unit,
) {
    if (!ui.loaded) return
    val searching = ui.query.isNotBlank()
    val shown = remember(ui) {
        if (searching) {
            ui.contacts.filter { matchesQuery(it, ui.query, ui.numbers) }
        } else {
            ui.contacts.filter { ui.profileOf(it).id == ui.currentProfileId }
        }
    }
    if (searching && shown.isEmpty()) {
        EmptyHome(ui, searching = true)
        return
    }
    val favorites = remember(shown, searching) { if (searching) emptyList() else shown.filter { it.isFavorite } }
    // The provider already sorts by name, so grouping keeps the letters in order.
    val sections = remember(shown, searching) {
        if (searching) emptyMap() else shown.groupBy { sectionLetterOf(it.displayName) }
    }
    // Where each letter's title sits in the list: after "Ma fiche" and the favourites.
    val letterIndex = remember(sections, favorites) {
        var index = 1 + if (favorites.isNotEmpty()) 2 else 0
        sections.mapValues { (_, group) -> index.also { index += 1 + group.size } }
    }
    val showScroller = sections.size >= MIN_SECTIONS_FOR_SCROLLER
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // Room at the bottom so the last contact clears the floating button, and on
            // the right for the letter strip when there is one.
            contentPadding = PaddingValues(end = if (showScroller) 20.dp else 0.dp, bottom = 112.dp),
        ) {
            if (!searching) {
                item(key = "my-card", contentType = "my-card") { MyCardRow(ui, onOpenMyCard) }
                if (shown.isEmpty()) {
                    item(key = "empty", contentType = "empty") {
                        EmptyHome(ui, searching = false, modifier = Modifier.fillParentMaxHeight(0.6f))
                    }
                }
            }
            if (searching) {
                // Search looks through every profile; results are grouped by profile.
                shown.groupBy { ui.profileOf(it) }.forEach { (profile, group) ->
                    item(key = "profile-${profile.id}", contentType = "label") { SecaSectionLabel(profile.name) }
                    contactGroup(group, keyPrefix = "result", ui = ui, onOpen = onOpen)
                }
            } else {
                if (favorites.isNotEmpty()) {
                    item(key = "favorites-label", contentType = "label") { SecaSectionLabel("Favoris") }
                    item(key = "favorites", contentType = "favorites") { FavoritesRow(favorites, ui, onOpen) }
                }
                sections.forEach { (letter, group) ->
                    item(key = "letter-$letter", contentType = "label") { SecaSectionLabel(letter) }
                    contactGroup(group, keyPrefix = "contact", ui = ui, onOpen = onOpen)
                }
                if (shown.isNotEmpty()) {
                    item(key = "count") {
                        Text(
                            text = if (shown.size == 1) "1 contact" else "${shown.size} contacts",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp),
                        )
                    }
                }
            }
        }
        if (showScroller) {
            SecaFastScroller(
                letters = sections.keys.toList(),
                onSelect = { letter -> letterIndex[letter]?.let { scope.launch { listState.scrollToItem(it) } } },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(top = 16.dp, bottom = 112.dp, end = 2.dp),
            )
        }
    }
}

/** A rounded group of contacts; each avatar takes its profile's colour. */
private fun LazyListScope.contactGroup(
    group: List<SecaContact>,
    keyPrefix: String,
    ui: ContactsUi,
    onOpen: (SecaContact) -> Unit,
) {
    // A shared content type lets the list reuse rows it scrolled past instead of building new ones.
    itemsIndexed(
        group,
        key = { _, contact -> "$keyPrefix-${contact.id}" },
        contentType = { _, _ -> "contact" },
    ) { index, contact ->
        SecaGroupItem(index = index, count = group.size) {
            SecaContactRow(
                contact = contact,
                onClick = { onOpen(contact) },
                supportingText = contact.phoneNumbers.firstOrNull()?.raw?.let(ui.numbers::display),
                tone = ui.toneOf(ui.profileOf(contact)),
                photo = rememberContactThumbnail(contact.photoUri),
            )
        }
    }
}

/** Favourites as a row of large avatars, the way a dialer shows speed dials. */
@Composable
private fun FavoritesRow(favorites: List<SecaContact>, ui: ContactsUi, onOpen: (SecaContact) -> Unit) {
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
                    .clickable { onOpen(contact) }
                    .padding(vertical = 8.dp),
            ) {
                SecaAvatar(
                    initials = contact.initials,
                    photoUri = contact.photoUri,
                    size = 64.dp,
                    tone = ui.toneOf(ui.profileOf(contact)),
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

@Composable
private fun EmptyHome(ui: ContactsUi, searching: Boolean, modifier: Modifier = Modifier) {
    when {
        searching -> SecaEmptyState(
            icon = SecaIcons.Search,
            title = "Aucun résultat",
            description = "Aucun contact ne correspond à « ${ui.query.trim()} ».",
            modifier = modifier,
        )
        ui.currentProfileId == ProfileStore.Principal.id -> SecaEmptyState(
            icon = SecaIcons.Contacts,
            title = "Aucun contact",
            description = "Les contacts enregistrés sur ce téléphone apparaîtront ici.",
            modifier = modifier,
        )
        else -> SecaEmptyState(
            icon = SecaIcons.Label,
            title = "« ${ui.currentProfile.name} » est vide",
            description = "Ouvrez la fiche d'un contact pour le ranger ici, ou créez-en un.",
            modifier = modifier,
        )
    }
}

/** "Ma fiche": the owner's own card, first in the list. */
@Composable
private fun MyCardRow(ui: ContactsUi, onClick: () -> Unit) {
    val mine = ui.myNumbers
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        SecaAvatar(
            initials = initialsOf(ui.myCard.name),
            photoUri = null,
            size = 64.dp,
            seed = MY_CARD_SEED,
            expressive = true,
            photo = ui.myPhoto,
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(
                text = ui.myCard.displayName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (mine.isEmpty()) {
                    "Ajoutez votre numéro, votre nom et une photo"
                } else {
                    mine.joinToString(" · ", transform = ui.numbers::display)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Below this many letters the list is short enough to scroll by hand. */
private const val MIN_SECTIONS_FOR_SCROLLER = 4

/** "Élodie" files under E, not under a separate É; anything without a letter under #. */
private fun sectionLetterOf(name: String): String {
    val letter = name.firstOrNull { it.isLetter() } ?: return "#"
    return Normalizer.normalize(letter.toString(), Normalizer.Form.NFD).first().uppercaseChar().toString()
}
