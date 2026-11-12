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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
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
import androidx.annotation.StringRes
import com.seca.core.design.label
import androidx.compose.ui.platform.LocalResources
import com.seca.core.contacts.describe
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import kotlin.math.abs
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import com.seca.core.design.component.SecaChoiceAction
import com.seca.core.design.component.SecaChoiceBar
import com.seca.core.design.R as DesignR
import com.seca.core.design.component.secaCopy

@Composable
internal fun HomeScreen(
    ui: PhoneUi,
    viewModel: PhoneViewModel,
    listState: LazyListState,
    onCall: (String) -> Unit,
    onCallWithSim: (String) -> Unit,
    onDeleteCalls: (List<Long>) -> Unit,
    isDefaultDialer: Boolean,
    onBecomeDefault: () -> Unit,
) {
    val context = LocalContext.current
    var bannerDismissed by remember { mutableStateOf(false) }
    var managingBlocked by remember { mutableStateOf(false) }
    if (managingBlocked) BlockedProfilesDialog(ui, viewModel, onDismiss = { managingBlocked = false })
    // The button carries its label at the top of the list and shrinks to an icon once scrolled.
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    // Chosen calls, by the group they belong to. A group is what the list shows: several calls
    // with the same person, one after another, so choosing one chooses all of them.
    var chosen by remember { mutableStateOf(emptySet<Long>()) }
    val choosing = chosen.isNotEmpty()
    BackHandler(enabled = choosing) { chosen = emptySet() }
    val actions = RowActions(
        onCall = onCall,
        onCallWithSim = onCallWithSim,
        onOpen = { viewModel.open(PhoneScreen.CallDetail(it)) },
        onDelete = onDeleteCalls,
        chosen = chosen,
        choosing = choosing,
        onChoose = { id -> chosen = if (id in chosen) chosen - id else chosen + id },
    )
    // One list, read by the pills and by the swipe, so a swipe always lands on the pill next to
    // the one lit up. Built here rather than inside the row: two lists would drift apart.
    val filters = filtersOf(ui)
    val filterRow = rememberLazyListState()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            if (choosing) {
                // A chosen row stands for every call in its group, so what the delete button
                // announces is the number of calls going, not the number of rows ticked.
                val going = ui.groups.filter { it.latest.id in chosen }.flatMap { it.ids }
                ChosenCallsBar(
                    count = chosen.size,
                    callCount = going.size,
                    onClear = { chosen = emptySet() },
                    onChooseAll = { chosen = ui.groups.map { it.latest.id }.toSet() },
                    allChosen = chosen.size == ui.groups.size,
                    // A search narrows the list to something this bar cannot see, so "all"
                    // could not be told the truth and is not offered.
                    canChooseAll = ui.query.isBlank(),
                    onDelete = {
                        // A row stands for a group of calls with the same person, so choosing
                        // one chooses all of them: deleting half a run would be a lie.
                        onDeleteCalls(going)
                        chosen = emptySet()
                    },
                )
                return@Scaffold
            }
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
                    placeholder = stringResource(R.string.search_hint),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    IconButton(onClick = { viewModel.open(PhoneScreen.Settings) }) {
                        Icon(SecaIcons.Settings, contentDescription = stringResource(R.string.settings))
                    }
                }
                if (ui.query.isBlank()) {
                    FilterRow(ui, filters, filterRow, onSelect = viewModel::setFilter)
                    // Blocked profiles stay in sight, so a quiet phone is never a mystery.
                    BlockedProfilesBanner(
                        ui = ui,
                        onManage = { managingBlocked = true },
                        onUnblock = viewModel::unblockProfiles,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        },
        bottomBar = { SecaSuiteBar(current = SecaAppIdentity.Phone, onSelect = { openSibling(context, it) }) },
        floatingActionButton = {
            if (choosing) return@Scaffold
            ExtendedFloatingActionButton(
                text = { Text(stringResource(R.string.keypad)) },
                icon = { Icon(SecaIcons.Dialpad, contentDescription = if (fabExpanded) null else stringResource(R.string.keypad)) },
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
                    modifier = Modifier.swipeBetween(filters, ui.filter, viewModel::setFilter),
                )
            }
        }
    }
}

/** What a history row can do; gathered so every row gets the same. */
private class RowActions(
    val onCall: (String) -> Unit,
    val onCallWithSim: (String) -> Unit,
    val onOpen: (String) -> Unit,
    val onDelete: (List<Long>) -> Unit,
    /** The groups chosen, by the call that stands for each of them. */
    val chosen: Set<Long> = emptySet(),
    val choosing: Boolean = false,
    val onChoose: (Long) -> Unit = {},
)

/** The bar that stands in for the search field while calls are chosen. */
@Composable
private fun ChosenCallsBar(
    count: Int,
    callCount: Int,
    onClear: () -> Unit,
    onChooseAll: () -> Unit,
    allChosen: Boolean,
    canChooseAll: Boolean,
    onDelete: () -> Unit,
) = SecaChoiceBar(
    count = count,
    onClear = onClear,
    onChooseAll = onChooseAll.takeIf { canChooseAll },
    allChosen = allChosen,
    actions = listOf(
        SecaChoiceAction(
            icon = SecaIcons.Delete,
            label = pluralStringResource(R.plurals.delete_calls, callCount, callCount),
            onClick = onDelete,
        ),
    ),
)

/** The history's filters, in the order they are offered: how calls went, then the profiles. */
private fun filtersOf(ui: PhoneUi): List<CallFilter> =
    CallFilter.ByType + if (ui.profiles.connected && ui.profiles.profiles.size > 1) {
        ui.profiles.profiles.map { CallFilter.ByProfile(it.id) }
    } else {
        emptyList()
    }

/** The history's filters: how calls went, then — with more than one — the profiles. */
@Composable
private fun FilterRow(
    ui: PhoneUi,
    filters: List<CallFilter>,
    state: LazyListState,
    onSelect: (CallFilter) -> Unit,
) {
    // Chosen by a swipe, the pill may be off the side of the screen; it is brought back so the
    // row always shows where you now are.
    val chosen = filters.indexOf(ui.filter)
    LaunchedEffect(chosen) { if (chosen >= 0) state.animateScrollToItem(chosen) }
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 12.dp),
    ) {
        items(filters, key = { it.toString() }) { filter ->
            val profile = (filter as? CallFilter.ByProfile)?.let { byProfile ->
                ui.profiles.profiles.firstOrNull { it.id == byProfile.profileId }
            }
            SecaChoicePill(
                label = profile?.label() ?: stringResource(filterLabel(filter)),
                selected = ui.filter == filter,
                onClick = { onSelect(filter) },
                leading = if (profile == null) {
                    null
                } else {
                    { SecaProfileBadge(profile.label(), tone = ui.profiles.toneOf(profile), size = 28.dp) }
                },
            )
        }
    }
}

@StringRes
private fun filterLabel(filter: CallFilter): Int = when (filter) {
    CallFilter.All -> R.string.filter_all
    CallFilter.Missed -> R.string.filter_missed
    CallFilter.Incoming -> R.string.filter_incoming
    CallFilter.Outgoing -> R.string.filter_outgoing
    CallFilter.Rejected -> R.string.filter_rejected
    CallFilter.Voicemail -> R.string.filter_voicemail
    is CallFilter.ByProfile -> R.string.filter_profile
}

@Composable
private fun Recents(
    ui: PhoneUi,
    listState: LazyListState,
    actions: RowActions,
    showBanner: Boolean,
    onBecomeDefault: () -> Unit,
    onDismissBanner: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!ui.loaded) return
    val favorites = remember(ui.contacts) { ui.contacts.filter { it.isFavorite && it.phoneNumbers.isNotEmpty() } }
    val showFavorites = favorites.isNotEmpty() && ui.filter == CallFilter.All
    if (ui.groups.isEmpty() && !showFavorites) {
        // The swipe has to work here too: a filter with nothing under it is exactly where
        // someone wants to move on to the next one.
        Column(modifier.fillMaxSize()) {
            if (showBanner) DefaultDialerBanner(onBecomeDefault, onDismissBanner)
            EmptyHistory(ui.filter)
        }
        return
    }
    val context = LocalContext.current
    val byDay = remember(ui.groups) { ui.groups.groupBy { dayOf(it.latest.date) } }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        // Room at the bottom so the last call clears the floating button.
        contentPadding = PaddingValues(bottom = 112.dp),
    ) {
        if (showBanner) {
            item(key = "default-banner", contentType = "banner") { DefaultDialerBanner(onBecomeDefault, onDismissBanner) }
        }
        if (showFavorites) {
            item(key = "favorites-label", contentType = "label") { SecaSectionLabel(stringResource(R.string.favorites)) }
            item(key = "favorites", contentType = "favorites") { FavoritesRow(favorites, ui, actions.onCall) }
        }
        byDay.values.forEach { rows ->
            item(key = "day-${rows.first().latest.id}", contentType = "label") {
                SecaSectionLabel(dayLabel(context, rows.first().latest.date))
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
                    text = stringResource(R.string.default_banner_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Text(
                text = stringResource(R.string.default_banner_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onActivate) { Text(stringResource(R.string.turn_on)) }
                TextButton(onClick = onDismiss, modifier = Modifier.padding(start = 8.dp)) { Text(stringResource(R.string.later)) }
            }
        }
    }
}

@Composable
private fun EmptyHistory(filter: CallFilter) {
    val (title, icon) = when (filter) {
        CallFilter.All -> R.string.empty_all to SecaIcons.Phone
        CallFilter.Missed -> R.string.empty_missed to SecaIcons.CallMissed
        CallFilter.Incoming -> R.string.empty_incoming to SecaIcons.CallReceived
        CallFilter.Outgoing -> R.string.empty_outgoing to SecaIcons.CallMade
        CallFilter.Rejected -> R.string.empty_rejected to SecaIcons.Block
        CallFilter.Voicemail -> R.string.empty_voicemail to SecaIcons.Voicemail
        is CallFilter.ByProfile -> R.string.empty_profile to SecaIcons.Label
    }
    SecaEmptyState(
        icon = icon,
        title = stringResource(title),
        description = stringResource(R.string.empty_history_hint),
    )
}

/**
 * One row of the history: who, how many times, how and when. A contact's
 * profile other than Principal is named, so "Work" calls stand out. A tap opens the call's
 * own page, which holds everything that can be done with it; holding the row down chooses it.
 */
@Composable
private fun CallRow(group: CallGroup, ui: PhoneUi, actions: RowActions) {
    val context = LocalContext.current
    val call = group.latest
    val contact = group.match?.contact
    val title = contact?.displayName ?: callerLabel(context, call, ui.numbers)
    val details = buildList {
        add(timeOf(context, call.date))
        ui.simLabels[call.accountId]?.let { add(it) }
        if (contact != null) {
            ui.profileOf(contact).takeIf { it.id != Profile.Principal.id }?.let { add(it.name) }
        } else if (call.callable) {
            ui.numbers.countryOf(call.number)?.takeIf { !it.isHome }?.let { add(it.displayName) }
        }
    }.joinToString(" · ")
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (call.id in actions.chosen) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                )
                .combinedClickable(
                    onClick = {
                        when {
                            actions.choosing -> actions.onChoose(call.id)
                            call.callable -> actions.onOpen(call.number)
                        }
                    },
                    onLongClickLabel = stringResource(R.string.choose),
                    // Holding a row down chooses it, as it does in the messages, and it says
                    // the same thing whether or not rows are chosen already. Everything one
                    // call can be put through is on its own page, which a tap opens.
                    onLongClick = { actions.onChoose(call.id) },
                )
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        ) {
            // A tick where the face was, the same as a chosen conversation in the messages:
            // the same gesture should say the same thing in both apps.
            if (call.id in actions.chosen) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                ) {
                    Icon(
                        imageVector = SecaIcons.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            } else if (contact != null) {
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
                // Two SIMs or one: with one, holding the button down can only place the same
                // call it would place anyway, under a label that says otherwise.
                val otherSim = ui.simLabels.isNotEmpty()
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .combinedClickable(
                            role = Role.Button,
                            onClick = { actions.onCall(call.number) },
                            onClickLabel = stringResource(R.string.call),
                            onLongClickLabel = stringResource(R.string.call_with_other_sim).takeIf { otherSim },
                            onLongClick = { actions.onCallWithSim(call.number) }.takeIf { otherSim },
                        ),
                ) {
                    Icon(
                        imageVector = SecaIcons.Phone,
                        contentDescription = stringResource(R.string.call_named, title),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
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
                    .clickable(onClickLabel = stringResource(R.string.call)) { onCall(contact.phoneNumbers.first().raw) }
                    .padding(vertical = 8.dp),
            ) {
                SecaAvatar(
                    initials = contact.initials,
                    photoUri = contact.photoUri,
                    size = 64.dp,
                    tone = ui.toneOf(contact),
                    seed = contact.displayName,
                    expressive = true,
                    avatarShape = ui.shapeOf(contact),
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
    val context = LocalContext.current
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
            title = stringResource(R.string.no_results),
            description = stringResource(R.string.nothing_matches, query),
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 112.dp)) {
        if (isNumber) {
            item(key = "number-label", contentType = "label") { SecaSectionLabel(stringResource(R.string.number)) }
            item(key = "number") {
                SecaGroupItem(index = 0, count = 1, onClick = { actions.onCall(query) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                        Icon(SecaIcons.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(
                                text = stringResource(R.string.call_named, ui.numbers.display(query)),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            ui.numbers.describe(query, LocalResources.current)?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        if (contacts.isNotEmpty()) {
            item(key = "contacts-label", contentType = "label") { SecaSectionLabel(stringResource(R.string.contacts)) }
            itemsIndexed(contacts, key = { _, contact -> "contact-${contact.id}" }, contentType = { _, _ -> "contact" }) { index, contact ->
                val number = contact.phoneNumbers.first().raw
                val copyLabel = stringResource(DesignR.string.design_copy)
                val numberLabel = stringResource(R.string.number)
                SecaGroupItem(index = index, count = contacts.size) {
                    SecaContactRow(
                        contact = contact,
                        onClick = { actions.onOpen(number) },
                        supportingText = ui.numbers.display(number),
                        tone = ui.toneOf(contact),
                        photo = rememberContactThumbnail(contact.photoUri),
                        // Nothing is chosen in this list, so holding a row is free to copy the
                        // number it shows, as it does in Seca Contacts (#24).
                        onLongClick = { secaCopy(context, numberLabel, number) },
                        longClickLabel = copyLabel,
                    )
                }
            }
        }
        if (calls.isNotEmpty()) {
            item(key = "calls-label", contentType = "label") { SecaSectionLabel(stringResource(R.string.history)) }
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

/**
 * Moves to the filter beside this one when the history is dragged sideways.
 *
 * Asked for by someone checking their calls one-handed and in a hurry: the pills sit at the top
 * of the screen, which is the far end of a thumb. The list scrolls up and down as before — a
 * drag only counts once it has clearly gone sideways and far enough to mean it.
 *
 * It does not wrap around. Running off the end of a list of filters tells you nothing about
 * where you are; stopping at the end does.
 */
@Composable
private fun Modifier.swipeBetween(
    filters: List<CallFilter>,
    current: CallFilter,
    onSelect: (CallFilter) -> Unit,
): Modifier {
    val travel = with(LocalDensity.current) { SWIPE_TRAVEL.toPx() }
    // Dragged leftwards, the next filter comes in from the right, as a page would. Mirrored
    // where the language is written right to left.
    val forward = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
    var moved by remember { mutableFloatStateOf(0f) }
    // Draggable rather than a gesture detector of our own: the history is a scrolling list, and
    // this is the machinery the list itself uses, so the two arbitrate properly and whichever
    // direction leaves the slop first wins. The favourites row inside it goes on scrolling
    // sideways on its own, which is the behaviour to keep.
    return draggable(
        state = rememberDraggableState { moved += it },
        orientation = Orientation.Horizontal,
        onDragStarted = { moved = 0f },
        onDragStopped = {
            if (abs(moved) >= travel) {
                val here = filters.indexOf(current)
                val there = here + if (moved < 0) forward else -forward
                if (here >= 0 && there in filters.indices) onSelect(filters[there])
            }
        },
    )
}

/** How far sideways a drag must go before it changes the filter. */
private val SWIPE_TRAVEL = 72.dp
