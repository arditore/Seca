package com.seca.messages

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.seca.core.contacts.ContactsRepository
import com.seca.core.contacts.NumberIndex
import com.seca.core.contacts.PhoneNumbers
import com.seca.core.contacts.SharedProfiles
import com.seca.core.contacts.SharedProfilesClient
import com.seca.core.design.SecaPalette
import com.seca.core.model.Profile
import com.seca.core.model.SecaContact
import com.seca.messages.sms.Conversation
import com.seca.messages.sms.Message
import com.seca.messages.sms.MessageNotifications
import com.seca.messages.sms.MessagesRepository
import com.seca.messages.sms.SmsSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The app's screens; the last entry of the back stack is the one shown. */
sealed interface MessagesScreen {
    data object Home : MessagesScreen

    /** [threadId] is -1 when the conversation does not exist yet; [draft] comes from a link. */
    data class Conversation(val threadId: Long, val address: String, val draft: String = "") : MessagesScreen
    data object NewMessage : MessagesScreen
    data object Settings : MessagesScreen
}

data class MessagesUi(
    /** Reads numbers with the SIM's country. */
    val numbers: PhoneNumbers,
    val loaded: Boolean = false,
    val conversations: List<Conversation> = emptyList(),
    val contacts: List<SecaContact> = emptyList(),
    val index: NumberIndex? = null,
    val profiles: SharedProfiles = SharedProfiles(),
    val query: String = "",
) {
    /** The palette picked in Seca Contacts; null follows the wallpaper. */
    val palette: SecaPalette? get() = SecaPalette.entries.firstOrNull { it.name == profiles.palette }

    fun contactOf(address: String): SecaContact? = index?.find(address)?.contact

    fun nameOf(address: String): String = contactOf(address)?.displayName ?: numbers.display(address)

    fun profileOf(contact: SecaContact): Profile = profiles.profileForKey(contact.lookupKey)

    fun toneOf(contact: SecaContact): Int = profiles.toneOf(profileOf(contact))
}

class MessagesViewModel(application: Application) : AndroidViewModel(application) {

    private val numbers = PhoneNumbers(PhoneNumbers.detectRegion(application))
    private val repository = MessagesRepository(application)
    private val contacts = ContactsRepository(application.contentResolver, numbers)
    private val sharedProfiles = SharedProfilesClient(application.contentResolver)
    private val _ui = MutableStateFlow(MessagesUi(numbers))
    val ui: StateFlow<MessagesUi> = _ui.asStateFlow()

    /** Kept here rather than in the composition, so it survives rotation. */
    val backStack = mutableStateListOf<MessagesScreen>(MessagesScreen.Home)

    private var watching: Job? = null

    init {
        viewModelScope.launch {
            loadProfiles()
            sharedProfiles.changes().conflate().collect { loadProfiles() }
        }
    }

    /** (Re)loads what the granted permissions allow, then follows every change. */
    fun start(smsGranted: Boolean, contactsGranted: Boolean) {
        watching?.cancel()
        watching = viewModelScope.launch {
            if (contactsGranted) {
                launch {
                    loadContacts()
                    contacts.changes().conflate().collect { loadContacts() }
                }
            }
            if (smsGranted) {
                launch {
                    loadConversations()
                    repository.changes().conflate().collect { loadConversations() }
                }
            }
        }
    }

    fun open(screen: MessagesScreen) {
        backStack.add(screen)
    }

    fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    /** Opens straight on [screen] for another app's request, so going back returns to that app. */
    fun startWith(screen: MessagesScreen) {
        backStack.clear()
        backStack.add(screen)
    }

    /** Opens the conversation with [address]; from "Nouveau message", it takes that screen's place. */
    fun openConversation(address: String, draft: String = "") {
        viewModelScope.launch {
            val threadId = repository.threadIdFor(address) ?: -1L
            val screen = MessagesScreen.Conversation(threadId, address, draft)
            if (backStack.lastOrNull() is MessagesScreen.NewMessage) {
                backStack[backStack.lastIndex] = screen
            } else {
                backStack.add(screen)
            }
        }
    }

    fun setQuery(query: String) = _ui.update { it.copy(query = query) }

    /** A conversation's messages, reloaded whenever any message changes. */
    fun messagesOf(threadId: Long): Flow<List<Message>> = flow {
        if (threadId < 0) {
            emit(emptyList())
            return@flow
        }
        emit(repository.messages(threadId))
        repository.changes().conflate().collect { emit(repository.messages(threadId)) }
    }

    fun send(address: String, text: String) {
        viewModelScope.launch(Dispatchers.IO) { SmsSender(getApplication()).send(address, text) }
    }

    /** Sends a failed message again, in place of the failed one. */
    fun retry(message: Message) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteMessage(message.id)
            SmsSender(getApplication()).send(message.address, message.body)
        }
    }

    fun markRead(threadId: Long) {
        if (threadId < 0) return
        viewModelScope.launch {
            repository.markRead(threadId)
            MessageNotifications.cancel(getApplication(), threadId)
        }
    }

    fun deleteConversation(threadId: Long) {
        viewModelScope.launch { repository.deleteConversation(threadId) }
    }

    fun deleteMessage(id: Long) {
        viewModelScope.launch { repository.deleteMessage(id) }
    }

    /** Sets the palette of every Seca app; null follows the wallpaper. */
    fun setPalette(palette: SecaPalette?) {
        viewModelScope.launch { sharedProfiles.setPalette(palette?.name) }
    }

    // Numbers are read here, off the main thread, before the screen sees them:
    // the lists then only look results up, and scrolling never waits on parsing.
    private suspend fun loadConversations() {
        val list = repository.conversations()
        withContext(Dispatchers.Default) { list.forEach { numbers.prepare(it.address) } }
        _ui.update { it.copy(conversations = list, loaded = true) }
    }

    private suspend fun loadContacts() {
        val list = contacts.contacts()
        val index = withContext(Dispatchers.Default) {
            list.forEach { contact -> contact.phoneNumbers.forEach { numbers.prepare(it.raw) } }
            NumberIndex(list, numbers)
        }
        _ui.update { it.copy(contacts = list, index = index) }
    }

    private suspend fun loadProfiles() {
        val profiles = sharedProfiles.load()
        _ui.update { it.copy(profiles = profiles) }
    }
}
