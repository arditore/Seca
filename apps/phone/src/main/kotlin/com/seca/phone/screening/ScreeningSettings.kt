package com.seca.phone.screening

import android.content.Context
import androidx.core.content.edit

/** How incoming calls are filtered. Telemarketing is blocked by default; unknown callers still ring. */
class ScreeningSettings(context: Context) {

    private val prefs = context.getSharedPreferences("seca_call_screening", Context.MODE_PRIVATE)

    var blockTelemarketing: Boolean
        get() = prefs.getBoolean(KEY_TELEMARKETING, true)
        set(value) = prefs.edit { putBoolean(KEY_TELEMARKETING, value) }

    var silenceUnknown: Boolean
        get() = prefs.getBoolean(KEY_UNKNOWN, false)
        set(value) = prefs.edit { putBoolean(KEY_UNKNOWN, value) }

    private companion object {
        const val KEY_TELEMARKETING = "block_telemarketing"
        const val KEY_UNKNOWN = "silence_unknown"
    }
}
