package com.seca.core.design.component

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClickLabel = longClickLabel,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SecaAvatar(
            initials = contact.initials,
            photoUri = contact.photoUri,
            size = 44.dp,
            tone = tone,
            seed = contact.displayName,
            photo = photo,
        )
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
            supportingText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
