package com.seca.core.design

import androidx.annotation.StringRes

/**
 * The accent families a user can pick instead of the wallpaper colours.
 *
 * Seca follows Material You by default, so the apps match the rest of the
 * phone. A palette is the opt-in alternative, chosen in settings: it sets a
 * base hue and each app shifts it by a fixed step, so the three apps become
 * distinguishable at a glance.
 *
 * [label] names the palette on screen, in the phone's language.
 */
enum class SecaPalette(
    @StringRes val label: Int,
    internal val baseHue: Float,
    internal val chroma: Float,
) {
    Ocean(R.string.design_palette_ocean, baseHue = 185f, chroma = 1f),
    Foret(R.string.design_palette_forest, baseHue = 128f, chroma = 0.9f),
    Crepuscule(R.string.design_palette_dusk, baseHue = 322f, chroma = 0.95f),
    Ardoise(R.string.design_palette_slate, baseHue = 220f, chroma = 0.4f),
}
