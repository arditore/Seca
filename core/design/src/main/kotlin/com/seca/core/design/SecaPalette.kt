package com.seca.core.design

/**
 * The accent families a user can choose between.
 *
 * Material You is deliberately not offered: deriving every role from the
 * wallpaper made all three Seca apps look identical, which defeats the
 * per-app identity. A palette instead sets a base hue; each app shifts it by
 * a fixed step so the three stay distinguishable inside every palette.
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
