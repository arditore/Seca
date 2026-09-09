package com.seca.catalog

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CatalogScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders a sample contact row for the selected identity`() {
        composeRule.setContent { CatalogScreen() }
        composeRule.onNodeWithText("Camille Durand").assertIsDisplayed()
    }

    @Test
    fun `renders the empty state sample`() {
        composeRule.setContent { CatalogScreen() }
        composeRule.onNodeWithText("Aucun contact").assertIsDisplayed()
    }
}
