package com.seca.messages.sms

import android.content.Context
import com.seca.messages.ConversationPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One conversation the sweep would file away: who it is from, and what its newest message says. */
data class SweptConversation(val threadId: Long, val address: String, val snippet: String)

/**
 * Looks through the messages already on the phone for advertising, which arriving ones are
 * checked for but older ones never were.
 *
 * The filter runs when a message is delivered, so anything that arrived before Seca was the
 * messaging app — or before the filter learned a rule — sits in the inbox for good. That is how
 * a clothing brand's "click here to unsubscribe" from last month stays there while an identical
 * one that arrives today is filed at once.
 *
 * Nothing moves on its own. The sweep is asked for, it reports what it would do, and the owner
 * says yes: a month of messages quietly rearranging themselves after an update would be a worse
 * surprise than the advertising.
 */
object SpamSweep {

    /** The conversations the rule would file, newest first. Never the owner's contacts. */
    suspend fun look(context: Context): List<SweptConversation> = withContext(Dispatchers.IO) {
        val prefs = ConversationPrefs(context)
        val alreadyFiled = prefs.spam()
        MessagesRepository(context).conversations()
            .asSequence()
            .filterNot { it.threadId in alreadyFiled }
            // A conversation taken out of the folder by hand is never put back by a sweep.
            .filterNot { prefs.isTrusted(it.threadId) }
            // Only what the owner has not answered: a conversation they have written into is
            // theirs, whatever it looks like to a rule.
            .filterNot { it.outgoing }
            // Never a verification code. A one-time code from a brand says "STOP to 38696 to
            // unsubscribe" at the bottom like any other commercial text, and burying the code
            // someone is waiting for is far worse than leaving an advertisement in the inbox.
            // The filter has always refused this on arrival; the sweep must refuse it too.
            .filterNot { OneTimeCode.find(it.snippet) != null }
            .filter { SpamFilter.isCommercial(it.address, it.snippet) }
            .filterNot { SpamFilter.isContact(context, it.address) }
            .map { SweptConversation(it.threadId, it.address, it.snippet) }
            .toList()
    }

    /** Files the conversations the owner agreed to. */
    suspend fun file(context: Context, conversations: List<SweptConversation>) {
        withContext(Dispatchers.IO) {
            val prefs = ConversationPrefs(context)
            conversations.forEach { prefs.setSpam(it.threadId, spam = true) }
        }
    }
}
