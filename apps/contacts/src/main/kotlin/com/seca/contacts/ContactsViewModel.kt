package com.seca.contacts

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.seca.core.contacts.ContactDetail
import com.seca.core.contacts.ContactInput
import com.seca.core.contacts.ContactsRepository
import com.seca.core.design.SecaPalette
import com.seca.core.model.SecaContact
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The app's screens; the last entry of the back stack is the one shown. */
sealed interface Screen {
    data object Home : Screen
    data class Detail(val id: Long) : Screen

    /** [id] is null when creating a contact. */
    data class Edit(val id: Long?) : Screen
    data object Settings : Screen
}

data class ContactsUi(
    val loaded: Boolean = false,
    val contacts: List<SecaContact> = emptyList(),
    val profiles: List<Profile> = listOf(ProfileStore.Principal),
    val currentProfileId: String = ProfileStore.Principal.id,
    val assignments: Map<String, String> = emptyMap(),
    val query: String = "",
    /** Null, the default, follows the wallpaper colours (Material You). */
    val palette: SecaPalette? = null,
) {
    val currentProfile: Profile
        get() = profiles.firstOrNull { it.id == currentProfileId } ?: ProfileStore.Principal

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

    private val repository = ContactsRepository(application.contentResolver)
    private val store = ProfileStore(application)
    private val _ui = MutableStateFlow(ContactsUi())
    val ui: StateFlow<ContactsUi> = _ui.asStateFlow()

    /** Kept here rather than in the composition, so it survives rotation. */
    val backStack = mutableStateListOf<Screen>(Screen.Home)

    private var watching: Job? = null

    init {
        refreshPreferences()
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

    suspend fun detail(id: Long): ContactDetail? = repository.contactDetail(id)

    fun save(existing: ContactDetail?, input: ContactInput, profileId: String) {
        viewModelScope.launch {
            val id: Long = if (existing == null) {
                repository.createContact(input) ?: return@launch
            } else {
                repository.updateContact(existing, input)
                existing.id
            }
            val lookupKey = existing?.lookupKey ?: repository.lookupKeyOf(id)
            if (lookupKey != null) store.assign(lookupKey, profileId)
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

    fun setStarred(id: Long, starred: Boolean) {
        viewModelScope.launch {
            repository.setStarred(id, starred)
            load()
        }
    }

    private suspend fun load() {
        val contacts = repository.contacts()
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
