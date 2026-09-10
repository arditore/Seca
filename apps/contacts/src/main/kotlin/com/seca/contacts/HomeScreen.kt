package com.seca.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.seca.core.design.SecaAppIdentity
import com.seca.core.design.SecaIcons
import com.seca.core.design.component.SecaAvatar
import com.seca.core.design.component.SecaContactRow
import com.seca.core.design.component.SecaEmptyState
import com.seca.core.design.component.SecaSearchField
import com.seca.core.design.component.SecaSuiteBar
import com.seca.core.model.SecaContact
import com.seca.core.model.initialsOf
import java.text.Normalizer

@Composable
internal fun HomeScreen(
    ui: ContactsUi,
    viewModel: ContactsViewModel,
    onOpenSibling: (SecaAppIdentity) -> Unit,
) {
    var addingProfile by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            HomeTopBar(
                ui = ui,
                onSelectProfile = viewModel::selectProfile,
                onAddProfile = { addingProfile = true },
                onOpenSettings = { viewModel.open(Screen.Settings) },
                onQueryChange = viewModel::setQuery,
            )
        },
        bottomBar = { SecaSuiteBar(current = SecaAppIdentity.Contacts, onSelect = onOpenSibling) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.open(Screen.Edit(null)) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(SecaIcons.Add, contentDescription = "Ajouter un contact")
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            HomeContent(ui, onOpen = { viewModel.open(Screen.Detail(it.id)) })
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

@Composable
private fun HomeTopBar(
    ui: ContactsUi,
    onSelectProfile: (String) -> Unit,
    onAddProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onQueryChange: (String) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val profile = ui.currentProfile
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraLarge)
                        .clickable { menuOpen = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    SecaAvatar(initials = initialsOf(profile.name).take(1), photoUri = null, size = 40.dp)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(
                            text = "Profil",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = profile.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Icon(
                                SecaIcons.ArrowDropDown,
                                contentDescription = "Changer de profil",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    ProfileMenuItems(
                        profiles = ui.profiles,
                        selectedId = ui.currentProfileId,
                        label = { p -> "${p.name} (${ui.contacts.count { ui.profileOf(it).id == p.id }})" },
                        onSelect = {
                            menuOpen = false
                            onSelectProfile(it.id)
                        },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Ajouter un profil") },
                        leadingIcon = { Icon(SecaIcons.Add, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onAddProfile()
                        },
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) {
                Icon(SecaIcons.Settings, contentDescription = "Paramètres")
            }
        }
        SecaSearchField(
            query = ui.query,
            onQueryChange = onQueryChange,
            placeholder = "Rechercher un contact",
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun HomeContent(ui: ContactsUi, onOpen: (SecaContact) -> Unit) {
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
        when {
            searching -> SecaEmptyState(
                title = "Aucun résultat",
                description = "Aucun contact ne correspond à « ${ui.query.trim()} ».",
            )
            ui.currentProfileId == ProfileStore.Principal.id -> SecaEmptyState(
                title = "Aucun contact",
                description = "Les contacts enregistrés sur ce téléphone apparaîtront ici.",
            )
            else -> SecaEmptyState(
                title = "Aucun contact",
                description = "Rangez un contact dans « ${ui.currentProfile.name} » depuis sa fiche, ou créez-en un.",
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Room at the bottom so the last contact is not hidden behind the + button.
        contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
    ) {
        if (searching) {
            // Search looks through every profile; results are grouped by profile.
            shown.groupBy { ui.profileOf(it) }.forEach { (profile, group) ->
                item(key = "profile-${profile.id}") { SectionHeader(profile.name) }
                items(group, key = { "result-${it.id}" }) { contact ->
                    SecaContactRow(contact = contact, onClick = { onOpen(contact) })
                }
            }
        } else {
            val favorites = shown.filter { it.isFavorite }
            if (favorites.isNotEmpty()) {
                item(key = "favorites") { SectionHeader("Favoris") }
                items(favorites, key = { "favorite-${it.id}" }) { contact ->
                    SecaContactRow(contact = contact, onClick = { onOpen(contact) })
                }
            }
            // The provider already sorts by name, so grouping keeps the letters in order.
            shown.groupBy { sectionLetterOf(it.displayName) }.forEach { (letter, group) ->
                item(key = "letter-$letter") { SectionHeader(letter) }
                items(group, key = { it.id }) { contact ->
                    SecaContactRow(contact = contact, onClick = { onOpen(contact) })
                }
            }
        }
    }
}

/** "Élodie" files under E, not under a separate É; anything without a letter under #. */
private fun sectionLetterOf(name: String): String {
    val letter = name.firstOrNull { it.isLetter() } ?: return "#"
    return Normalizer.normalize(letter.toString(), Normalizer.Form.NFD).first().uppercaseChar().toString()
}
