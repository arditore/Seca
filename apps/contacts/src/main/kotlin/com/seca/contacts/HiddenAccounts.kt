package com.seca.contacts

import android.content.Context
import androidx.core.content.edit
import com.seca.core.contacts.Place

/**
 * The accounts the owner has turned off, so their rows stop being drawn (#74, #64).
 *
 * A contact is not one row. Android keeps one per account that knows the person and shows them
 * aggregated under a single name, so WhatsApp writing its own copy of somebody puts their
 * number in the card twice. A user with 625 contacts counted those duplicates, could not see
 * where they came from, and worked it out himself before anybody here did.
 *
 * Hiding is not deleting, and it is on this phone only: nothing is written to the account and
 * nothing is removed from Android's address book. The switch stops Seca from reading those
 * rows, and turning it back on brings them straight back.
 *
 * Kept as "type\u0000name" because either half may be empty and both may contain anything a
 * provider felt like putting there.
 */
internal object HiddenAccounts {

    private const val PREFS = "seca_accounts"
    private const val KEY_HIDDEN = "hidden"
    private const val SEPARATOR = "\u0000"

    fun hidden(context: Context): Set<Place> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_HIDDEN, emptySet())
            .orEmpty()
            .mapNotNull(::placeOf)
            .toSet()

    fun setHidden(context: Context, account: Place, hidden: Boolean) {
        val now = hidden(context).toMutableSet()
        if (hidden) now += account else now -= account
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putStringSet(KEY_HIDDEN, now.map(::keyOf).toSet())
        }
    }

    private fun keyOf(place: Place): String = "${place.type.orEmpty()}$SEPARATOR${place.name.orEmpty()}"

    private fun placeOf(key: String): Place? {
        val at = key.indexOf(SEPARATOR)
        if (at < 0) return null
        return Place(key.take(at).ifEmpty { null }, key.substring(at + SEPARATOR.length).ifEmpty { null })
    }
}
