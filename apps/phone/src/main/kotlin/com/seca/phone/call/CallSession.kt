package com.seca.phone.call

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.OutcomeReceiver
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.PhoneLookup
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.CallEndpointException
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import androidx.annotation.RequiresApi
import androidx.core.os.BundleCompat
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

/** Where the call's sound goes. */
enum class AudioKind { Earpiece, Speaker, Bluetooth, Headset }

/**
 * One place the call's sound can go. Android 14 describes it as a call
 * endpoint; before, as a route of the call's audio state, with a Bluetooth
 * device when there are several.
 */
class AudioRoute internal constructor(
    val kind: AudioKind,
    val name: String,
    /** Tells two devices apart even when Android names them alike, or not at all. */
    internal val key: String,
    internal val endpoint: Any? = null,
    internal val legacyRoute: Int = 0,
    internal val bluetooth: BluetoothDevice? = null,
) {
    override fun equals(other: Any?): Boolean = other is AudioRoute && other.key == key

    override fun hashCode(): Int = key.hashCode()
}

data class AudioView(
    val muted: Boolean = false,
    val route: AudioRoute? = null,
    val routes: List<AudioRoute> = emptyList(),
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

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    internal fun onEndpoint(endpoint: CallEndpoint) {
        val key = endpoint.identifier.toString()
        val named = routesOf(listOf(endpoint)).first()
        // The route as the list names it, so the button and the list agree.
        _audio.update { audio -> audio.copy(route = audio.routes.firstOrNull { it.key == key } ?: named) }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    internal fun onEndpoints(endpoints: List<CallEndpoint>) {
        val routes = routesOf(endpoints)
        _audio.update { audio -> audio.copy(routes = routes, route = routes.firstOrNull { it.key == audio.route?.key } ?: audio.route) }
    }

    /** Before Android 14, where the sound goes and whether it is muted come as one audio state. */
    internal fun onAudioState(state: CallAudioState) {
        val mask = state.supportedRouteMask
        val routes = buildList {
            if (mask and CallAudioState.ROUTE_EARPIECE != 0) {
                add(AudioRoute(AudioKind.Earpiece, EARPIECE, "earpiece", legacyRoute = CallAudioState.ROUTE_EARPIECE))
            }
            if (mask and CallAudioState.ROUTE_WIRED_HEADSET != 0) {
                add(AudioRoute(AudioKind.Headset, HEADSET, "headset", legacyRoute = CallAudioState.ROUTE_WIRED_HEADSET))
            }
            if (mask and CallAudioState.ROUTE_SPEAKER != 0) {
                add(AudioRoute(AudioKind.Speaker, SPEAKER, "speaker", legacyRoute = CallAudioState.ROUTE_SPEAKER))
            }
            if (mask and CallAudioState.ROUTE_BLUETOOTH != 0) {
                val devices = state.supportedBluetoothDevices.toList()
                val names = bluetoothNames(devices.size.coerceAtLeast(1))
                if (devices.isEmpty()) {
                    add(AudioRoute(AudioKind.Bluetooth, names.first(), "bluetooth", legacyRoute = CallAudioState.ROUTE_BLUETOOTH))
                }
                devices.forEachIndexed { index, device ->
                    add(
                        AudioRoute(
                            AudioKind.Bluetooth,
                            names[index],
                            "bluetooth-${device.hashCode()}",
                            legacyRoute = CallAudioState.ROUTE_BLUETOOTH,
                            bluetooth = device,
                        ),
                    )
                }
            }
        }
        val current = routes.firstOrNull { it.legacyRoute == state.route && (it.bluetooth == null || it.bluetooth == state.activeBluetoothDevice) }
            ?: routes.firstOrNull { it.legacyRoute == state.route }
        _audio.update { it.copy(muted = state.isMuted, route = current, routes = routes) }
    }

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

    @Suppress("DEPRECATION") // Before Android 14, the route and the Bluetooth device are chosen this way.
    fun route(route: AudioRoute) {
        val service = service ?: return
        val endpoint = route.endpoint
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && endpoint is CallEndpoint) {
            service.requestCallEndpointChange(endpoint, service.mainExecutor, IgnoreOutcome)
        } else if (route.bluetooth != null) {
            service.requestBluetoothAudio(route.bluetooth)
        } else {
            service.setAudioRoute(route.legacyRoute)
        }
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
        appContext?.let { CallNotifications.update(it, _calls.value, service) }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun routesOf(endpoints: List<CallEndpoint>): List<AudioRoute> {
        val bluetooth = endpoints.filter { it.endpointType == CallEndpoint.TYPE_BLUETOOTH }
        val bluetoothNames = bluetoothNames(bluetooth.size, bluetooth.map { it.endpointName.toString() })
        return endpoints.map { endpoint ->
            val given = endpoint.endpointName.toString().trim()
            val (kind, name) = when (endpoint.endpointType) {
                CallEndpoint.TYPE_SPEAKER, CallEndpoint.TYPE_STREAMING -> AudioKind.Speaker to given.ifEmpty { SPEAKER }
                CallEndpoint.TYPE_BLUETOOTH -> AudioKind.Bluetooth to bluetoothNames[bluetooth.indexOfFirst { it === endpoint }]
                CallEndpoint.TYPE_WIRED_HEADSET -> AudioKind.Headset to given.ifEmpty { HEADSET }
                else -> AudioKind.Earpiece to given.ifEmpty { EARPIECE }
            }
            AudioRoute(kind, name, endpoint.identifier.toString(), endpoint = endpoint)
        }
    }

    /**
     * Names for [count] Bluetooth routes. Telecom often calls every device just
     * "Bluetooth"; the audio system knows the headset's or the car's own name,
     * and tells it without any permission.
     */
    private fun bluetoothNames(count: Int, given: List<String?> = emptyList()): List<String> {
        val known = runCatching { appContext?.getSystemService(AudioManager::class.java)?.availableCommunicationDevices }
            .getOrNull()
            .orEmpty()
            .filter { it.type in BluetoothTypes }
            .map { it.productName?.toString()?.trim().orEmpty() }
            .filter { it.isNotEmpty() }
            .distinct()
        return List(count) { index ->
            given.getOrNull(index)?.trim()?.takeUnless { it.isEmpty() || it.equals(BLUETOOTH, ignoreCase = true) }
                ?: known.getOrNull(index)?.takeIf { known.size == count }
                ?: if (count == 1) BLUETOOTH else "$BLUETOOTH ${index + 1}"
        }
    }

    private val BluetoothTypes = setOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_HEARING_AID,
    )

    private const val EARPIECE = "Téléphone"
    private const val SPEAKER = "Haut-parleur"
    private const val HEADSET = "Écouteurs"
    private const val BLUETOOTH = "Bluetooth"

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
            BundleCompat.getParcelableArrayList(extras, Call.AVAILABLE_PHONE_ACCOUNTS, PhoneAccountHandle::class.java)
                ?.takeIf { it.isNotEmpty() }
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

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private object IgnoreOutcome : OutcomeReceiver<Void, CallEndpointException> {
        override fun onResult(result: Void?) = Unit
        override fun onError(error: CallEndpointException) = Unit
    }

    private const val DTMF_MILLIS = 150L
}
