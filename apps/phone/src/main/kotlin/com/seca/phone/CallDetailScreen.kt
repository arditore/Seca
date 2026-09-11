package com.seca.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.seca.core.contacts.NumberMatch
import com.seca.core.design.SecaIcons
import com.seca.core.design.component.SecaActionButton
import com.seca.core.design.component.SecaAvatar
import com.seca.core.design.component.SecaGroupItem
import com.seca.core.design.component.SecaProfileBadge
import com.seca.core.design.component.SecaSectionLabel
import com.seca.core.design.component.SecaTopBar

/** Who a number belongs to, what can be done with it, and every call with them. */
@Composable
internal fun CallDetailScreen(
    number: String,
    ui: PhoneUi,
    viewModel: PhoneViewModel,
    onCall: (String) -> Unit,
    onDeleteCalls: (List<Long>) -> Unit,
) {
    val context = LocalContext.current
    val match = remember(number, ui.index) { ui.index?.find(number) }
    val contact = match?.contact
    val history = remember(number, ui.calls, ui.index) { ui.historyFor(number) }
    // A contact with several numbers: each call says which one it was.
    val showNumbers = (contact?.phoneNumbers?.size ?: 0) > 1
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            SecaTopBar(title = "", onBack = { viewModel.back() }) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(SecaIcons.MoreVert, contentDescription = "Plus d'options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Copier le numéro") },
                            leadingIcon = { Icon(SecaIcons.ContentCopy, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                copyNumber(context, match?.number?.raw ?: number)
                            },
                        )
                        if (history.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Supprimer cet historique") },
                                leadingIcon = { Icon(SecaIcons.Delete, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onDeleteCalls(history.map { it.id })
                                },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item(key = "header") { Header(number, match, ui) }
            item(key = "actions") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 28.dp),
                ) {
                    SecaActionButton(
                        SecaIcons.Phone,
                        "Appeler",
                        onClick = { onCall(match?.number?.raw ?: number) },
                        modifier = Modifier.weight(1f),
                        emphasized = true,
                    )
                    SecaActionButton(SecaIcons.Messages, "Message", onClick = { sms(context, number) }, modifier = Modifier.weight(1f))
                    if (contact != null) {
                        SecaActionButton(
                            SecaIcons.Contacts,
                            "Fiche",
                            onClick = { openContact(context, contact.id, contact.lookupKey) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        SecaActionButton(
                            SecaIcons.PersonAdd,
                            "Ajouter",
                            onClick = { addContact(context, number) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            if (history.isNotEmpty()) {
                item(key = "history-label") { SecaSectionLabel("Historique") }
                itemsIndexed(history, key = { _, call -> call.id }) { index, call ->
                    SecaGroupItem(index = index, count = history.size) { HistoryRow(call, ui, showNumbers) }
                }
            }
        }
    }
}

@Composable
private fun Header(number: String, match: NumberMatch?, ui: PhoneUi) {
    val contact = match?.contact
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (contact != null) {
            SecaAvatar(
                initials = contact.initials,
                photoUri = contact.photoUri,
                size = 120.dp,
                tone = ui.toneOf(contact),
                seed = contact.displayName,
                expressive = true,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            UnknownAvatar(modifier = Modifier.padding(top = 8.dp), size = 120.dp)
        }
        Text(
            text = contact?.displayName ?: ui.numbers.display(number),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp),
        )
        if (contact != null) {
            // The profile the contact is filed under, as Seca Contacts shows it.
            val profile = ui.profileOf(contact)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            ) {
                SecaProfileBadge(profile.name, tone = ui.profiles.toneOf(profile), size = 28.dp)
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                text = ui.numbers.display(match.number.raw),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        } else {
            ui.numbers.describe(number)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(call: CallRecord, ui: PhoneUi, showNumber: Boolean) {
    val context = LocalContext.current
    val details = listOfNotNull(
        "${dayLabel(call.date)}, ${timeOf(context, call.date)}",
        durationOf(call.durationSeconds),
        if (showNumber) ui.numbers.display(call.number) else null,
    ).joinToString(" · ")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        CallTypeIcon(call.type, Modifier.size(20.dp))
        Column(Modifier.padding(start = 16.dp)) {
            Text(call.type.label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
