package com.seca.contacts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seca.core.design.SecaIcons
import com.seca.core.design.component.SecaAvatar
import com.seca.core.design.component.SecaSectionLabel
import com.seca.core.design.component.rememberContactThumbnail
import com.seca.core.model.SecaContact

/**
 * The "Favourites" heading, with the way to arrange them beside it.
 *
 * [onArrange] is null where there is nothing to arrange, and then the heading is just a
 * heading: a button that cannot do anything reads as the app failing rather than as there
 * being only one favourite.
 */
@Composable
internal fun FavoritesLabel(onArrange: (() -> Unit)?) {
    if (onArrange == null) {
        SecaSectionLabel(stringResource(R.string.favorites))
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        SecaSectionLabel(stringResource(R.string.favorites), modifier = Modifier.weight(1f))
        IconButton(onClick = onArrange, modifier = Modifier.padding(end = 8.dp)) {
            Icon(SecaIcons.SwapVert, contentDescription = stringResource(R.string.arrange_favorites))
        }
    }
}

/**
 * Arranging the favourites, asked for in #26.
 *
 * The row runs across the screen and this list runs down it, so the arrows say first and last
 * rather than up and down, and a line above says which end is which. Dragging a face along a
 * row that scrolls is the gesture people expect and the one that fights the scrolling; two
 * buttons cannot be got wrong, and a screen reader can use them.
 *
 * Nothing is written until the owner says so: the arranging is theirs to abandon.
 */
@Composable
internal fun ArrangeFavoritesDialog(
    favorites: List<SecaContact>,
    ui: ContactsUi,
    onDismiss: () -> Unit,
    onKeep: (List<String>) -> Unit,
) {
    var order by remember(favorites) { mutableStateOf(favorites) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(SecaIcons.Star, contentDescription = null) },
        title = { Text(stringResource(R.string.arrange_favorites)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.arrange_favorites_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(
                    Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 12.dp),
                ) {
                    order.forEachIndexed { index, contact ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp),
                        ) {
                            SecaAvatar(
                                initials = contact.initials,
                                photoUri = contact.photoUri,
                                size = 36.dp,
                                tone = ui.toneOf(contact),
                                seed = contact.displayName,
                                photo = rememberContactThumbnail(contact.photoUri),
                            )
                            Text(
                                text = contact.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp),
                            )
                            // No arrow where there is nowhere to go, and the gap kept, so the
                            // other arrow stays under the one above it.
                            if (index > 0) {
                                IconButton(onClick = { order = order.moved(index, -1) }) {
                                    Icon(
                                        imageVector = SecaIcons.ArrowUp,
                                        contentDescription = stringResource(R.string.arrange_earlier, contact.displayName),
                                    )
                                }
                            } else {
                                Spacer(Modifier.size(48.dp))
                            }
                            if (index < order.size - 1) {
                                IconButton(onClick = { order = order.moved(index, 1) }) {
                                    Icon(
                                        imageVector = SecaIcons.ArrowDown,
                                        contentDescription = stringResource(R.string.arrange_later, contact.displayName),
                                    )
                                }
                            } else {
                                Spacer(Modifier.size(48.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onKeep(order.map { it.lookupKey }) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** The list with the one at [from] moved by [by] places. */
private fun List<SecaContact>.moved(from: Int, by: Int): List<SecaContact> {
    val to = (from + by).coerceIn(0, size - 1)
    if (to == from) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
