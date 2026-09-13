package com.seca.phone.screening

import android.net.Uri
import android.provider.ContactsContract.PhoneLookup
import android.telecom.Call
import android.telecom.CallScreeningService
import com.seca.core.contacts.PhoneNumbers
import kotlin.concurrent.thread

/**
 * Decides, before the phone rings, what happens to an incoming call:
 * telemarketing is turned away, unknown callers ring silently when asked, and
 * everything else rings as usual. It all happens on the phone; a contact is
 * never filtered. Android only asks the default phone app.
 */
class SecaCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }
        // Looking the caller up in the contacts reads a database: off the main thread.
        thread(name = "seca-screening") {
            respondToCall(callDetails, decide(callDetails.handle?.schemeSpecificPart.orEmpty()))
        }
    }

    private fun decide(raw: String): CallResponse {
        val settings = ScreeningSettings(this)
        val allow = CallResponse.Builder().build()
        val known = raw.isNotBlank() && isContact(raw)
        if (known) return allow

        val e164 = if (raw.isBlank()) null else PhoneNumbers(PhoneNumbers.detectRegion(this)).toE164(raw)
        if (settings.blockTelemarketing && e164 != null && Telemarketing.isTelemarketing(e164)) {
            // Turned away without ringing; the call stays in the history as blocked.
            return CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipNotification(true)
                .build()
        }
        if (settings.silenceUnknown) {
            return CallResponse.Builder().setSilenceCall(true).build()
        }
        return allow
    }

    private fun isContact(raw: String): Boolean = runCatching {
        contentResolver.query(
            Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(raw)),
            arrayOf(PhoneLookup._ID),
            null,
            null,
            null,
        )?.use { it.count > 0 }
    }.getOrNull() == true
}
