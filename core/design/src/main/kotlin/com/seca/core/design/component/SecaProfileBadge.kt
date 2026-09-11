package com.seca.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seca.core.design.secaToneColors
import com.seca.core.model.initialsOf

/**
 * A contact profile's badge: its initial on the profile's own accent, the
 * strong version of the colour its contacts' avatars wear. [tone] is the
 * profile's position in the list.
 */
@Composable
fun SecaProfileBadge(name: String, tone: Int, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    val (container, content) = secaToneColors(tone, strong = true)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(container),
    ) {
        Text(
            text = initialsOf(name).take(1).ifEmpty { "?" },
            color = content,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = (size.value * 0.44f).sp,
                lineHeight = (size.value * 0.44f).sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}
