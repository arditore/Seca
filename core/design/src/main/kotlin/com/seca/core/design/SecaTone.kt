package com.seca.core.design

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import kotlin.math.abs

/** Text on a tone keeps at least this contrast, the WCAG level for small text. */
private const val MIN_CONTRAST = 4.5

/** A hue closer than this to another does not read as a different profile's colour. */
private const val DISTINCT_HUE_DEGREES = 40f

/** Where the tones after the first sit around the accent's hue, as far apart as the circle allows. */
private val HueOffsets = listOf(110f, -110f, 55f, -55f, 165f)

private const val ADJUST_STEPS = 20

/**
 * The colour scheme profile tones are drawn from: the palette's own, the same
 * in every app, where [MaterialTheme.colorScheme] carries each app's shifted
 * hue. Null outside [SecaTheme].
 */
internal val LocalToneScheme = staticCompositionLocalOf<ColorScheme?> { null }

/**
 * The colours of the profile at [index], as the fill and the colour drawn on
 * it: the accent for the first, the theme's tertiary colour for the second when
 * it is a hue of its own, then hues spread around the colour circle.
 *
 * Seca Contacts gives each profile the tone at its position, shared by the
 * profile's badge ([strong] colours) and its contacts' avatars (the softer
 * container colours), so a colour always means the same profile, in all three
 * apps and in every palette.
 */
@Composable
fun secaToneColors(index: Int, strong: Boolean = false): Pair<Color, Color> {
    val colors = LocalToneScheme.current ?: MaterialTheme.colorScheme
    return remember(colors, index, strong) { toneColors(colors, index, strong) }
}

private fun toneColors(colors: ColorScheme, index: Int, strong: Boolean): Pair<Color, Color> {
    val accent = if (strong) colors.primary to colors.onPrimary else colors.primaryContainer to colors.onPrimaryContainer
    if (index <= 0) return accent
    val base = hueOf(colors.primary)
    val tertiaryHue = hueOf(colors.tertiary)
    // The wallpaper's own second colour is the most harmonious choice when it has one; a palette has none.
    val tertiaryDistinct = hueDistance(base, tertiaryHue) >= DISTINCT_HUE_DEGREES
    // Each hue kept only when it stays apart from the accent, the tertiary colour and the hues before it.
    val taken = mutableListOf(base).apply { if (tertiaryDistinct) add(tertiaryHue) }
    val spread = HueOffsets.map { flattering(normalized(base + it)) }.filter { hue ->
        taken.all { hueDistance(it, hue) >= DISTINCT_HUE_DEGREES }.also { apart -> if (apart) taken += hue }
    }
    val hues: List<Float?> = (if (tertiaryDistinct) listOf<Float?>(null) else emptyList()) + spread
    if (hues.isEmpty()) return accent
    val target = hues[(index - 1) % hues.size]
        ?: return if (strong) colors.tertiary to colors.onTertiary else colors.tertiaryContainer to colors.onTertiaryContainer
    return readable(withHue(accent.first, target), withHue(accent.second, target))
}

/** Mustard and olive look dull on an avatar, and red reads as an error: those hues move to the nearest pleasant one. */
private fun flattering(hue: Float): Float = when {
    hue >= 345f -> 335f
    hue < 15f -> 30f
    hue in 45f..70f -> 35f
    hue in 70f..100f -> 125f
    else -> hue
}

private fun normalized(hue: Float): Float = (hue % 360f + 360f) % 360f

private fun hueOf(color: Color): Float = FloatArray(3).also { ColorUtils.colorToHSL(color.toArgb(), it) }[0]

private fun hueDistance(a: Float, b: Float): Float = abs(normalized(a) - normalized(b)).let { if (it > 180f) 360f - it else it }

private fun withHue(color: Color, hue: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    hsl[0] = hue
    return Color(ColorUtils.HSLToColor(hsl))
}

/** Some hues are much brighter than others at the same lightness: the fill moves away from its text until both read. */
private fun readable(fill: Color, content: Color): Pair<Color, Color> {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(fill.toArgb(), hsl)
    val contentLightness = FloatArray(3).also { ColorUtils.colorToHSL(content.toArgb(), it) }[2]
    val step = if (contentLightness > hsl[2]) -0.02f else 0.02f
    var adjusted = fill
    repeat(ADJUST_STEPS) {
        if (ColorUtils.calculateContrast(content.toArgb(), adjusted.toArgb()) >= MIN_CONTRAST) return adjusted to content
        hsl[2] = (hsl[2] + step).coerceIn(0f, 1f)
        adjusted = Color(ColorUtils.HSLToColor(hsl))
    }
    return adjusted to content
}
