package com.seca.phone.screening

import android.content.Context
import androidx.core.content.edit

/** What happens to a call from a contact in a blocked profile. */
enum class BlockMode {
    /** Refused before it rings; it stays in the history among the declined calls. */
    Decline,

    /** Shown without ringing nor vibrating, for the owner to take or leave. */
    Silence,
}

/** How incoming calls are filtered. Telemarketing is blocked by default; unknown callers still ring. */
class ScreeningSettings(context: Context) {

    private val prefs = context.getSharedPreferences("seca_call_screening", Context.MODE_PRIVATE)

    var blockTelemarketing: Boolean
        get() = prefs.getBoolean(KEY_TELEMARKETING, true)
        set(value) = prefs.edit { putBoolean(KEY_TELEMARKETING, value) }

    var silenceUnknown: Boolean
        get() = prefs.getBoolean(KEY_UNKNOWN, false)
        set(value) = prefs.edit { putBoolean(KEY_UNKNOWN, value) }

    /** The Seca Contacts profiles whose contacts cannot call, by id, until the owner lets them again. */
    var blockedProfiles: Set<String>
        get() = prefs.getStringSet(KEY_BLOCKED_PROFILES, null)?.toSet().orEmpty()
        set(value) = prefs.edit { putStringSet(KEY_BLOCKED_PROFILES, value) }

    var blockMode: BlockMode
        get() = BlockMode.entries.firstOrNull { it.name == prefs.getString(KEY_BLOCK_MODE, null) } ?: BlockMode.Decline
        set(value) = prefs.edit { putString(KEY_BLOCK_MODE, value.name) }

    private companion object {
        const val KEY_TELEMARKETING = "block_telemarketing"
        const val KEY_UNKNOWN = "silence_unknown"
        const val KEY_BLOCKED_PROFILES = "blocked_profiles"
        const val KEY_BLOCK_MODE = "block_mode"
    }
}
