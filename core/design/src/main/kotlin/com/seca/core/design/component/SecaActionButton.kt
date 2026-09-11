package com.seca.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * A large tonal button with its label under the icon, as under a contact's
 * name. [emphasized] fills it with the accent, for the main action.
 */
@Composable
fun SecaActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasized: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val container = when {
        !enabled -> colors.surfaceContainerHigh
        emphasized -> colors.primary
        else -> colors.secondaryContainer
    }
    val content = when {
        !enabled -> colors.onSurface.copy(alpha = 0.38f)
        emphasized -> colors.onPrimary
        else -> colors.onSecondaryContainer
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(container)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content)
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
