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
import com.seca.core.design.component.rememberContactPhoto
import com.seca.core.design.label
import androidx.compose.ui.platform.LocalResources
import com.seca.core.contacts.describe
import androidx.compose.ui.res.stringResource
import android.provider.BlockedNumberContract
import android.widget.Toast
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.seca.phone.allBlocked
import com.seca.phone.blockNumbers
import com.seca.phone.unblockNumbers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Who a number belongs to, what can be done with it, and every call with them. */
@Composable
internal fun CallDetailScreen(
    number: String,
    ui: PhoneUi,
    viewModel: PhoneViewModel,
    onCall: (String) -> Unit,
    onCallWithSim: (String) -> Unit,
    onDeleteCalls: (List<Long>) -> Unit,
) {
    val context = LocalContext.current
    val match = remember(number, ui.index) { ui.index?.find(number) }
    val contact = match?.contact
    val history = remember(number, ui.calls, ui.index) { ui.historyFor(number) }
    val carrierName = remember(history) { history.firstNotNullOfOrNull { it.carrierName(context) } }
    // A contact with several numbers: each call says which one it was.
    val showNumbers = (contact?.phoneNumbers?.size ?: 0) > 1
    var menuOpen by remember { mutableStateOf(false) }
    val blockedText = stringResource(R.string.number_blocked)
    val blockFailedText = stringResource(R.string.block_failed)
    val unblockedText = stringResource(R.string.number_unblocked)
    val scope = rememberCoroutineScope()
    val blockable = remember(number) { BlockedNumberContract.canCurrentUserBlockNumbers(context) }
    // A person has a card, not a number. Blocking the mobile and leaving the landline through
    // is not what anybody meant by blocking them (#50).
    val numbers = remember(contact, number) {
        contact?.phoneNumbers?.map { it.raw }?.takeIf { it.isNotEmpty() } ?: listOf(number)
    }
    var blocked by remember(numbers) { mutableStateOf(false) }
    LaunchedEffect(numbers, blockable) {
        blocked = blockable && withContext(Dispatchers.IO) { allBlocked(context, numbers) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            SecaTopBar(title = "", onBack = { viewModel.back() }) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(SecaIcons.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.copy_number)) },
                            leadingIcon = { Icon(SecaIcons.ContentCopy, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                copyNumber(context, match?.number?.raw ?: number)
                            },
                        )
                        // Blocking is Android's own list, which only the default phone app may
                        // change. It used to be on the history row's menu; this is the page
                        // that page's menu became.
                        if (blockable) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            when {
                                                blocked -> R.string.unblock
                                                numbers.size > 1 -> R.string.block_contact
                                                else -> R.string.block_number
                                            },
                                        ),
                                    )
                                },
                                leadingIcon = { Icon(SecaIcons.Block, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    val wanted = !blocked
                                    scope.launch {
                                        val done = withContext(Dispatchers.IO) {
                                            if (wanted) {
                                                blockNumbers(context, numbers) > 0
                                            } else {
                                                unblockNumbers(context, numbers) > 0
                                            }
                                        }
                                        if (done) blocked = wanted
                                        Toast.makeText(
                                            context,
                                            when {
                                                !done -> blockFailedText
                                                wanted -> blockedText
                                                else -> unblockedText
                                            },
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                },
                            )
                        }
                        if (history.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete_this_history)) },
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
            item(key = "header") { Header(number, match, ui, carrierName) }
            item(key = "actions") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 28.dp),
                ) {
                    SecaActionButton(
                        SecaIcons.Phone,
                        stringResource(R.string.call),
                        onClick = { onCall(match?.number?.raw ?: number) },
                        modifier = Modifier.weight(1f),
                        emphasized = true,
                        onLongClick = { onCallWithSim(match?.number?.raw ?: number) },
                        longClickLabel = stringResource(R.string.call_with_other_sim),
                    )
                    SecaActionButton(SecaIcons.Messages, stringResource(R.string.message), onClick = { sms(context, number) }, modifier = Modifier.weight(1f))
                    if (contact != null) {
                        SecaActionButton(
                            SecaIcons.Contacts,
                            stringResource(R.string.contact_card),
                            onClick = { openContact(context, contact.id, contact.lookupKey) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        SecaActionButton(
                            SecaIcons.PersonAdd,
                            stringResource(R.string.add),
                            onClick = { addContact(context, number, carrierName) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            item(key = "sim") {
                SimPreferenceRow(
                    number = match?.number?.raw ?: number,
                    lookupKey = contact?.lookupKey,
                    name = contact?.displayName ?: carrierName ?: ui.numbers.display(number),
                    numbers = ui.numbers,
                )
            }
            if (history.isNotEmpty()) {
                item(key = "history-label") { SecaSectionLabel(stringResource(R.string.history)) }
                itemsIndexed(history, key = { _, call -> call.id }) { index, call ->
                    SecaGroupItem(index = index, count = history.size) { HistoryRow(call, ui, showNumbers) }
                }
            }
        }
    }
}

@Composable
private fun Header(number: String, match: NumberMatch?, ui: PhoneUi, carrierName: String?) {
    val context = LocalContext.current
    val copyLabel = stringResource(R.string.copy_number)
    val contact = match?.contact
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (contact != null) {
            SecaAvatar(
                initials = contact.initials,
                photoUri = contact.photoUri,
                size = 120.dp,
                tone = ui.toneOf(contact),
                seed = contact.displayName,
                photo = rememberContactPhoto(contact.id),
                expressive = true,
                avatarShape = ui.shapeOf(contact),
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            UnknownAvatar(modifier = Modifier.padding(top = 8.dp), size = 120.dp)
        }
        Text(
            text = contact?.displayName ?: carrierName ?: ui.numbers.display(number),
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
                SecaProfileBadge(profile.label(), tone = ui.profiles.toneOf(profile), size = 28.dp)
                Text(
                    text = profile.label(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                text = ui.numbers.display(match.number.raw),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clip(MaterialTheme.shapes.small)
                    .combinedClickable(
                        onClick = { copyNumber(context, match.number.raw) },
                        onClickLabel = copyLabel,
                        onLongClickLabel = copyLabel,
                        onLongClick = { copyNumber(context, match.number.raw) },
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        } else {
            if (carrierName != null) {
                Text(
                    text = ui.numbers.display(number),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            ui.numbers.describe(number, LocalResources.current)?.let {
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
        "${dayLabel(context, call.date)}, ${timeOf(context, call.date)}",
        ui.simLabels[call.accountId],
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
            Text(stringResource(call.type.label), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
