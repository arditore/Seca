package com.seca.core.design.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.seca.core.design.SecaAppIdentity
import com.seca.core.design.SecaTheme
import com.seca.core.model.PhoneNumber
import com.seca.core.model.SecaContact
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SecaContactRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val camille = SecaContact(
        id = 1L,
        displayName = "Camille Durand",
        phoneNumbers = listOf(PhoneNumber("06 12 34 56 78")),
        isFavorite = false,
        photoUri = null,
    )

    @Test
    fun `shows the display name and first number`() {
        composeRule.setContent {
            SecaTheme(SecaAppIdentity.Contacts) {
                SecaContactRow(contact = camille, onClick = {})
            }
        }
        composeRule.onNodeWithText("Camille Durand").assertIsDisplayed()
        composeRule.onNodeWithText("06 12 34 56 78").assertIsDisplayed()
    }

    @Test
    fun `shows only the name when the contact has no phone number`() {
        // Contacts with no number are routine — email-only entries, import
        // artefacts. This covers the row's only real branch.
        val numberless = camille.copy(phoneNumbers = emptyList())
        composeRule.setContent {
            SecaTheme(SecaAppIdentity.Contacts) {
                SecaContactRow(contact = numberless, onClick = {})
            }
        }
        composeRule.onNodeWithText("Camille Durand").assertIsDisplayed()
        composeRule.onNodeWithText("06 12 34 56 78").assertDoesNotExist()
    }

    @Test
    fun `invokes onClick when tapped`() {
        var clicked = false
        composeRule.setContent {
            SecaTheme(SecaAppIdentity.Contacts) {
                SecaContactRow(contact = camille, onClick = { clicked = true })
            }
        }
        composeRule.onNodeWithText("Camille Durand").performClick()
        assertTrue(clicked)
    }
}
