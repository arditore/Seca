package com.seca.core.design

/**
 * What a palette is, to the colour machinery: a base hue, and how colourful it is.
 *
 * The four named palettes were always only this, so a palette the owner mixes themselves is
 * the same thing with the numbers chosen by hand (#28). Everything downstream — each app's
 * hue step, the lightnesses the contrast was checked at, the shared neutrals — is untouched.
 */
interface SecaPaletteSpec {
    /** Where on the colour wheel, in degrees. Seca Contacts sits here; the siblings step on. */
    val baseHue: Float

    /** How much colour, from 0 for grey to 1 for as much as the scheme allows. */
    val chroma: Float
}

/** A palette the owner mixed: any hue, any amount of colour (#28). */
data class SecaCustomPalette(
    override val baseHue: Float,
    override val chroma: Float,
) : SecaPaletteSpec

/**
 * A palette as it is written down — one of the four by name, or a mixed one by its numbers.
 *
 * Kept as text because that is what travels between the three apps and into a backup, and
 * because a name is readable in both: `Ocean`, or `mix:214:0.80`.
 */
fun SecaPaletteSpec.stored(): String = when (this) {
    is SecaPalette -> name
    else -> "$MIX$baseHue:$chroma"
}

/**
 * Reads one back. Null for nothing written, which means the wallpaper's own colours.
 *
 * Anything unreadable — written by a later version, or truncated — also comes back null rather
 * than as a colour nobody chose.
 */
fun secaPaletteOf(stored: String?): SecaPaletteSpec? {
    val text = stored?.takeIf { it.isNotBlank() } ?: return null
    if (!text.startsWith(MIX)) return SecaPalette.entries.firstOrNull { it.name == text }
    val parts = text.removePrefix(MIX).split(":")
    if (parts.size != 2) return null
    val hue = parts[0].toFloatOrNull() ?: return null
    val chroma = parts[1].toFloatOrNull() ?: return null
    return SecaCustomPalette(
        baseHue = hue.coerceIn(0f, HUE_MAX),
        chroma = chroma.coerceIn(0f, 1f),
    )
}

/** As far round the wheel as a hue goes. */
const val HUE_MAX: Float = 360f

private const val MIX = "mix:"
