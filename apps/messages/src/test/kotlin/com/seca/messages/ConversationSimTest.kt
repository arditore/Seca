package com.seca.messages

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The card a conversation is written from, and the microphone switch (#90, #91).
 *
 * Both are kept on the phone and nowhere else, and both have a default that has to be the
 * quiet one: no card chosen means the phone decides, as it always did, and a suite that
 * silently stopped offering the microphone would be a feature disappearing on its own.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConversationSimTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `a conversation starts with no card of its own`() {
        assertNull(ConversationPrefs(context).sim(threadId = 11))
    }

    @Test
    fun `the card is remembered for that conversation alone`() {
        val prefs = ConversationPrefs(context)
        prefs.setSim(threadId = 11, subscriptionId = 3)
        assertEquals(3, prefs.sim(threadId = 11))
        assertNull(prefs.sim(threadId = 12))
    }

    @Test
    fun `handing the choice back to the phone forgets it`() {
        val prefs = ConversationPrefs(context)
        prefs.setSim(threadId = 11, subscriptionId = 3)
        prefs.setSim(threadId = 11, subscriptionId = null)
        assertNull(prefs.sim(threadId = 11))
    }

    @Test
    fun `the microphone is offered until somebody says otherwise`() {
        assertTrue(VoiceButtonSetting.enabled(context))
        VoiceButtonSetting.setEnabled(context, false)
        assertEquals(false, VoiceButtonSetting.enabled(context))
        VoiceButtonSetting.setEnabled(context, true)
        assertTrue(VoiceButtonSetting.enabled(context))
    }
}
