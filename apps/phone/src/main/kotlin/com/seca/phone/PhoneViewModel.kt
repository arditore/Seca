package com.seca.phone

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.seca.core.contacts.ContactsRepository
import com.seca.core.contacts.NumberIndex
import com.seca.core.contacts.NumberMatch
import com.seca.core.contacts.PhoneNumbers
import com.seca.core.contacts.SharedProfiles
import com.seca.core.contacts.SharedProfilesClient
import com.seca.core.design.SecaPalette
import com.seca.core.model.Profile
import com.seca.core.model.SecaContact
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The app's screens; the last entry of the back stack is the one shown. */
sealed interface PhoneScreen {
    data object Home : PhoneScreen
    data class Dialer(val initial: String = "") : PhoneScreen

    /** The history with one number — or with the whole contact, when the number is known. */
    data class CallDetail(val number: String) : PhoneScreen
}

enum class CallFilter { All, Missed }

data class PhoneUi(
    /** Reads numbers with the SIM's country. */
    val numbers: PhoneNumbers,
    val loaded: Boolean = false,
    val calls: List<CallRecord> = emptyList(),
    /** The history as rows, rebuilt when the calls, the contacts or the filter change. */
    val groups: List<CallGroup> = emptyList(),
    val contacts: List<SecaContact> = emptyList(),
    val index: NumberIndex? = null,
    val profiles: SharedProfiles = SharedProfiles(),
    val query: String = "",
    val filter: CallFilter = CallFilter.All,
) {
    /** The palette picked in Seca Contacts; null follows the wallpaper. */
    val palette: SecaPalette? get() = SecaPalette.entries.firstOrNull { it.name == profiles.palette }

    fun profileOf(contact: SecaContact): Profile = profiles.profileForKey(contact.lookupKey)

    fun toneOf(contact: SecaContact): Int = profiles.toneOf(profileOf(contact))

    fun matchOf(call: CallRecord): NumberMatch? =
        if (call.presentation == Presentation.Allowed) index?.find(call.number) else null

    /** All calls with the contact [number] belongs to, or with that number alone when it is unknown. */
    fun historyFor(number: String): List<CallRecord> {
        val contact = index?.find(number)?.contact
        return if (contact != null) {
            calls.filter { matchOf(it)?.contact?.id == contact.id }
        } else {
            val key = numbers.key(number)
            calls.filter { it.presentation == Presentation.Allowed && numbers.key(it.number) == key }
        }
    }
}

class PhoneViewModel(application: Application) : AndroidViewModel(application) {

    private val numbers = PhoneNumbers(PhoneNumbers.detectRegion(application))
    private val callLog = CallLogRepository(application.contentResolver)
    private val contacts = ContactsRepository(application.contentResolver, numbers)
    private val sharedProfiles = SharedProfilesClient(application.contentResolver)
    private val _ui = MutableStateFlow(PhoneUi(numbers))
    val ui: StateFlow<PhoneUi> = _ui.asStateFlow()

    /** Kept here rather than in the composition, so it survives rotation. */
    val backStack = mutableStateListOf<PhoneScreen>(PhoneScreen.Home)

    private var watching: Job? = null

    init {
        viewModelScope.launch {
            loadProfiles()
            sharedProfiles.changes().conflate().collect { loadProfiles() }
        }
    }

    /** (Re)loads what the granted permissions allow, then follows every change. */
    fun start(callLogGranted: Boolean, contactsGranted: Boolean) {
        watching?.cancel()
        watching = viewModelScope.launch {
            if (contactsGranted) {
                launch {
                    loadContacts()
                    contacts.changes().conflate().collect { loadContacts() }
                }
            }
            if (callLogGranted) {
                launch {
                    loadCalls()
                    callLog.changes().conflate().collect { loadCalls() }
                }
            }
        }
    }

    fun open(screen: PhoneScreen) {
        backStack.add(screen)
    }

    fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    /** Opens the keypad with [number], replacing a keypad already open. */
    fun openDialer(number: String) {
        backStack.removeAll { it is PhoneScreen.Dialer }
        backStack.add(PhoneScreen.Dialer(number))
    }

    fun setQuery(query: String) = _ui.update { it.copy(query = query) }

    fun setFilter(filter: CallFilter) = _ui.update { regrouped(it.copy(filter = filter)) }

    private suspend fun loadCalls() {
        val calls = callLog.calls()
        _ui.update { regrouped(it.copy(calls = calls, loaded = true)) }
    }

    private suspend fun loadContacts() {
        val list = contacts.contacts()
        _ui.update { regrouped(it.copy(contacts = list, index = NumberIndex(list, numbers))) }
    }

    private suspend fun loadProfiles() {
        val profiles = sharedProfiles.load()
        _ui.update { it.copy(profiles = profiles) }
    }

    private fun regrouped(ui: PhoneUi): PhoneUi {
        val shown = if (ui.filter == CallFilter.Missed) ui.calls.filter { it.type == CallType.Missed } else ui.calls
        val keyOf = { call: CallRecord ->
            if (call.presentation == Presentation.Allowed) numbers.key(call.number) else "hidden-${call.presentation}"
        }
        val groups = groupCalls(shown, keyOf, dayOf = { dayOf(it) }).map { CallGroup(it, ui.matchOf(it.first())) }
        return ui.copy(groups = groups)
    }
}
