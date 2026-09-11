package com.seca.core.design

/**
 * The accent families a user can pick instead of the wallpaper colours.
 *
 * Seca follows Material You by default, so the apps match the rest of the
 * phone. A palette is the opt-in alternative, chosen in settings: it sets a
 * base hue and each app shifts it by a fixed step, so the three apps become
 * distinguishable at a glance.
 *
 * [label] is user-visible and therefore French.
 */
enum class SecaPalette(
    val label: String,
    internal val baseHue: Float,
    internal val chroma: Float,
) {
    Ocean("Océan", baseHue = 185f, chroma = 1f),
    Foret("Forêt", baseHue = 128f, chroma = 0.9f),
    Crepuscule("Crépuscule", baseHue = 322f, chroma = 0.95f),
    Ardoise("Ardoise", baseHue = 220f, chroma = 0.4f),
}
