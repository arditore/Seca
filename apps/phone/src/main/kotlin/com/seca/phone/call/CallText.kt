package com.seca.phone.call

import android.telecom.Call
import android.telecom.TelecomManager
import com.seca.core.contacts.PhoneNumbers
import com.seca.core.model.Profile
import android.content.Context
import com.seca.phone.R

/** How to name the other side of a call, on the screen and in the notification. */
internal fun CallView.title(context: Context, numbers: PhoneNumbers): String = when {
    caller != null -> caller.name
    networkName != null -> networkName
    presentation == TelecomManager.PRESENTATION_RESTRICTED -> context.getString(R.string.hidden_number)
    presentation == TelecomManager.PRESENTATION_PAYPHONE -> context.getString(R.string.payphone)
    number.isBlank() -> context.getString(R.string.unknown_number)
    else -> numbers.display(number)
}

/** Where the call stands, in words. */
internal fun CallView.status(context: Context): String = when (state) {
    Call.STATE_RINGING, Call.STATE_SIMULATED_RINGING -> context.getString(R.string.call_status_incoming)
    Call.STATE_DIALING, Call.STATE_CONNECTING -> context.getString(R.string.call_status_dialing)
    Call.STATE_ACTIVE -> context.getString(R.string.call_status_active)
    Call.STATE_HOLDING -> context.getString(R.string.call_status_holding)
    Call.STATE_SELECT_PHONE_ACCOUNT -> context.getString(R.string.call_status_select_sim)
    Call.STATE_DISCONNECTING -> context.getString(R.string.call_status_disconnecting)
    Call.STATE_DISCONNECTED -> disconnectLabel ?: context.getString(R.string.call_status_ended)
    else -> context.getString(R.string.call_other)
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
