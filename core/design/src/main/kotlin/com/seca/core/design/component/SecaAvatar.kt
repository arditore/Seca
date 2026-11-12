package com.seca.core.design.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seca.core.design.SecaIcons
import com.seca.core.design.secaToneColors
import com.seca.core.design.SecaAvatarShape
import com.seca.core.design.shapeFor

/**
 * A person's avatar: their [photo] when there is one, else their initials.
 *
 * The colour is the accent family [tone] (see [secaToneColors]), never a
 * random pick: Seca Contacts passes the contact's profile, so every contact of
 * a profile shares one colour and the colour says where the contact belongs.
 * [expressive] swaps the circle for a shape with corners, where the avatar is the centrepiece.
 * Which shape is [avatarShape]'s to say; by default a different one per person, picked from
 * [seed] so they always keep their own.
 *
 * [photoUri], the contacts provider's thumbnail, is accepted so call sites do
 * not have to change when it is drawn, but it has no effect yet; [photo] is an
 * image the app already decoded. It carries no description: the adjacent name
 * already identifies the person, and announcing it twice hurts screen readers.
 */
@Composable
fun SecaAvatar(
    initials: String,
    photoUri: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    tone: Int = 0,
    seed: String = initials,
    expressive: Boolean = false,
    photo: ImageBitmap? = null,
    avatarShape: SecaAvatarShape = SecaAvatarShape.Playful,
) {
    val (container, content) = secaToneColors(tone)
    val shape = if (expressive) avatarShape.shapeFor(seed) else CircleShape
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        when {
            photo != null -> Image(
                bitmap = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            initials.isBlank() -> Icon(
                SecaIcons.Contacts,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(size * 0.5f),
            )
            else -> Text(
                text = initials,
                color = content,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = (size.value * 0.38f).sp,
                    lineHeight = (size.value * 0.38f).sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.sp,
                ),
            )
        }
    }
}
