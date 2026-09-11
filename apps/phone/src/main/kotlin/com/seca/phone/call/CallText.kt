package com.seca.phone.call

import android.telecom.Call
import android.telecom.TelecomManager
import com.seca.core.contacts.PhoneNumbers
import com.seca.core.model.Profile

/** How to name the other side of a call, on the screen and in the notification. */
internal fun CallView.title(numbers: PhoneNumbers): String = when {
    caller != null -> caller.name
    networkName != null -> networkName
    presentation == TelecomManager.PRESENTATION_RESTRICTED -> "Numéro masqué"
    presentation == TelecomManager.PRESENTATION_PAYPHONE -> "Cabine téléphonique"
    number.isBlank() -> "Numéro inconnu"
    else -> numbers.display(number)
}

/** Where the call stands, in words. */
internal fun CallView.status(): String = when (state) {
    Call.STATE_RINGING, Call.STATE_SIMULATED_RINGING -> "Appel entrant"
    Call.STATE_DIALING, Call.STATE_CONNECTING -> "Appel…"
    Call.STATE_ACTIVE -> "En communication"
    Call.STATE_HOLDING -> "En attente"
    Call.STATE_SELECT_PHONE_ACCOUNT -> "Choisissez une carte SIM"
    Call.STATE_DISCONNECTING -> "Fin de l'appel…"
    Call.STATE_DISCONNECTED -> disconnectLabel ?: "Appel terminé"
    else -> "Appel"
}

/**
 * The line under the name: the caller's Seca profile — the category asked
 * for when someone calls — and the kind of number. Only the number for
 * someone who is not a contact.
 */
internal fun CallView.subtitle(numbers: PhoneNumbers): String? = when {
    caller != null -> listOfNotNull(
        caller.profile.name.takeIf { caller.profile.id != Profile.Principal.id },
        caller.numberLabel,
    ).joinToString(" · ").ifEmpty { null }
    number.isNotBlank() && presentation == TelecomManager.PRESENTATION_ALLOWED && networkName != null -> numbers.display(number)
    else -> numbers.countryOf(number)?.takeIf { !it.isHome }?.let { "${it.flag} ${it.displayName}" }
}

/** "02:31", or "1:02:31" past an hour. */
internal fun elapsed(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val rest = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, rest) else "%02d:%02d".format(minutes, rest)
}
