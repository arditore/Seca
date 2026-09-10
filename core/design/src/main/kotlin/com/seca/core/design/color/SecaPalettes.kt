package com.seca.core.design.color

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.seca.core.design.SecaAppIdentity
import com.seca.core.design.SecaPalette

/** One app's accent within one palette, for one theme. */
private data class Accent(
    val primary: Color,
    val onPrimary: Color,
    val container: Color,
    val onContainer: Color,
)

/**
 * How far each app's hue sits from its palette's base.
 *
 * Large enough to read as a different colour at a glance, small enough that
 * the three still look like one family.
 */
private const val IdentityHueStep = 34f

private fun hueFor(palette: SecaPalette, identity: SecaAppIdentity): Float {
    val step = when (identity) {
        SecaAppIdentity.Contacts -> 0f
        SecaAppIdentity.Phone -> IdentityHueStep
        SecaAppIdentity.Messages -> IdentityHueStep * 2f
    }
    return (palette.baseHue + step) % 360f
}

private fun lightAccent(palette: SecaPalette, identity: SecaAppIdentity): Accent {
    val h = hueFor(palette, identity)
    val s = 0.62f * palette.chroma
    return Accent(
        primary = Color.hsl(h, s, 0.32f),
        onPrimary = Color.White,
        container = Color.hsl(h, s * 0.7f, 0.88f),
        onContainer = Color.hsl(h, s, 0.12f),
    )
}

private fun darkAccent(palette: SecaPalette, identity: SecaAppIdentity): Accent {
    val h = hueFor(palette, identity)
    val s = 0.55f * palette.chroma
    return Accent(
        primary = Color.hsl(h, s, 0.72f),
        onPrimary = Color.hsl(h, s, 0.14f),
        container = Color.hsl(h, s, 0.28f),
        onContainer = Color.hsl(h, s * 0.8f, 0.90f),
    )
}

// Shared neutral base — identical across palettes and apps, so the family
// reads as one system whatever accent the user picked.
private val SurfaceLight = Color(0xFFF7FAFA)
private val OnSurfaceLight = Color(0xFF191C1D)
private val SurfaceVariantLight = Color(0xFFDBE4E5)
private val OnSurfaceVariantLight = Color(0xFF3F4849)
private val OutlineLight = Color(0xFF6F7979)

private val SurfaceDark = Color(0xFF0E1414)
private val OnSurfaceDark = Color(0xFFE1E3E3)
private val SurfaceVariantDark = Color(0xFF3F4849)
private val OnSurfaceVariantDark = Color(0xFFBFC8C9)
private val OutlineDark = Color(0xFF899393)

internal fun lightSchemeFor(palette: SecaPalette, identity: SecaAppIdentity): ColorScheme {
    val a = lightAccent(palette, identity)
    return lightColorScheme(
        primary = a.primary,
        onPrimary = a.onPrimary,
        primaryContainer = a.container,
        onPrimaryContainer = a.onContainer,
        secondary = a.primary,
        onSecondary = a.onPrimary,
        secondaryContainer = a.container,
        onSecondaryContainer = a.onContainer,
        tertiary = a.primary,
        onTertiary = a.onPrimary,
        tertiaryContainer = a.container,
        onTertiaryContainer = a.onContainer,
        surface = SurfaceLight,
        onSurface = OnSurfaceLight,
        surfaceVariant = SurfaceVariantLight,
        onSurfaceVariant = OnSurfaceVariantLight,
        outline = OutlineLight,
        background = SurfaceLight,
        onBackground = OnSurfaceLight,
    )
}

internal fun darkSchemeFor(palette: SecaPalette, identity: SecaAppIdentity): ColorScheme {
    val a = darkAccent(palette, identity)
    return darkColorScheme(
        primary = a.primary,
        onPrimary = a.onPrimary,
        primaryContainer = a.container,
        onPrimaryContainer = a.onContainer,
        secondary = a.primary,
        onSecondary = a.onPrimary,
        secondaryContainer = a.container,
        onSecondaryContainer = a.onContainer,
        tertiary = a.primary,
        onTertiary = a.onPrimary,
        tertiaryContainer = a.container,
        onTertiaryContainer = a.onContainer,
        surface = SurfaceDark,
        onSurface = OnSurfaceDark,
        surfaceVariant = SurfaceVariantDark,
        onSurfaceVariant = OnSurfaceVariantDark,
        outline = OutlineDark,
        background = SurfaceDark,
        onBackground = OnSurfaceDark,
    )
}
