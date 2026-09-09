package com.seca.core.design.color

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.seca.core.design.SecaAppIdentity

/**
 * One app's accent, in both themes.
 *
 * Container roles are set explicitly: `lightColorScheme`/`darkColorScheme` fill
 * anything left out from Material's baseline purple, which would render every
 * identity's containers identically and defeat the per-app accent.
 */
private data class Accent(
    val primary: Color,
    val onPrimary: Color,
    val container: Color,
    val onContainer: Color,
)

private val ContactsLight = Accent(Color(0xFF00696E), Color.White, Color(0xFF9CF1F6), Color(0xFF002022))
private val ContactsDark = Accent(Color(0xFF4FD8E0), Color(0xFF00363A), Color(0xFF004F53), Color(0xFF9CF1F6))

private val PhoneLight = Accent(Color(0xFF3A5BA9), Color.White, Color(0xFFDAE2FF), Color(0xFF001A43))
private val PhoneDark = Accent(Color(0xFFB1C5FF), Color(0xFF002B75), Color(0xFF1F438F), Color(0xFFDAE2FF))

private val MessagesLight = Accent(Color(0xFF6B4EA8), Color.White, Color(0xFFEADDFF), Color(0xFF250057))
private val MessagesDark = Accent(Color(0xFFD3BCFF), Color(0xFF3A1D6E), Color(0xFF53378E), Color(0xFFEADDFF))

// Shared neutral base — identical across the three apps, so the family reads as one system.
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

private fun lightAccent(identity: SecaAppIdentity) = when (identity) {
    SecaAppIdentity.Contacts -> ContactsLight
    SecaAppIdentity.Phone -> PhoneLight
    SecaAppIdentity.Messages -> MessagesLight
}

private fun darkAccent(identity: SecaAppIdentity) = when (identity) {
    SecaAppIdentity.Contacts -> ContactsDark
    SecaAppIdentity.Phone -> PhoneDark
    SecaAppIdentity.Messages -> MessagesDark
}

internal fun lightSchemeFor(identity: SecaAppIdentity): ColorScheme {
    val a = lightAccent(identity)
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

internal fun darkSchemeFor(identity: SecaAppIdentity): ColorScheme {
    val a = darkAccent(identity)
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
