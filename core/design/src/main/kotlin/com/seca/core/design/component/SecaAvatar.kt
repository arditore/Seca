package com.seca.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seca.core.design.SecaIcons
import com.seca.core.design.secaToneColors

/**
 * A contact's avatar, rendered as initials.
 *
 * Its colour is the accent family [tone] (see [secaToneColors]), never a
 * random pick: Seca Contacts passes the contact's profile, so every contact of
 * a profile shares one colour and the colour says where the contact belongs.
 * [expressive] swaps the circle for one of the M3 Expressive shapes, picked by
 * [seed] so a contact always keeps its own, for places where the avatar is the
 * centrepiece.
 *
 * Photo rendering is NOT implemented. [photoUri] is accepted so call sites do
 * not have to change when it lands, but passing one currently has no effect —
 * no image library enters the APK until an app actually needs one. When it is
 * added, the image should carry `contentDescription = null`: the adjacent name
 * already identifies the contact, so announcing it twice hurts screen readers.
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
) {
    val (container, content) = secaToneColors(tone)
    val shape = if (expressive) expressiveShapeFor(seed) else CircleShape
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        if (initials.isBlank()) {
            Icon(SecaIcons.Contacts, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.5f))
        } else {
            Text(
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

/** A few of the M3 Expressive shapes; [seed] picks one, so a contact always keeps its own. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun expressiveShapeFor(seed: String): Shape = when (Math.floorMod(seed.hashCode(), 4)) {
    0 -> MaterialShapes.Cookie9Sided.toShape()
    1 -> MaterialShapes.Clover4Leaf.toShape()
    2 -> MaterialShapes.Sunny.toShape()
    else -> MaterialShapes.Cookie6Sided.toShape()
}
