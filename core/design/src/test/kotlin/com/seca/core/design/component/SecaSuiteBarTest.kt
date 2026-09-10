package com.seca.core.design.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.seca.core.design.SecaAppIdentity
import com.seca.core.design.SecaPalette
import com.seca.core.design.SecaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SecaSuiteBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `shows all three apps in French`() {
        composeRule.setContent {
            SecaTheme(SecaAppIdentity.Contacts, SecaPalette.Ocean, darkTheme = false) {
                SecaSuiteBar(current = SecaAppIdentity.Contacts, onSelect = {})
            }
        }
        composeRule.onNodeWithText("Contacts").assertIsDisplayed()
        composeRule.onNodeWithText("Téléphone").assertIsDisplayed()
        composeRule.onNodeWithText("Messages").assertIsDisplayed()
    }

    @Test
    fun `reports the app the user tapped`() {
        var chosen: SecaAppIdentity? = null
        composeRule.setContent {
            SecaTheme(SecaAppIdentity.Contacts, SecaPalette.Ocean, darkTheme = false) {
                SecaSuiteBar(current = SecaAppIdentity.Contacts, onSelect = { chosen = it })
            }
        }
        composeRule.onNodeWithText("Téléphone").performClick()
        assertEquals(SecaAppIdentity.Phone, chosen)
    }
}
