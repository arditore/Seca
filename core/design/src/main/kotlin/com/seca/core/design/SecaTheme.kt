package com.seca.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.seca.core.design.color.darkSchemeFor
import com.seca.core.design.color.lightSchemeFor

/**
 * The single entry point for Seca visuals.
 *
 * No app module defines its own colours, shapes or typography; they all
 * wrap their content in this.
 *
 * [dynamicColor] opts in to the user's Material You wallpaper palette. It
 * defaults to false: dynamic colour derives every role from the wallpaper,
 * which would make Contacts, Phone and Messages look identical and erase the
 * per-app accent that makes them recognisable as distinct members of one
 * family.
 */
@Composable
fun SecaTheme(
    identity: SecaAppIdentity,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        darkTheme -> darkSchemeFor(identity)
        else -> lightSchemeFor(identity)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SecaTypography,
        shapes = SecaShapes,
        content = content,
    )
}
