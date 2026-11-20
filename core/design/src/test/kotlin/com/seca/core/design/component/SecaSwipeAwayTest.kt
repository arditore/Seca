package com.seca.core.design.component

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.seca.core.design.SecaAppIdentity
import com.seca.core.design.SecaIcons
import com.seca.core.design.SecaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What a swipe has to be before it acts (#92).
 *
 * Somebody scrolling a list of conversations with a thumb reported archiving one by accident,
 * often. A thumb travelling up a screen draws an arc, and the end of that arc is a short, fast
 * sideways flick: short enough to be nothing, fast enough for a gesture that also listens to
 * speed. So distance is the only thing that counts here, and these tests are the statement of
 * that.
 */
@RunWith(RobolectricTestRunner::class)
class SecaSwipeAwayTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var archived = 0
    private var deleted = 0

    private fun row() {
        composeRule.setContent {
            SecaTheme(SecaAppIdentity.Messages) {
                SecaSwipeAway(
                    toStart = SwipeAction(
                        onAct = { archived++ },
                        icon = SecaIcons.Archive,
                        label = "Archive",
                    ),
                    toEnd = SwipeAction(
                        onAct = { deleted++ },
                        icon = SecaIcons.Delete,
                        label = "Delete",
                        destructive = true,
                    ),
                    modifier = Modifier.width(320.dp).height(72.dp).testTag("row"),
                ) {
                    Text("A conversation")
                }
            }
        }
    }

    @Test
    fun `a short fast flick leaves the row alone`() {
        row()
        composeRule.onNodeWithTag("row").performTouchInput {
            // A tenth of the width, thrown: the tail of a scrolling thumb.
            swipeLeft(startX = width * 0.9f, endX = width * 0.8f, durationMillis = 40)
        }
        composeRule.waitForIdle()
        assertEquals(0, archived)
    }

    @Test
    fun `a short fast flick the other way leaves the row alone`() {
        row()
        composeRule.onNodeWithTag("row").performTouchInput {
            swipeRight(startX = width * 0.1f, endX = width * 0.2f, durationMillis = 40)
        }
        composeRule.waitForIdle()
        assertEquals(0, deleted)
    }

    @Test
    fun `carrying the row most of the way across archives it`() {
        row()
        composeRule.onNodeWithTag("row").performTouchInput {
            swipeLeft(startX = width - 1f, endX = width * 0.1f, durationMillis = 300)
        }
        composeRule.waitForIdle()
        assertEquals(1, archived)
    }

    @Test
    fun `carrying the row most of the way the other side asks to delete`() {
        row()
        composeRule.onNodeWithTag("row").performTouchInput {
            swipeRight(startX = 1f, endX = width * 0.9f, durationMillis = 300)
        }
        composeRule.waitForIdle()
        assertEquals(1, deleted)
    }
}
