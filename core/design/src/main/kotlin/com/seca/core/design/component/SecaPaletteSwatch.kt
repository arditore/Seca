package com.seca.core.design.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.seca.core.design.SecaAppIdentity
import com.seca.core.design.SecaIcons
import com.seca.core.design.SecaPalette
import com.seca.core.design.color.darkSchemeFor
import com.seca.core.design.color.lightSchemeFor
import androidx.compose.ui.res.stringResource
import com.seca.core.design.R

/**
 * One colour choice, drawn like Android's own wallpaper-and-style swatches: a
 * disc split between the colours it gives. For a Seca [palette] those are the
 * three apps' accents; `null` stands for the wallpaper colours. The tile
 * morphs from a rounded square into a circle once picked.
 */
@Composable
fun SecaPaletteSwatch(
    palette: SecaPalette?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    val colors = swatchColors(palette)
    val corner by animateDpAsState(if (selected) size / 2 else 16.dp, label = "corner")
    val tile by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "tile",
    )
    Column(
        modifier = modifier.selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(corner))
                .background(tile),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(size * 0.64f)) {
                drawArc(colors[0], startAngle = 180f, sweepAngle = 180f, useCenter = true)
                drawArc(colors[1], startAngle = 90f, sweepAngle = 90f, useCenter = true)
                drawArc(colors[2], startAngle = 0f, sweepAngle = 90f, useCenter = true)
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        SecaIcons.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Text(
            text = stringResource(palette?.label ?: R.string.design_palette_wallpaper),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun swatchColors(palette: SecaPalette?): List<Color> {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    return remember(palette, dark) {
        if (palette == null) {
            val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            listOf(scheme.primary, scheme.secondary, scheme.tertiary)
        } else {
            SecaAppIdentity.entries.map { identity ->
                val scheme = if (dark) darkSchemeFor(palette, identity) else lightSchemeFor(palette, identity)
                scheme.primary
            }
        }
    }
}
