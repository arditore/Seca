package com.seca.core.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SecaThemeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `every app identity gets a distinct accent within a palette`() {
        val primaries = mutableMapOf<SecaAppIdentity, Color>()
        composeRule.setContent {
            SecaAppIdentity.entries.forEach { identity ->
                SecaTheme(identity = identity, palette = SecaPalette.Ocean, darkTheme = false) {
                    primaries[identity] = MaterialTheme.colorScheme.primary
                    Text("probe-${identity.name}")
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(SecaAppIdentity.entries.size, primaries.values.toSet().size)
    }

    @Test
    fun `every palette gives the same app a distinct accent`() {
        val primaries = mutableMapOf<SecaPalette, Color>()
        composeRule.setContent {
            SecaPalette.entries.forEach { palette ->
                SecaTheme(identity = SecaAppIdentity.Contacts, palette = palette, darkTheme = false) {
                    primaries[palette] = MaterialTheme.colorScheme.primary
                    Text("palette-${palette.name}")
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(SecaPalette.entries.size, primaries.values.toSet().size)
    }

    @Test
    fun `container roles follow the app accent rather than Material defaults`() {
        val containers = mutableMapOf<SecaAppIdentity, Color>()
        composeRule.setContent {
            SecaAppIdentity.entries.forEach { identity ->
                SecaTheme(identity = identity, palette = SecaPalette.Ocean, darkTheme = false) {
                    containers[identity] = MaterialTheme.colorScheme.primaryContainer
                    Text("container-${identity.name}")
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(SecaAppIdentity.entries.size, containers.values.toSet().size)
    }

    @Test
    fun `dark theme differs from light theme`() {
        var light = Color.Unspecified
        var dark = Color.Unspecified
        composeRule.setContent {
            SecaTheme(SecaAppIdentity.Contacts, SecaPalette.Ocean, darkTheme = false) {
                light = MaterialTheme.colorScheme.surface
                Text("light")
            }
            SecaTheme(SecaAppIdentity.Contacts, SecaPalette.Ocean, darkTheme = true) {
                dark = MaterialTheme.colorScheme.surface
                Text("dark")
            }
        }
        composeRule.waitForIdle()
        assertNotEquals(light, dark)
    }

    @Test
    fun `theme wires in the Seca shape and type scales`() {
        var shapes: Shapes? = null
        var typography: Typography? = null
        composeRule.setContent {
            SecaTheme(SecaAppIdentity.Contacts, SecaPalette.Ocean, darkTheme = false) {
                shapes = MaterialTheme.shapes
                typography = MaterialTheme.typography
                Text("probe")
            }
        }
        composeRule.waitForIdle()
        assertEquals(SecaShapes, shapes)
        assertEquals(SecaTypography, typography)
    }
}
