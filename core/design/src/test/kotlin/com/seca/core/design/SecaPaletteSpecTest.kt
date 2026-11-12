package com.seca.core.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * How a palette is written down.
 *
 * This text goes between the three apps and into backups, so it has to be read back as what
 * was written — including by a version that knew only the four named palettes, which is why a
 * name still reads as a name.
 */
class SecaPaletteSpecTest {

    @Test
    fun `a named palette is written as its name`() {
        assertEquals("Ocean", SecaPalette.Ocean.stored())
        assertEquals(SecaPalette.Ocean, secaPaletteOf("Ocean"))
    }

    @Test
    fun `a mixed palette comes back as the same hue and the same amount of colour`() {
        val mixed = SecaCustomPalette(baseHue = 214f, chroma = 0.8f)

        val read = secaPaletteOf(mixed.stored())

        assertEquals(214f, read?.baseHue)
        assertEquals(0.8f, read?.chroma)
    }

    @Test
    fun `nothing written means the wallpaper's own colours`() {
        assertNull(secaPaletteOf(null))
        assertNull(secaPaletteOf(""))
        assertNull(secaPaletteOf("   "))
    }

    @Test
    fun `a palette written by a later version is not guessed at`() {
        // Better the wallpaper's colours than a colour nobody chose.
        assertNull(secaPaletteOf("Sunrise"))
        assertNull(secaPaletteOf("mix:214"))
        assertNull(secaPaletteOf("mix:blue:0.8"))
    }

    @Test
    fun `a hue past the wheel and a colour past full are brought back in`() {
        val read = secaPaletteOf("mix:9000:4.5")

        assertEquals(HUE_MAX, read?.baseHue)
        assertEquals(1f, read?.chroma)
    }
}
