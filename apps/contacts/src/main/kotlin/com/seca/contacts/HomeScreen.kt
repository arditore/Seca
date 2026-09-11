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
import com.seca.core.design.component.SecaGroupItem
import com.seca.core.design.component.SecaSearchField
import com.seca.core.design.component.SecaSuiteBar
import com.seca.core.model.SecaContact
import java.text.Normalizer

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
            HomeContent(ui, listState, onOpen = { viewModel.open(Screen.Detail(it.id)) })
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
        ProfileBadge(name = name, tone = tone, size = 32.dp)
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
private fun HomeContent(ui: ContactsUi, listState: LazyListState, onOpen: (SecaContact) -> Unit) {
    if (!ui.loaded) return
    val searching = ui.query.isNotBlank()
    val shown = remember(ui) {
        if (searching) {
            ui.contacts.filter { matchesQuery(it, ui.query) }
        } else {
            ui.contacts.filter { ui.profileOf(it).id == ui.currentProfileId }
        }
    }
    if (shown.isEmpty()) {
        EmptyHome(ui, searching)
        return
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        // Room at the bottom so the last contact clears the floating button.
        contentPadding = PaddingValues(bottom = 112.dp),
    ) {
        if (searching) {
            // Search looks through every profile; results are grouped by profile.
            shown.groupBy { ui.profileOf(it) }.forEach { (profile, group) ->
                item(key = "profile-${profile.id}") { SectionLabel(profile.name) }
                contactGroup(group, keyPrefix = "result", onOpen = onOpen)
            }
        } else {
            val favorites = shown.filter { it.isFavorite }
            if (favorites.isNotEmpty()) {
                item(key = "favorites-label") { SectionLabel("Favoris") }
                item(key = "favorites") { FavoritesRow(favorites, onOpen) }
            }
            // The provider already sorts by name, so grouping keeps the letters in order.
            shown.groupBy { sectionLetterOf(it.displayName) }.forEach { (letter, group) ->
                item(key = "letter-$letter") { SectionLabel(letter) }
                contactGroup(group, keyPrefix = "contact", onOpen = onOpen)
            }
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

private fun LazyListScope.contactGroup(
    group: List<SecaContact>,
    keyPrefix: String,
    onOpen: (SecaContact) -> Unit,
) {
    itemsIndexed(group, key = { _, contact -> "$keyPrefix-${contact.id}" }) { index, contact ->
        SecaGroupItem(index = index, count = group.size) {
            SecaContactRow(
                contact = contact,
                onClick = { onOpen(contact) },
                supportingText = contact.phoneNumbers.firstOrNull()?.raw?.let(::formatNumber),
            )
        }
    }
}

/** Favourites as a row of large avatars, the way a dialer shows speed dials. */
@Composable
private fun FavoritesRow(favorites: List<SecaContact>, onOpen: (SecaContact) -> Unit) {
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

@Composable
private fun EmptyHome(ui: ContactsUi, searching: Boolean) {
    when {
        searching -> SecaEmptyState(
            icon = SecaIcons.Search,
            title = "Aucun résultat",
            description = "Aucun contact ne correspond à « ${ui.query.trim()} ».",
        )
        ui.currentProfileId == ProfileStore.Principal.id -> SecaEmptyState(
            icon = SecaIcons.Contacts,
            title = "Aucun contact",
            description = "Les contacts enregistrés sur ce téléphone apparaîtront ici.",
        )
        else -> SecaEmptyState(
            icon = SecaIcons.Label,
            title = "« ${ui.currentProfile.name} » est vide",
            description = "Ouvrez la fiche d'un contact pour le ranger ici, ou créez-en un.",
        )
    }
}

/** "Élodie" files under E, not under a separate É; anything without a letter under #. */
private fun sectionLetterOf(name: String): String {
    val letter = name.firstOrNull { it.isLetter() } ?: return "#"
    return Normalizer.normalize(letter.toString(), Normalizer.Form.NFD).first().uppercaseChar().toString()
}
