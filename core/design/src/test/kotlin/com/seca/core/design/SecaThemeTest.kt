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
    fun `every app identity gets a distinct accent`() {
        // Captured in a single setContent: ComposeContentTestRule allows
        // setContent only once per test.
        val primaries = mutableMapOf<SecaAppIdentity, Color>()
        composeRule.setContent {
            SecaAppIdentity.entries.forEach { identity ->
                SecaTheme(identity = identity, darkTheme = false, dynamicColor = false) {
                    primaries[identity] = MaterialTheme.colorScheme.primary
                    Text("probe-${identity.name}")
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(SecaAppIdentity.entries.size, primaries.values.toSet().size)
    }

    @Test
    fun `dark theme differs from light theme`() {
        var light = Color.Unspecified
        var dark = Color.Unspecified
        composeRule.setContent {
            SecaTheme(SecaAppIdentity.Contacts, darkTheme = false, dynamicColor = false) {
                light = MaterialTheme.colorScheme.surface
                Text("light")
            }
            SecaTheme(SecaAppIdentity.Contacts, darkTheme = true, dynamicColor = false) {
                dark = MaterialTheme.colorScheme.surface
                Text("dark")
            }
        }
        composeRule.waitForIdle()
        assertNotEquals(light, dark)
    }

    @Test
    fun `container roles follow the app accent rather than Material defaults`() {
        // Guards the gap that made every identity share Material's baseline
        // purple container: SecaAvatar paints itself with primaryContainer.
        val containers = mutableMapOf<SecaAppIdentity, Color>()
        composeRule.setContent {
            SecaAppIdentity.entries.forEach { identity ->
                SecaTheme(identity = identity, darkTheme = false, dynamicColor = false) {
                    containers[identity] = MaterialTheme.colorScheme.primaryContainer
                    Text("container-${identity.name}")
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(SecaAppIdentity.entries.size, containers.values.toSet().size)
    }

    @Test
    fun `theme wires in the Seca shape and type scales`() {
        // Guards the constraint that no app gets Material defaults by accident.
        var shapes: Shapes? = null
        var typography: Typography? = null
        composeRule.setContent {
            SecaTheme(SecaAppIdentity.Contacts, darkTheme = false, dynamicColor = false) {
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
