package com.seca.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.seca.core.design.color.darkSchemeFor
import com.seca.core.design.color.lightSchemeFor
import com.seca.core.design.color.pureBlack

/**
 * The single entry point for Seca visuals.
 *
 * No app module defines its own colours, shapes or typography; they all wrap
 * their content in this.
 *
 * Colours follow the wallpaper (Material You) unless the user picked a
 * [palette] in settings; with a palette, each [identity] shifts its hue so the
 * three apps become distinguishable. [darkTheme] follows the system and is
 * never toggled inside an app — the parameter exists so tests can assert both
 * themes. [black] takes the dark backgrounds down to black, for OLED screens;
 * in the light theme it changes nothing.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SecaTheme(
    identity: SecaAppIdentity,
    palette: SecaPaletteSpec? = null,
    darkTheme: Boolean = isSystemInDarkTheme(),
    black: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        palette == null && darkTheme -> dynamicDarkColorScheme(context)
        palette == null -> dynamicLightColorScheme(context)
        darkTheme -> darkSchemeFor(palette, identity)
        else -> lightSchemeFor(palette, identity)
    }.let { if (darkTheme && black) it.pureBlack() else it }

    // Profile colours start from the palette itself rather than from each app's shifted hue,
    // so a profile wears the same colour in all three apps.
    val toneScheme = remember(palette, darkTheme, colorScheme) {
        when {
            palette == null -> colorScheme
            darkTheme -> darkSchemeFor(palette, SecaAppIdentity.Contacts)
            else -> lightSchemeFor(palette, SecaAppIdentity.Contacts)
        }
    }

    // The expressive motion scheme gives components their springy, slightly
    // overshooting movement: the other half of M3 Expressive, besides shape.
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = SecaShapes,
        typography = SecaTypography,
    ) {
        CompositionLocalProvider(
            LocalToneScheme provides toneScheme,
            LocalBlackTheme provides (darkTheme && black),
            content = content,
        )
    }
}

/**
 * Whether the black theme is on, for the few places a colour scheme cannot say it.
 *
 * Material's bars sit on `surfaceContainer`, a tinted grey, which is right in the ordinary
 * dark theme and wrong under this one: the bar at the bottom of the screen is what shows
 * behind the phone's own navigation bar, so a grey there is a grey strip across the bottom
 * of a screen that is meant to be unlit.
 */
val LocalBlackTheme = staticCompositionLocalOf { false }
