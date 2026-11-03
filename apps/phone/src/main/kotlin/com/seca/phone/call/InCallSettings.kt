package com.seca.phone.call

import android.content.Context
import androidx.core.content.edit

/**
 * What the owner chose about the call screen itself.
 *
 * Both are on by default, which is what a phone does out of the box: the
 * buttons are named, and the screen goes dark against an ear. Someone who
 * knows the icons can take the labels away, and someone whose sensor misreads
 * a case or a screen protector can stop the screen going dark.
 */
class InCallSettings(context: Context) {

    private val prefs = context.getSharedPreferences("seca_in_call", Context.MODE_PRIVATE)

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
    }
}
