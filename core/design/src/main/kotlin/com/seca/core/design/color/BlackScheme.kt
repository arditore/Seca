package com.seca.core.design.color

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The dark scheme with its backgrounds taken down to black.
 *
 * On an OLED screen a black pixel is an unlit pixel: the panel draws nothing
 * there, which saves power and makes the screen look bottomless in the dark.
 * Material's own dark scheme keeps a tinted grey instead, so this replaces the
 * surfaces — and only the surfaces. Accents, text and outlines stay as the
 * palette set them, so nothing loses contrast.
 */
fun ColorScheme.pureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceBright = Lifted,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Raised,
    surfaceContainer = Card,
    surfaceContainerHigh = Lifted,
    surfaceContainerHighest = Highest,
    surfaceVariant = Lifted,
    inverseSurface = Highest,
)

// Four steps out of black, close enough together that a card reads as depth
// rather than as a grey box, and far enough apart to be told apart at night.
private val Raised = Color(0xFF0A0A0C)
private val Card = Color(0xFF121214)
private val Lifted = Color(0xFF1B1B1F)
private val Highest = Color(0xFF242428)
