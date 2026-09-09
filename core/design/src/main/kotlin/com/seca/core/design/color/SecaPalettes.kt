package com.seca.core.design.color

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.seca.core.design.SecaAppIdentity

private val ContactsAccent = Color(0xFF00696E)
private val ContactsAccentDark = Color(0xFF4FD8E0)
private val PhoneAccent = Color(0xFF3A5BA9)
private val PhoneAccentDark = Color(0xFFB1C5FF)
private val MessagesAccent = Color(0xFF6B4EA8)
private val MessagesAccentDark = Color(0xFFD3BCFF)

private val NeutralSurfaceLight = Color(0xFFF7FAFA)
private val NeutralSurfaceDark = Color(0xFF0E1414)
private val NeutralOnSurfaceLight = Color(0xFF191C1D)
private val NeutralOnSurfaceDark = Color(0xFFE1E3E3)

internal fun lightSchemeFor(identity: SecaAppIdentity): ColorScheme {
    val accent = when (identity) {
        SecaAppIdentity.Contacts -> ContactsAccent
        SecaAppIdentity.Phone -> PhoneAccent
        SecaAppIdentity.Messages -> MessagesAccent
    }
    return lightColorScheme(
        primary = accent,
        onPrimary = Color.White,
        surface = NeutralSurfaceLight,
        onSurface = NeutralOnSurfaceLight,
        background = NeutralSurfaceLight,
        onBackground = NeutralOnSurfaceLight,
    )
}

internal fun darkSchemeFor(identity: SecaAppIdentity): ColorScheme {
    val accent = when (identity) {
        SecaAppIdentity.Contacts -> ContactsAccentDark
        SecaAppIdentity.Phone -> PhoneAccentDark
        SecaAppIdentity.Messages -> MessagesAccentDark
    }
    return darkColorScheme(
        primary = accent,
        onPrimary = Color(0xFF00363A),
        surface = NeutralSurfaceDark,
        onSurface = NeutralOnSurfaceDark,
        background = NeutralSurfaceDark,
        onBackground = NeutralOnSurfaceDark,
    )
}
