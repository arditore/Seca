package com.seca.messages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.seca.core.design.SecaIcons
import com.seca.core.design.component.SecaContactRow
import com.seca.core.design.component.SecaGroupItem
import com.seca.core.design.component.SecaSectionLabel
import com.seca.core.design.component.SecaTopBar
import com.seca.core.design.component.rememberContactThumbnail
import com.seca.core.model.PhoneNumber
import com.seca.core.model.SecaContact
import androidx.compose.ui.platform.LocalResources
import com.seca.core.contacts.describe
import androidx.compose.ui.res.stringResource

private const val MAX_RESULTS = 60

/** Choose who to write to: a contact, found by name or number, or a number typed in full. */
@Composable
internal fun NewMessageScreen(ui: MessagesUi, viewModel: MessagesViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val trimmed = query.trim()
    val digits = trimmed.count(Char::isDigit)
    val isNumber = digits >= 3 && trimmed.all { it.isDigit() || it in "+ ()-." }
    // One row per number: a contact with a mobile and a landline shows both.
    val results = remember(trimmed, ui.contacts) {
        val wanted = searchable(trimmed)
        ui.contacts.asSequence()
            .flatMap { contact -> contact.phoneNumbers.asSequence().map { contact to it } }
            .filter { (contact, number) ->
                trimmed.isEmpty() ||
                    searchable(contact.displayName).contains(wanted) ||
                    (digits >= 2 && ui.numbers.matchesDigits(number.raw, trimmed))
            }
            .take(MAX_RESULTS)
            .toList()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { SecaTopBar(title = stringResource(R.string.new_message), onBack = { viewModel.back() }, navigationIcon = SecaIcons.Close) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.to)) },
                placeholder = { Text(stringResource(R.string.name_or_number)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (isNumber) viewModel.openConversation(trimmed) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .focusRequester(focus),
            )
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                if (isNumber) {
                    item(key = "number-label") { SecaSectionLabel(stringResource(R.string.number)) }
                    item(key = "number") {
                        SecaGroupItem(index = 0, count = 1, onClick = { viewModel.openConversation(trimmed) }) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                                Icon(SecaIcons.Send, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.padding(start = 16.dp)) {
                                    Text(
                                        text = stringResource(R.string.write_to, ui.numbers.display(trimmed)),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    ui.numbers.describe(trimmed, LocalResources.current)?.let {
                                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
                if (results.isNotEmpty()) {
                    item(key = "contacts-label") { SecaSectionLabel(stringResource(if (trimmed.isEmpty()) R.string.contacts else R.string.results)) }
                    itemsIndexed(results, key = { _, (contact, number) -> "${contact.id}-${number.raw}" }) { index, (contact, number) ->
                        ContactNumberRow(contact, number, ui, index, results.size) { viewModel.openConversation(number.raw) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactNumberRow(
    contact: SecaContact,
    number: PhoneNumber,
    ui: MessagesUi,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    SecaGroupItem(index = index, count = count) {
        SecaContactRow(
            contact = contact,
            onClick = onClick,
            supportingText = ui.numbers.display(number.raw),
            tone = ui.toneOf(contact),
            photo = rememberContactThumbnail(contact.photoUri),
        )
    }
}
