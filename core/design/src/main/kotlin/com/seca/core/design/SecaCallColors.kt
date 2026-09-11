package com.seca.core.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Green for placing or answering a call, as the fill and the colour drawn on
 * it — the one convention every dialer shares, so it does not follow the
 * palette. Hanging up uses the theme's error red.
 */
@Composable
fun secaCallColors(): Pair<Color, Color> {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (dark) CallGreenDark to OnCallGreenDark else CallGreenLight to OnCallGreenLight
}

private val CallGreenLight = Color(0xFF146C2E)
private val OnCallGreenLight = Color(0xFFFFFFFF)
private val CallGreenDark = Color(0xFF6DD58C)
private val OnCallGreenDark = Color(0xFF00391A)
