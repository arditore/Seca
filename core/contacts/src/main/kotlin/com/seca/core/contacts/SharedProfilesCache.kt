package com.seca.core.contacts

import android.content.Context
import androidx.core.content.edit

/**
 * The colours Seca Contacts was last known to hold, kept by each app for its first frame.
 *
 * Asking Seca Contacts means crossing into another process, which takes a moment the screen does
 * not have: the app would draw once in Material's own dark theme and then again in black, and
 * that flash is exactly what someone who asked for a black theme notices. So each app remembers
 * the answer it was given and starts from it, then replaces it with whatever Seca Contacts says
 * a moment later — which is almost always the same thing.
 *
 * Nothing here is authoritative. Seca Contacts keeps the setting; this is a copy for the time it
 * takes to ask, and an app that has never asked simply starts on the defaults, as before.
 */
class SharedProfilesCache(context: Context) {

    private val prefs = context.getSharedPreferences("seca_suite_colours", Context.MODE_PRIVATE)

    /** The palette by name, or null for the wallpaper colours. */
    val palette: String? get() = prefs.getString(KEY_PALETTE, null)

    val black: Boolean get() = prefs.getBoolean(KEY_BLACK, false)

    /** What to start from before Seca Contacts has answered. */
    fun remembered(): SharedProfiles = SharedProfiles(palette = palette, black = black)

    fun remember(shared: SharedProfiles) {
        // An app that could not reach Seca Contacts has learned nothing: keeping its silence
        // would throw away a palette the owner really did choose.
        if (!shared.connected) return
        prefs.edit {
            if (shared.palette == null) remove(KEY_PALETTE) else putString(KEY_PALETTE, shared.palette)
            putBoolean(KEY_BLACK, shared.black)
        }
    }

    private companion object {
        const val KEY_PALETTE = "palette"
        const val KEY_BLACK = "black"
    }
}
