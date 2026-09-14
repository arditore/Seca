package com.seca.phone.screening

import android.net.Uri
import android.provider.ContactsContract.PhoneLookup
import android.telecom.Call
import android.telecom.CallScreeningService
import com.seca.core.contacts.PhoneNumbers
import com.seca.core.contacts.SharedProfilesClient
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread

/**
 * Decides, before the phone rings, what happens to an incoming call:
 * contacts of a profile the owner blocked are held back, telemarketing is
 * turned away, unknown callers ring silently when asked, and everything else
 * rings as usual. It all happens on the phone. Android only asks the default
 * phone app.
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
        val contacts = if (raw.isBlank()) emptyList() else contactKeysOf(raw)
        if (contacts.isNotEmpty()) {
            return if (inBlockedProfile(contacts, settings)) held(settings.blockMode) else allow
        }

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

    /**
     * Whether every contact the number belongs to sits in a blocked profile: a
     * number shared with someone still allowed keeps ringing.
     */
    private fun inBlockedProfile(lookupKeys: List<String>, settings: ScreeningSettings): Boolean {
        val blocked = settings.blockedProfiles
        if (blocked.isEmpty()) return false
        val profiles = runBlocking { SharedProfilesClient(contentResolver).load() }
        // Without Seca Contacts there are no profiles, and nothing blocked through them.
        if (!profiles.connected) return false
        return lookupKeys.all { profiles.profileForKey(it).id in blocked }
    }

    private fun held(mode: BlockMode): CallResponse = when (mode) {
        // Refused before ringing and without a missed-call notification; the history keeps it among the declined.
        BlockMode.Decline -> CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)
            .setSkipNotification(true)
            .build()
        BlockMode.Silence -> CallResponse.Builder().setSilenceCall(true).build()
    }

    /** The lookup keys of the contacts [raw] belongs to; empty for someone not in the contacts. */
    private fun contactKeysOf(raw: String): List<String> = runCatching {
        contentResolver.query(
            Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(raw)),
            arrayOf(PhoneLookup.LOOKUP_KEY),
            null,
            null,
            null,
        )?.use { c -> buildList { while (c.moveToNext()) c.getString(0)?.let(::add) } }
    }.getOrNull().orEmpty()
}
