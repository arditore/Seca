package com.seca.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.seca.core.design.color.darkSchemeFor
import com.seca.core.design.color.lightSchemeFor

/**
 * The single entry point for Seca visuals.
 *
 * No app module defines its own colours, shapes or typography; they all wrap
 * their content in this.
 *
 * [darkTheme] follows the system and is never toggled inside an app — the
 * parameter exists so tests can assert both themes. [palette] is the one
 * visual choice a user gets; each [identity] shifts its hue so the three apps
 * stay distinguishable whichever palette is picked.
 */
@Composable
fun SecaTheme(
    identity: SecaAppIdentity,
    palette: SecaPalette = SecaPalette.Ocean,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme =
        if (darkTheme) darkSchemeFor(palette, identity) else lightSchemeFor(palette, identity)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SecaTypography,
        shapes = SecaShapes,
        content = content,
    )
}
