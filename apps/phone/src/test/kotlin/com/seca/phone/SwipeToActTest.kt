package com.seca.phone

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.seca.phone.call.actOnSwipeUp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Answering, declining and hanging up by swiping the button up (#79).
 *
 * The point of the setting is that a tap must stop working, so that is the half worth testing:
 * a control that still answers a stray touch protects nobody. The other half is the screen
 * reader, which cannot drag and keeps its own way in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
class SwipeToActTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var acted = false

    private fun button(bySwipe: Boolean) {
        composeRule.setContent {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .testTag(TAG)
                    .actOnSwipeUp("Answer", bySwipe) { acted = true }
                    .size(76.dp),
            ) {}
        }
    }

    @Test
    fun `a swipe up answers`() {
        button(bySwipe = true)
        composeRule.onNodeWithTag(TAG).performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        assertTrue("a swipe up did not act", acted)
    }

    @Test
    fun `a tap does nothing once the swipe is asked for`() {
        button(bySwipe = true)
        composeRule.onNodeWithTag(TAG).performClick()
        composeRule.waitForIdle()
        assertFalse("a tap acted, which is the thing this setting exists to prevent", acted)
    }

    @Test
    fun `a swipe too short to be meant does nothing`() {
        button(bySwipe = true)
        composeRule.onNodeWithTag(TAG).performTouchInput {
            swipeUp(startY = bottom, endY = bottom - (height / 8f))
        }
        composeRule.waitForIdle()
        assertFalse("a flick of a few pixels acted", acted)
    }

    @Test
    fun `a screen reader still has its way in`() {
        button(bySwipe = true)
        composeRule.onNodeWithTag(TAG).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertTrue("the accessibility action did not act, leaving the button unreachable", acted)
    }

    @Test
    fun `a tap answers when the swipe is not asked for`() {
        button(bySwipe = false)
        composeRule.onNodeWithTag(TAG).performClick()
        composeRule.waitForIdle()
        assertTrue("the ordinary button stopped working", acted)
    }

    private companion object {
        const val TAG = "act"
    }
}
