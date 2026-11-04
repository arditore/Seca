package com.seca.phone.call

import android.content.Context
import androidx.core.content.edit
import com.seca.phone.R

/**
 * What the owner chose about the call screen itself.
 *
 * The two switches are on by default, which is what a phone does out of the
 * box: the buttons are named, and the screen goes dark against an ear. Someone
 * who knows the icons can take the labels away, and someone whose sensor
 * misreads a case or a screen protector can stop the screen going dark.
 */
class InCallSettings(context: Context) {

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("seca_in_call", Context.MODE_PRIVATE)

    /**
     * The four ready-made texts for declining a call with a message.
     *
     * Android keeps no shared list of these: a phone's own list belongs to the app that came
     * with it, in its own storage, and no other app may read it. So a Seca user asking for
     * their manufacturer's four sentences could only be given their own four instead — which
     * is what this is. Left untouched they follow the phone's language; once written they are
     * the owner's words and stay as typed.
     */
    var replies: List<String>
        get() = Defaults.mapIndexed { index, fallback ->
            prefs.getString("$KEY_REPLY$index", null)?.takeIf(String::isNotBlank) ?: app.getString(fallback)
        }
        set(value) = prefs.edit {
            Defaults.indices.forEach { index ->
                // An emptied line falls back to the shipped sentence rather than leaving a
                // blank row in the list: four choices, always.
                putString("$KEY_REPLY$index", value.getOrNull(index)?.trim()?.takeIf(String::isNotEmpty))
            }
        }

    /** What the replies say before anyone changes them, in the phone's language. */
    val defaultReplies: List<String> get() = Defaults.map(app::getString)

    /** Whether the buttons carry their name under the icon. */
    var showLabels: Boolean
        get() = prefs.getBoolean(KEY_LABELS, true)
        set(value) = prefs.edit { putBoolean(KEY_LABELS, value) }

    /** Whether the screen goes dark while the phone is held against an ear. */
    var proximity: Boolean
        get() = prefs.getBoolean(KEY_PROXIMITY, true)
        set(value) = prefs.edit { putBoolean(KEY_PROXIMITY, value) }

    private companion object {
        const val KEY_LABELS = "show_labels"
        const val KEY_PROXIMITY = "proximity"
        const val KEY_REPLY = "reply_"

        val Defaults = listOf(
            R.string.quick_reply_call_back,
            R.string.quick_reply_cant_talk,
            R.string.quick_reply_meeting,
            R.string.quick_reply_text_me,
        )
    }
}
