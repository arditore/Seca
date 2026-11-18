package com.seca.messages

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a message that just arrived should be announced, and when it would only be repeating
 * what is already on screen.
 *
 * The rule got this wrong in one direction that matters: it treated the app being in front as
 * the owner having seen the message, whatever screen they were on. On the conversation list
 * that is false — a row changes somewhere below the fold — and a user reported receiving
 * everything in silence because of it (#55).
 */
class ActiveConversationTest {

    @After
    fun reset() {
        ActiveConversation.appInFront(false)
        ActiveConversation.hide(SOMEBODY)
    }

    @Test
    fun `a message is announced when nothing of the app is on screen`() {
        assertFalse(ActiveConversation.isShown(SOMEBODY))
    }

    @Test
    fun `a message is announced while the app is in front on another screen`() {
        ActiveConversation.appInFront(true)
        assertFalse("the conversation list is not reading the message", ActiveConversation.isShown(SOMEBODY))
    }

    @Test
    fun `a message is announced while another conversation is open`() {
        ActiveConversation.appInFront(true)
        ActiveConversation.show(SOMEBODY_ELSE)
        assertFalse(ActiveConversation.isShown(SOMEBODY))
    }

    @Test
    fun `a message is not announced while its own conversation is open`() {
        ActiveConversation.appInFront(true)
        ActiveConversation.show(SOMEBODY)
        assertTrue(ActiveConversation.isShown(SOMEBODY))
    }

    @Test
    fun `a conversation left open behind another app does not swallow the notification`() {
        ActiveConversation.appInFront(true)
        ActiveConversation.show(SOMEBODY)
        ActiveConversation.appInFront(false)
        assertFalse(ActiveConversation.isShown(SOMEBODY))
    }

    @Test
    fun `a message from nobody in particular is announced`() {
        ActiveConversation.appInFront(true)
        ActiveConversation.show(SOMEBODY)
        assertFalse(ActiveConversation.isShown(null))
    }

    private companion object {
        const val SOMEBODY = "+33612345678"
        const val SOMEBODY_ELSE = "+33698765432"
    }
}
