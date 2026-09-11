package com.seca.phone.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.OutcomeReceiver
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.PhoneLookup
import android.telecom.Call
import android.telecom.CallEndpoint
import android.telecom.CallEndpointException
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import com.seca.core.contacts.PhoneNumbers
import com.seca.core.contacts.SharedProfilesClient
import com.seca.core.model.Profile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Who is calling, when they are in the contacts: name, photo, number type and Seca profile. */
data class Caller(
    val contactId: Long,
    val lookupKey: String,
    val name: String,
    val photoUri: String?,
    val numberLabel: String?,
    val profile: Profile,
    val tone: Int,
)

/** A call as the screen shows it, rebuilt each time Telecom reports a change. */
data class CallView(
    val call: Call,
    val state: Int,
    val number: String,
    val presentation: Int,
    /** The name the network sent along, when there is one. */
    val networkName: String?,
    val incoming: Boolean,
    /** When the call was answered, or 0. */
    val connectTime: Long,
    val canHold: Boolean,
    val canMerge: Boolean,
    val canSwap: Boolean,
    /** Whether the call goes through a conference: its parts are not shown on their own. */
    val isConferencePart: Boolean,
    val wifi: Boolean,
    val hd: Boolean,
    val disconnectLabel: String?,
    /** The SIM the call uses, named only when the phone has more than one. */
    val accountLabel: String?,
    /** The SIMs to choose between, when Android asks which one should place the call. */
    val accountsToChoose: List<PhoneAccountHandle>,
    val caller: Caller?,
)

data class AudioView(
    val muted: Boolean = false,
    val endpoint: CallEndpoint? = null,
    val endpoints: List<CallEndpoint> = emptyList(),
)

/** The call the screen is about: one ringing first, then one waiting for a SIM, then the one in progress. */
fun List<CallView>.primary(): CallView? {
    val shown = filterNot { it.isConferencePart }
    return shown.firstOrNull { it.state == Call.STATE_RINGING || it.state == Call.STATE_SIMULATED_RINGING }
        ?: shown.firstOrNull { it.state == Call.STATE_SELECT_PHONE_ACCOUNT }
        ?: shown.firstOrNull { it.state in InProgress }
        ?: shown.firstOrNull { it.state == Call.STATE_HOLDING }
        ?: shown.firstOrNull()
}

private val InProgress = setOf(Call.STATE_ACTIVE, Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_PULLING_CALL)

/**
 * Every call in progress, for the in-call screen, the notifications and their
 * buttons, which all live in this process.
 *
 * Telecom hands the calls to [SecaInCallService]; this keeps them and turns
 * each change into a fresh [CallView]. Everything happens on the main thread,
 * where Telecom delivers its callbacks.
 */
object CallSession {

    private val main = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val tracked = mutableListOf<Call>()
    private val callers = mutableMapOf<String, Caller?>()
    private var appContext: Context? = null
    private var service: SecaInCallService? = null

    /** Reads numbers with the SIM's country, for the screen and the notifications. */
    var numbers: PhoneNumbers = PhoneNumbers("ZZ")
        private set

    private val _calls = MutableStateFlow<List<CallView>>(emptyList())
    val calls: StateFlow<List<CallView>> = _calls.asStateFlow()

    private val _audio = MutableStateFlow(AudioView())
    val audio: StateFlow<AudioView> = _audio.asStateFlow()

    /** The last call to end, so the screen can say so for a moment before closing. */
    private val _ended = MutableStateFlow<CallView?>(null)
    val ended: StateFlow<CallView?> = _ended.asStateFlow()

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = changed()
        override fun onDetailsChanged(call: Call, details: Call.Details) = changed()
        override fun onParentChanged(call: Call, parent: Call?) = changed()
        override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: List<Call>) = changed()
    }

    internal fun attach(service: SecaInCallService) {
        this.service = service
        appContext = service.applicationContext
        numbers = PhoneNumbers(PhoneNumbers.detectRegion(service))
    }

    internal fun detach(service: SecaInCallService) {
        if (this.service === service) this.service = null
    }

    internal fun add(call: Call) {
        tracked += call
        call.registerCallback(callback)
        _ended.value = null
        lookUp(numberOf(call))
        changed()
    }

    internal fun remove(call: Call) {
        val last = viewOf(call)
        call.unregisterCallback(callback)
        tracked.remove(call)
        if (tracked.isEmpty()) _ended.value = last
        changed()
    }

    internal fun onMuted(muted: Boolean) = _audio.update { it.copy(muted = muted) }

    internal fun onEndpoint(endpoint: CallEndpoint) = _audio.update { it.copy(endpoint = endpoint) }

    internal fun onEndpoints(endpoints: List<CallEndpoint>) = _audio.update { it.copy(endpoints = endpoints) }

    fun ringing(): Call? = tracked.firstOrNull {
        it.details.state == Call.STATE_RINGING || it.details.state == Call.STATE_SIMULATED_RINGING
    }

    fun answer(call: Call) = call.answer(VideoProfile.STATE_AUDIO_ONLY)

    /** Declines; with a [message], Android texts it to the caller. */
    fun decline(call: Call, message: String? = null) = call.reject(message != null, message)

    fun hangUp(call: Call) = call.disconnect()

    fun toggleHold(view: CallView) = if (view.state == Call.STATE_HOLDING) view.call.unhold() else view.call.hold()

    /** Picks the call on hold back up; the network puts the other one on hold. */
    fun swap() {
        tracked.firstOrNull { it.details.state == Call.STATE_HOLDING && it.parent == null }?.unhold()
    }

    fun merge(view: CallView) {
        if (view.canMerge) view.call.mergeConference() else view.call.conferenceableCalls.firstOrNull()?.let(view.call::conference)
    }

    fun setMuted(muted: Boolean) {
        service?.setMuted(muted)
    }

    fun route(endpoint: CallEndpoint) {
        val service = service ?: return
        service.requestCallEndpointChange(endpoint, service.mainExecutor, IgnoreOutcome)
    }

    /** Plays a keypad tone to the other side, for voice menus. */
    fun dtmf(call: Call, key: Char) {
        call.playDtmfTone(key)
        main.launch {
            delay(DTMF_MILLIS)
            call.stopDtmfTone()
        }
    }

    fun chooseAccount(call: Call, account: PhoneAccountHandle) = call.phoneAccountSelected(account, false)

    fun accountName(account: PhoneAccountHandle): String =
        runCatching { appContext?.getSystemService(TelecomManager::class.java)?.getPhoneAccount(account)?.label?.toString() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "SIM"

    fun clearEnded() {
        _ended.value = null
    }

    private fun changed() {
        _calls.value = tracked.map(::viewOf)
        appContext?.let { CallNotifications.update(it, _calls.value) }
    }

    private fun viewOf(call: Call): CallView {
        val details = call.details
        val number = numberOf(call)
        return CallView(
            call = call,
            state = details.state,
            number = number,
            presentation = details.handlePresentation,
            networkName = details.callerDisplayName?.takeIf {
                it.isNotBlank() && details.callerDisplayNamePresentation == TelecomManager.PRESENTATION_ALLOWED
            },
            incoming = details.callDirection == Call.Details.DIRECTION_INCOMING,
            connectTime = details.connectTimeMillis,
            canHold = details.can(Call.Details.CAPABILITY_HOLD),
            canMerge = details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE),
            canSwap = details.can(Call.Details.CAPABILITY_SWAP_CONFERENCE),
            isConferencePart = call.parent != null,
            wifi = details.hasProperty(Call.Details.PROPERTY_WIFI),
            hd = details.hasProperty(Call.Details.PROPERTY_HIGH_DEF_AUDIO),
            disconnectLabel = details.disconnectCause?.label?.toString()?.takeIf { it.isNotBlank() },
            accountLabel = accountLabel(details.accountHandle),
            accountsToChoose = accountsOf(details),
            caller = callers[number],
        )
    }

    private fun numberOf(call: Call): String = call.details.handle?.schemeSpecificPart.orEmpty()

    private fun accountLabel(account: PhoneAccountHandle?): String? {
        val context = appContext ?: return null
        if (account == null) return null
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return null
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return null
        return runCatching { if (telecom.callCapablePhoneAccounts.size < 2) null else accountName(account) }.getOrNull()
    }

    private fun accountsOf(details: Call.Details): List<PhoneAccountHandle> {
        if (details.state != Call.STATE_SELECT_PHONE_ACCOUNT) return emptyList()
        val offered = listOfNotNull(details.intentExtras, details.extras).firstNotNullOfOrNull { extras ->
            extras.getParcelableArrayList(Call.AVAILABLE_PHONE_ACCOUNTS, PhoneAccountHandle::class.java)?.takeIf { it.isNotEmpty() }
        }
        if (offered != null) return offered
        val context = appContext ?: return emptyList()
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return emptyList()
        return runCatching { context.getSystemService(TelecomManager::class.java)?.callCapablePhoneAccounts }.getOrNull().orEmpty()
    }

    /** Finds the caller once per number, off the main thread, then refreshes the screen and notification. */
    private fun lookUp(number: String) {
        val context = appContext ?: return
        if (number.isBlank() || callers.containsKey(number)) return
        callers[number] = null
        main.launch {
            callers[number] = withContext(Dispatchers.IO) { findCaller(context, number) }
            changed()
        }
    }

    private suspend fun findCaller(context: Context, number: String): Caller? {
        val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val projection = arrayOf(
            PhoneLookup._ID,
            PhoneLookup.LOOKUP_KEY,
            PhoneLookup.DISPLAY_NAME,
            PhoneLookup.PHOTO_THUMBNAIL_URI,
            PhoneLookup.TYPE,
            PhoneLookup.LABEL,
        )
        // The provider's own matching, which already treats "06…" and "+33 6…" alike.
        val found = runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                val label = Phone.getTypeLabel(context.resources, c.getInt(4), c.getString(5)).toString()
                FoundContact(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getString(3), label)
            }
        }.getOrNull() ?: return null
        val profiles = SharedProfilesClient(context.contentResolver).load()
        val profile = profiles.profileForKey(found.lookupKey)
        return Caller(
            contactId = found.id,
            lookupKey = found.lookupKey,
            name = found.name,
            photoUri = found.photoUri,
            numberLabel = found.label.takeIf { it.isNotBlank() },
            profile = profile,
            tone = profiles.toneOf(profile),
        )
    }

    private class FoundContact(val id: Long, val lookupKey: String, val name: String, val photoUri: String?, val label: String)

    private object IgnoreOutcome : OutcomeReceiver<Void, CallEndpointException> {
        override fun onResult(result: Void?) = Unit
        override fun onError(error: CallEndpointException) = Unit
    }

    private const val DTMF_MILLIS = 150L
}
