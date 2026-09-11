package com.seca.core.design.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * One choice in a row of mutually exclusive choices, such as a filter. The
 * picked pill fills with the accent and morphs toward a rounded square, as M3
 * Expressive toggles do. [detail] is a secondary figure after the label.
 */
@Composable
fun SecaChoicePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    detail: String? = null,
) {
    val corner by animateDpAsState(if (selected) 12.dp else 20.dp, label = "corner")
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "container",
    )
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(corner))
            .background(container)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .padding(start = if (leading != null) 6.dp else 16.dp, end = 16.dp),
    ) {
        leading?.invoke()
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            modifier = Modifier.padding(start = if (leading != null) 8.dp else 0.dp),
        )
        detail?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = content.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}
