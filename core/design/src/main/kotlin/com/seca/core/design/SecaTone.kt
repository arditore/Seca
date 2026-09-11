package com.seca.core.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The colours of the accent family at [index] — primary, tertiary, secondary,
 * then round again — as the fill and the colour drawn on it.
 *
 * Seca Contacts gives each profile the family at its position, shared by the
 * profile's badge ([strong] colours) and its contacts' avatars (the softer
 * container colours), so a colour always means the same profile.
 */
@Composable
fun secaToneColors(index: Int, strong: Boolean = false): Pair<Color, Color> {
    val colors = MaterialTheme.colorScheme
    return when (Math.floorMod(index, 3)) {
        0 -> if (strong) colors.primary to colors.onPrimary else colors.primaryContainer to colors.onPrimaryContainer
        1 -> if (strong) colors.tertiary to colors.onTertiary else colors.tertiaryContainer to colors.onTertiaryContainer
        else -> if (strong) colors.secondary to colors.onSecondary else colors.secondaryContainer to colors.onSecondaryContainer
    }
}
