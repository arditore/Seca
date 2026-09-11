package com.seca.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One item of a grouped list, laid out the way M3 Expressive draws settings
 * and contact lists: the items of a group share a tonal surface, split by
 * thin gaps, and only the group's outer corners are fully rounded.
 *
 * [index] and [count] place the item in its group. Content that is itself
 * clickable gets its ripple clipped to the item's shape.
 */
@Composable
fun SecaGroupItem(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = if (index < count - 1) 2.dp else 0.dp)
            .clip(segmentShape(index, count))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        content()
    }
}

/** Large corners at the ends of a group, small ones between its items. */
fun segmentShape(index: Int, count: Int, outer: Dp = 24.dp, inner: Dp = 6.dp): Shape {
    val top = if (index == 0) outer else inner
    val bottom = if (index == count - 1) outer else inner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}
