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

// Contrast: primary against onPrimary is tightest on green hues (Foret, 128 deg,
// near 5.0:1 against white at chroma 0.9). Re-check WCAG 4.5:1 after changing
// any chroma, lightness or base hue.
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

// Surface-container ladder, outline variant and inverse surfaces for the shared
// neutral family. Left unset, Material fills them from its baseline purple:
// SecaSuiteBar is a NavigationBar, which paints with surfaceContainer, and it
// rendered #211F26 — a purple grey — under a teal page.
private val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
private val SurfaceContainerLowLight = Color(0xFFF1F5F5)
private val SurfaceContainerLight = Color(0xFFEBF0F0)
private val SurfaceContainerHighLight = Color(0xFFE5EBEB)
private val SurfaceContainerHighestLight = Color(0xFFDFE5E5)
private val SurfaceDimLight = Color(0xFFD6DBDB)
private val OutlineVariantLight = Color(0xFFBFC8C9)
private val InverseSurfaceLight = Color(0xFF2B3131)
private val InverseOnSurfaceLight = Color(0xFFECF2F2)

private val SurfaceContainerLowestDark = Color(0xFF090F0F)
private val SurfaceContainerLowDark = Color(0xFF161D1D)
private val SurfaceContainerDark = Color(0xFF1A2121)
private val SurfaceContainerHighDark = Color(0xFF252B2B)
private val SurfaceContainerHighestDark = Color(0xFF2F3636)
private val SurfaceBrightDark = Color(0xFF343A3A)
private val OutlineVariantDark = Color(0xFF3F4849)
private val InverseSurfaceDark = Color(0xFFDEE4E4)
private val InverseOnSurfaceDark = Color(0xFF2B3131)

// Error stays red in every palette and every app: it must never read as an
// accent. Standard Material 3 error tones.
private val ErrorLight = Color(0xFFBA1A1A)
private val OnErrorLight = Color(0xFFFFFFFF)
private val ErrorContainerLight = Color(0xFFFFDAD6)
private val OnErrorContainerLight = Color(0xFF410002)
private val ErrorDark = Color(0xFFFFB4AB)
private val OnErrorDark = Color(0xFF690005)
private val ErrorContainerDark = Color(0xFF93000A)
private val OnErrorContainerDark = Color(0xFFFFDAD6)

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
        surfaceContainerLowest = SurfaceContainerLowestLight,
        surfaceContainerLow = SurfaceContainerLowLight,
        surfaceContainer = SurfaceContainerLight,
        surfaceContainerHigh = SurfaceContainerHighLight,
        surfaceContainerHighest = SurfaceContainerHighestLight,
        surfaceDim = SurfaceDimLight,
        surfaceBright = SurfaceLight,
        outlineVariant = OutlineVariantLight,
        inverseSurface = InverseSurfaceLight,
        inverseOnSurface = InverseOnSurfaceLight,
        inversePrimary = darkAccent(palette, identity).primary,
        scrim = Color.Black,
        error = ErrorLight,
        onError = OnErrorLight,
        errorContainer = ErrorContainerLight,
        onErrorContainer = OnErrorContainerLight,
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
        surfaceContainerLowest = SurfaceContainerLowestDark,
        surfaceContainerLow = SurfaceContainerLowDark,
        surfaceContainer = SurfaceContainerDark,
        surfaceContainerHigh = SurfaceContainerHighDark,
        surfaceContainerHighest = SurfaceContainerHighestDark,
        surfaceDim = SurfaceDark,
        surfaceBright = SurfaceBrightDark,
        outlineVariant = OutlineVariantDark,
        inverseSurface = InverseSurfaceDark,
        inverseOnSurface = InverseOnSurfaceDark,
        inversePrimary = lightAccent(palette, identity).primary,
        scrim = Color.Black,
        error = ErrorDark,
        onError = OnErrorDark,
        errorContainer = ErrorContainerDark,
        onErrorContainer = OnErrorContainerDark,
    )
}
