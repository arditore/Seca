package com.seca.core.design.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.seca.core.design.SecaAppIdentity
import com.seca.core.design.SecaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SecaAvatarTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `shows initials when no photo is available`() {
        composeRule.setContent {
            SecaTheme(SecaAppIdentity.Contacts, dynamicColor = false) {
                SecaAvatar(initials = "CD", photoUri = null)
            }
        }
        composeRule.onNodeWithText("CD").assertIsDisplayed()
    }
}
