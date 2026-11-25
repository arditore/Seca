package com.seca.core.design.component

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.seca.core.design.SecaIcons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seca.core.model.SecaContact

/**
 * One contact in a list: avatar in the accent family [tone], name, and a
 * supporting line — the first number unless the app passes something better,
 * such as a formatted one.
 */
@Composable
fun SecaContactRow(
    contact: SecaContact,
    onClick: () -> Unit,
    /**
     * The second line. No default: a number belongs on the screen spaced out for reading, and
     * which spacing that is depends on the region, which this row cannot know. It used to
     * default to the number as the address book stores it, and the one screen that let it
     * showed +33612345678 where every other screen showed 06 12 34 56 78.
     */
    supportingText: String?,
    modifier: Modifier = Modifier,
    tone: Int = 0,
    photo: ImageBitmap? = null,
    /** What holding the row down does, where there is no set of rows to be choosing from. */
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null,
    /** Whether this row is one of those being chosen (#88). */
    chosen: Boolean = false,
) {
    val bottomText = remember(contact.organization, supportingText) {
        listOfNotNull(contact.organization, supportingText)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (chosen) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClickLabel = longClickLabel,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A chosen row says so where the face was, rather than beside it: a tick in the
        // margin is read as something to tick, and this is already done.
        if (chosen) {
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
        } else {
            SecaAvatar(
                initials = contact.initials,
                photoUri = contact.photoUri,
                size = 44.dp,
                tone = tone,
                seed = contact.displayName,
                photo = photo,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(
                text = contact.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (bottomText.isNotEmpty()) {
                Text(
                    text = bottomText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
