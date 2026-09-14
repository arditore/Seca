package com.seca.contacts

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.seca.core.contacts.findDuplicates
import com.seca.core.design.SecaIcons
import com.seca.core.design.component.SecaContactRow
import com.seca.core.design.component.SecaEmptyState
import com.seca.core.design.component.SecaGroupItem
import com.seca.core.design.component.SecaHint
import com.seca.core.design.component.SecaSectionLabel
import com.seca.core.design.component.SecaSettingRow
import com.seca.core.design.component.SecaTopBar
import com.seca.core.design.component.rememberContactThumbnail
import com.seca.core.model.SecaContact

/** The contacts that look like the same person. */
data object DuplicatesRoute : Screen

/**
 * Contacts sharing a name or a number, group by group, each merged in one tap
 * once the owner confirms. Android keeps every original entry: nothing is
 * deleted.
 */
@Composable
internal fun DuplicatesScreen(ui: ContactsUi, viewModel: ContactsViewModel, withWrite: (() -> Unit) -> Unit) {
    val groups = remember(ui.contacts) { findDuplicates(ui.contacts, ui.numbers) }
    var merging by remember { mutableStateOf<List<SecaContact>?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { SecaTopBar(title = "Doublons", onBack = { viewModel.back() }) },
    ) { padding ->
        if (groups.isEmpty()) {
            SecaEmptyState(
                icon = SecaIcons.Contacts,
                title = "Aucun doublon",
                description = "Aucun contact ne porte le même nom ou le même numéro qu'un autre.",
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item(key = "hint") {
                SecaHint("Fusionner réunit les fiches d'une même personne sous un seul contact. Android garde chaque fiche d'origine : rien n'est supprimé.")
            }
            groups.forEach { group ->
                val first = group.first()
                item(key = "label-${first.id}") { SecaSectionLabel(first.displayName.ifBlank { "Sans nom" }) }
                itemsIndexed(group, key = { _, contact -> "contact-${contact.id}" }) { index, contact ->
                    SecaGroupItem(index = index, count = group.size + 1) {
                        SecaContactRow(
                            contact = contact,
                            onClick = { viewModel.open(Screen.Detail(contact.id)) },
                            tone = ui.toneOf(ui.profileOf(contact)),
                            photo = rememberContactThumbnail(contact.photoUri),
                        )
                    }
                }
                item(key = "merge-${first.id}") {
                    SecaGroupItem(index = group.size, count = group.size + 1, onClick = { merging = group }) {
                        SecaSettingRow(icon = SecaIcons.Link, title = "Fusionner ces ${group.size} fiches")
                    }
                }
            }
        }
    }

    merging?.let { group ->
        AlertDialog(
            onDismissRequest = { merging = null },
            icon = { Icon(SecaIcons.Link, contentDescription = null) },
            title = { Text("Fusionner ${group.size} fiches ?") },
            text = {
                Text(
                    "${group.joinToString(", ") { it.displayName.ifBlank { "Sans nom" } }} ne formeront plus qu'un contact, " +
                        "rangé dans le profil de la première fiche.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        merging = null
                        withWrite { viewModel.merge(group) }
                    },
                ) { Text("Fusionner") }
            },
            dismissButton = { TextButton(onClick = { merging = null }) { Text("Annuler") } },
        )
    }
}
