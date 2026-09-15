package com.seca.contacts

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.seca.core.contacts.ContactDetail
import com.seca.core.contacts.ContactInput
import com.seca.core.contacts.ContactsRepository
import com.seca.core.contacts.PhoneNumbers
import com.seca.core.contacts.SharedProfilesClient
import com.seca.core.contacts.VCard
import com.seca.core.contacts.VCardContact
import com.seca.core.design.SecaPalette
import com.seca.core.model.backup.BackupCipher
import org.json.JSONObject
import com.seca.core.model.Profile
import com.seca.core.model.SecaContact
import com.seca.core.suite.SuiteBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/** The app's screens; the last entry of the back stack is the one shown. */
sealed interface Screen {
    data object Home : Screen
    data class Detail(val id: Long) : Screen

    /** [id] is null when creating a contact; [prefillPhone] comes from another app's "add". */
    data class Edit(val id: Long?, val prefillPhone: String? = null) : Screen
    data object Settings : Screen
    data object MyCard : Screen
}

data class ContactsUi(
    /** Reads numbers with the SIM's country. */
    val numbers: PhoneNumbers,
    val loaded: Boolean = false,
    val contacts: List<SecaContact> = emptyList(),
    val profiles: List<Profile> = listOf(ProfileStore.Principal),
    val currentProfileId: String = ProfileStore.Principal.id,
    val assignments: Map<String, String> = emptyMap(),
    val query: String = "",
    /** Null, the default, follows the wallpaper colours (Material You). */
    val palette: SecaPalette? = null,
    val myCard: MyCard = MyCard(),
    val myPhoto: ImageBitmap? = null,
    /** The phone's SIM lines, read only once the owner allowed it. */
    val simLines: List<SimLine> = emptyList(),
) {
    /** The owner's numbers: those the SIMs carry, then those typed in, each once. */
    val myNumbers: List<String>
        get() = (simLines.mapNotNull { it.number } + myCard.numbers).distinctBy { numbers.key(it) }

    val currentProfile: Profile
        get() = profiles.firstOrNull { it.id == currentProfileId } ?: ProfileStore.Principal

    /** Whether the list is showing every contact rather than one profile. */
    val showingAll: Boolean get() = currentProfileId == ProfileStore.ALL

    /** How many contacts each profile holds, by profile id. */
    val counts: Map<String, Int> by lazy { contacts.groupingBy { profileOf(it).id }.eachCount() }

    /** A contact never placed anywhere, or whose profile was deleted, is in Principal. */
    fun profileForKey(lookupKey: String): Profile =
        profiles.firstOrNull { it.id == assignments[lookupKey] } ?: ProfileStore.Principal

    fun profileOf(contact: SecaContact): Profile = profileForKey(contact.lookupKey)

    /** A profile's position in the list, which picks its badge colour. */
    fun toneOf(profile: Profile): Int = profiles.indexOfFirst { it.id == profile.id }.coerceAtLeast(0)
}

class ContactsViewModel(application: Application) : AndroidViewModel(application) {

    private val numbers = PhoneNumbers(PhoneNumbers.detectRegion(application))
    private val repository = ContactsRepository(application.contentResolver, numbers)
    private val store = ProfileStore(application)
    private val myCards = MyCardStore(application)
    private val resolver = application.contentResolver
    private val _ui = MutableStateFlow(ContactsUi(numbers = numbers))
    val ui: StateFlow<ContactsUi> = _ui.asStateFlow()

    /** Kept here rather than in the composition, so it survives rotation. */
    val backStack = mutableStateListOf<Screen>(Screen.Home)

    private var watching: Job? = null

    init {
        refreshPreferences()
        loadMyCard()
        refreshSimLines()
        // Seca Phone can change the suite's palette too: follow it.
        viewModelScope.launch {
            SharedProfilesClient(application.contentResolver).changes().conflate().collect { refreshPreferences() }
        }
    }

    /** Loads once access is granted, then follows every change to the provider. */
    fun start() {
        if (watching != null) return
        watching = viewModelScope.launch {
            load()
            repository.changes().conflate().collect { load() }
        }
    }

    fun open(screen: Screen) {
        backStack.add(screen)
    }

    fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    /** Opens straight on [screen] for another app's request, so going back returns to that app. */
    fun startWith(screen: Screen) {
        backStack.clear()
        backStack.add(screen)
    }

    /** Opens the card of the contact behind a contacts link from another app. */
    fun openExternal(uri: Uri) {
        viewModelScope.launch {
            repository.contactIdFor(uri)?.let { startWith(Screen.Detail(it)) }
        }
    }

    fun setQuery(query: String) = _ui.update { it.copy(query = query) }

    fun selectProfile(id: String) {
        store.setCurrentProfile(id)
        refreshPreferences()
    }

    fun addProfile(name: String) {
        val profile = store.addProfile(name)
        store.setCurrentProfile(profile.id)
        refreshPreferences()
    }

    fun renameProfile(id: String, name: String) {
        store.renameProfile(id, name)
        refreshPreferences()
    }

    fun deleteProfile(id: String) {
        store.deleteProfile(id)
        refreshPreferences()
    }

    fun assignProfile(lookupKey: String, profileId: String) {
        store.assign(lookupKey, profileId)
        refreshPreferences()
    }

    /** Null goes back to the wallpaper colours. */
    fun setPalette(palette: SecaPalette?) {
        store.setPalette(palette)
        refreshPreferences()
    }

    /** Writes every contact to the chosen .vcf file, each with the profile it is filed in. */
    fun exportContacts(uri: Uri) {
        viewModelScope.launch {
            val state = _ui.value
            val cards = repository.exportEntries().map { (key, card) ->
                val profile = state.profileForKey(key)
                if (profile.id == ProfileStore.Principal.id) card else card.copy(profile = profile.name)
            }
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    resolver.openOutputStream(uri, "wt")?.use { it.write(VCard.write(cards).toByteArray(Charsets.UTF_8)) } != null
                }.getOrDefault(false)
            }
            toast(if (written) quantity(R.plurals.contacts_exported, cards.size) else text(R.string.export_failed))
        }
    }

    /** Adds the contacts of a .vcf file, on this device only, skipping those already here. */
    fun importContacts(uri: Uri) {
        viewModelScope.launch {
            val cards = withContext(Dispatchers.IO) {
                runCatching { resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
            }?.let(VCard::parse)
            if (cards == null) {
                toast(text(R.string.unreadable_file))
                return@launch
            }
            val existing = _ui.value.contacts
            var added = 0
            val filed = store.assignments().toMutableMap()
            cards.forEach { card ->
                val key = existing.firstOrNull { it.isSameAs(card) }?.lookupKey
                    ?: repository.createContact(card.toInput())?.also { added++ }?.let { repository.lookupKeyOf(it) }
                // A card exported by Seca names its profile: the contact goes back there, unless already filed here.
                if (card.profile.isBlank() || key.isNullOrEmpty() || key in filed) return@forEach
                val profile = store.profiles().firstOrNull { it.name.equals(card.profile, ignoreCase = true) }
                    ?: store.addProfile(card.profile)
                if (profile.id != ProfileStore.Principal.id) filed[key] = profile.id
            }
            store.assignAll(filed)
            refreshPreferences()
            load()
            toast(
                when {
                    cards.isEmpty() -> text(R.string.no_contacts_in_file)
                    added == 0 -> text(R.string.contacts_already_here)
                    else -> quantity(R.plurals.contacts_imported, added)
                },
            )
        }
    }

    /**
     * Writes every contact, its profile, the profiles themselves and "My card" to [uri],
     * encrypted with [passphrase]. The passphrase is wiped from memory once used.
     */
    fun exportBackup(uri: Uri, passphrase: CharArray) {
        viewModelScope.launch {
            val part = ContactsBackup(getApplication()).export()
            val sealed = withContext(Dispatchers.Default) {
                BackupCipher.encrypt(part.toString().toByteArray(Charsets.UTF_8), passphrase).also { passphrase.fill(' ') }
            }
            val written = withContext(Dispatchers.IO) {
                runCatching { resolver.openOutputStream(uri, "wt")?.use { it.write(sealed) } != null }.getOrDefault(false)
            }
            toast(if (written) text(R.string.backup_done, part.optString(SuiteBackup.KEY_SUMMARY)) else text(R.string.backup_failed))
        }
    }

    /** Restores a backup made by [exportBackup]: missing profiles and contacts come back, each in its profile. */
    fun importBackup(uri: Uri, passphrase: CharArray) {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            val plain = bytes?.let { withContext(Dispatchers.Default) { BackupCipher.decrypt(it, passphrase) } }
            passphrase.fill(' ')
            val json = plain?.let { runCatching { JSONObject(it.toString(Charsets.UTF_8)) }.getOrNull() }
            if (json == null || json.optString("app") != "seca-contacts") {
                toast(
                    when {
                        bytes == null -> text(R.string.unreadable_file)
                        plain == null -> text(R.string.wrong_password)
                        else -> text(R.string.not_contacts_backup)
                    },
                )
                return@launch
            }
            val restored = ContactsBackup(getApplication()).restore(json)
            refreshPreferences()
            loadMyCard()
            load()
            toast(text(R.string.backup_restored, restored))
        }
    }

    /** One contact as a vCard, for sharing it. */
    suspend fun vCardOf(id: Long): String? = repository.contactDetail(id)?.let { detail ->
        VCard.write(listOf(VCardContact(detail.givenName, detail.familyName, detail.displayName, detail.phones, detail.emails)))
    }

    private fun SecaContact.isSameAs(card: VCardContact): Boolean = isSameAs(card, numbers)

    private fun text(@StringRes id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    private fun quantity(@PluralsRes id: Int, count: Int): String =
        getApplication<Application>().resources.getQuantityString(id, count, count)

    private fun toast(text: String) {
        Toast.makeText(getApplication(), text, Toast.LENGTH_LONG).show()
    }

    fun saveMyCard(name: String, numbers: List<String>) {
        myCards.save(name, numbers)
        loadMyCard()
    }

    fun setMyPhoto(uri: Uri) {
        viewModelScope.launch { if (myCards.setPhoto(uri)) loadMyCard() }
    }

    fun removeMyPhoto() {
        myCards.removePhoto()
        loadMyCard()
    }

    /** Reads the SIM lines again, e.g. once the owner allowed it; nothing without the permissions. */
    fun refreshSimLines() {
        viewModelScope.launch {
            val lines = withContext(Dispatchers.IO) { readSimLines(getApplication<Application>()) }
            _ui.update { it.copy(simLines = lines) }
        }
    }

    private fun loadMyCard() {
        viewModelScope.launch {
            val card = myCards.load()
            val photo = myCards.photo()?.asImageBitmap()
            _ui.update { it.copy(myCard = card, myPhoto = photo) }
        }
    }

    suspend fun detail(id: Long): ContactDetail? = repository.contactDetail(id)

    fun save(existing: ContactDetail?, input: ContactInput, profileId: String) {
        viewModelScope.launch {
            val id: Long = if (existing == null) {
                repository.createContact(input) ?: return@launch
            } else {
                repository.updateContact(existing, input)
                existing.id
            }
            // The provider rebuilds a contact's key when its name changes, so the profile is
            // filed again under the key the contact has now, and the old one is let go.
            val currentKey = repository.lookupKeyOf(id)
            existing?.lookupKey?.takeIf { it != currentKey }?.let { store.assign(it, ProfileStore.Principal.id) }
            if (currentKey != null) store.assign(currentKey, profileId)
            refreshPreferences()
            load()
            if (existing == null) backStack[backStack.lastIndex] = Screen.Detail(id) else back()
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            repository.deleteContact(id)
            load()
            backStack.removeAll { (it is Screen.Detail && it.id == id) || (it is Screen.Edit && it.id == id) }
            if (backStack.isEmpty()) backStack.add(Screen.Home)
        }
    }

    /** Null goes back to the phone's own ringtone. */
    fun setRingtone(id: Long, ringtone: String?) {
        viewModelScope.launch {
            repository.setRingtone(id, ringtone)
            load()
        }
    }

    fun setSendToVoicemail(id: Long, on: Boolean) {
        viewModelScope.launch {
            repository.setSendToVoicemail(id, on)
            load()
        }
    }

    /** Joins contacts that are the same person; the joined contact keeps the first one's profile. */
    fun merge(group: List<SecaContact>) {
        viewModelScope.launch {
            val profileId = _ui.value.profileOf(group.first()).id
            val merged = runCatching { repository.mergeContacts(group.map { it.id }) }.getOrNull()
            if (merged == null) {
                toast(text(R.string.merge_failed))
                return@launch
            }
            // Joining changes the contact's lookup key, which carries its profile.
            repository.lookupKeyOf(merged)?.let { assignProfile(it, profileId) }
            load()
            toast(quantity(R.plurals.cards_merged, group.size))
        }
    }

    fun setStarred(id: Long, starred: Boolean) {
        viewModelScope.launch {
            repository.setStarred(id, starred)
            load()
        }
    }

    private suspend fun load() {
        val contacts = repository.contacts()
        // Every number is read here, off the main thread: the list then only
        // looks results up, and scrolling never waits on parsing.
        withContext(Dispatchers.Default) {
            contacts.forEach { contact -> contact.phoneNumbers.forEach { numbers.prepare(it.raw) } }
        }
        _ui.update { it.copy(loaded = true, contacts = contacts) }
    }

    private fun refreshPreferences() {
        _ui.update {
            it.copy(
                profiles = store.profiles(),
                currentProfileId = store.currentProfileId(),
                assignments = store.assignments(),
                palette = store.palette(),
            )
        }
    }
}
