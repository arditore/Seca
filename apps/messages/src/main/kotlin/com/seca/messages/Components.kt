package com.seca.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import com.seca.core.design.SecaIcons
import com.seca.core.design.component.SecaAvatar
import com.seca.core.design.component.rememberContactThumbnail
import com.seca.core.model.SecaContact
import java.text.Normalizer

/**
 * Who a conversation is with: the contact's photo or initials in their
 * profile's colour, or a neutral disc for a number that is not a contact.
 */
@Composable
internal fun PersonAvatar(
    contact: SecaContact?,
    ui: MessagesUi,
    size: Dp,
    modifier: Modifier = Modifier,
    expressive: Boolean = false,
) {
    if (contact != null) {
        SecaAvatar(
            initials = contact.initials,
            photoUri = contact.photoUri,
            modifier = modifier,
            size = size,
            tone = ui.toneOf(contact),
            seed = contact.displayName,
            expressive = expressive,
            photo = rememberContactThumbnail(contact.photoUri),
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Icon(
                SecaIcons.Contacts,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}

private val Accents = Regex("\\p{M}+")

/** Lower-cases and strips accents, so "elo" finds "Élodie". */
internal fun searchable(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(Accents, "").lowercase()
