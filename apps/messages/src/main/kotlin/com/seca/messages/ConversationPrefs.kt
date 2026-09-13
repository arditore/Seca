package com.seca.messages

import android.content.Context
import androidx.core.content.edit

/**
 * Which conversations the owner pinned to the top or put away in the archive.
 * Kept in this app's private storage: Android's own message store knows
 * nothing of it, and nothing leaves the phone.
 */
class ConversationPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("seca_conversations", Context.MODE_PRIVATE)

    /** Pinned conversations, in the order they were pinned. */
    fun pinned(): List<Long> = ids(KEY_PINNED)

    fun archived(): Set<Long> = ids(KEY_ARCHIVED).toSet()

    /** Pinning a conversation also takes it out of the archive. */
    fun setPinned(threadId: Long, pinned: Boolean) {
        change(KEY_PINNED) { ids -> ids.filter { it != threadId } + if (pinned) listOf(threadId) else emptyList() }
        if (pinned) change(KEY_ARCHIVED) { ids -> ids.filter { it != threadId } }
    }

    /** Archiving a conversation also unpins it. */
    fun setArchived(threadId: Long, archived: Boolean) {
        change(KEY_ARCHIVED) { ids -> ids.filter { it != threadId } + if (archived) listOf(threadId) else emptyList() }
        if (archived) change(KEY_PINNED) { ids -> ids.filter { it != threadId } }
    }

    private fun ids(key: String): List<Long> =
        prefs.getString(key, null).orEmpty().split(',').mapNotNull { it.toLongOrNull() }

    private fun change(key: String, update: (List<Long>) -> List<Long>) {
        prefs.edit { putString(key, update(ids(key)).joinToString(",")) }
    }

    private companion object {
        const val KEY_PINNED = "pinned"
        const val KEY_ARCHIVED = "archived"
    }
}
