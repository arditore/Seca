package com.seca.messages

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.seca.core.contacts.ContactsRepository
import com.seca.core.contacts.NumberIndex
import com.seca.core.contacts.PhoneNumbers
import com.seca.core.contacts.SharedProfiles
import com.seca.core.contacts.SharedProfilesClient
import com.seca.core.design.SecaPalette
import com.seca.core.link.LinkSettings
import com.seca.core.link.SecaLink
import com.seca.core.link.TorAccess
import com.seca.core.link.relay.PublishResult
import com.seca.core.model.Profile
import com.seca.core.model.SecaContact
import com.seca.core.model.backup.BackupCipher
import com.seca.messages.link.LinkService
import com.seca.messages.sms.BackupMessage
import com.seca.messages.sms.Conversation
import com.seca.messages.sms.LinkSms
import com.seca.messages.sms.Message
import com.seca.messages.sms.MessageNotifications
import com.seca.messages.sms.MessagesRepository
import com.seca.messages.sms.ScheduledMessage
import com.seca.messages.sms.ScheduledMessages
import com.seca.messages.sms.SmsSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/** Searching the text of every message waits for this pause in typing. */
private const val SEARCH_PAUSE_MILLIS = 250L

/** The app's screens; the last entry of the back stack is the one shown. */
sealed interface MessagesScreen {
    data object Home : MessagesScreen

    /** [threadId] is -1 when the conversation does not exist yet; [draft] comes from a link. */
    data class Conversation(val threadId: Long, val address: String, val draft: String = "") : MessagesScreen
    data object NewMessage : MessagesScreen
    data object Settings : MessagesScreen
    data object Archived : MessagesScreen
    data object Relays : MessagesScreen
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
    /** Pinned conversations, in pinning order. */
    val pinned: List<Long> = emptyList(),
    val archived: Set<Long> = emptySet(),
    /** Conversations filed as advertising. */
    val spam: Set<Long> = emptySet(),
    /** Messages whose text matches the search, newest first. */
    val searchHits: List<Message> = emptyList(),
    /** Messages waiting for their time, soonest first. */
    val scheduled: List<ScheduledMessage> = emptyList(),
    val link: LinkUi = LinkUi(),
) {
    /** What is waiting to be sent to [address], however the number is written. */
    fun scheduledFor(address: String): List<ScheduledMessage> {
        if (scheduled.isEmpty()) return emptyList()
        val key = numbers.key(address)
        return scheduled.filter { numbers.key(it.address) == key }
    }

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

    private val link = SecaLink(application)
    private var linkLoaded = false
    private var publishing: Job? = null

    private var watchingNetwork: Job? = null

    /** Shows Seca Link as the owner left it, and publishes the keys again when a week has passed. */
    private fun loadLink() {
        _ui.update {
            it.copy(
                link = it.link.copy(
                    enabled = link.settings.enabled,
                    relays = link.settings.relays(),
                    readReceipts = link.settings.readReceipts,
                    typingIndicator = link.settings.typingIndicator,
                    useTor = link.settings.useTor,
                ),
            )
        }
        if (!link.settings.enabled) return
        watchNetwork()
        if (link.settings.useTor) refreshTor()
        if (link.settings.publishDue()) {
            publishPrekeys()
        } else {
            viewModelScope.launch { showFingerprint() }
        }
    }

    fun setReadReceipts(on: Boolean) {
        link.settings.readReceipts = on
        _ui.update { it.copy(link = it.link.copy(readReceipts = on)) }
    }

    fun setTypingIndicator(on: Boolean) {
        link.settings.typingIndicator = on
        _ui.update { it.copy(link = it.link.copy(typingIndicator = on)) }
    }

    /** Routes Seca Link through Tor, with Orbot, or back to a direct connection; listening reconnects at once. */
    fun setUseTor(on: Boolean) {
        val context = getApplication<Application>()
        link.settings.useTor = on
        _ui.update { it.copy(link = it.link.copy(useTor = on, orbotRunning = null)) }
        if (on) TorAccess(context).requestStart()
        if (link.settings.enabled) LinkService.start(context)
        refreshTor()
    }

    /** Checks again whether Orbot is installed and takes connections. */
    fun refreshTor() {
        viewModelScope.launch(Dispatchers.IO) {
            val tor = TorAccess(getApplication())
            val installed = tor.installed()
            val running = installed && tor.state() == TorAccess.State.Ready
            _ui.update { it.copy(link = it.link.copy(orbotInstalled = installed, orbotRunning = running)) }
        }
    }

    fun openOrbot() = TorAccess(getApplication()).open()

    fun setLinkEnabled(on: Boolean) {
        link.settings.enabled = on
        _ui.update { it.copy(link = it.link.copy(enabled = on, statuses = emptyMap())) }
        // Listening to the relays starts and stops with Seca Link.
        links.listen(on)
        if (on) {
            watchNetwork()
            publishPrekeys()
            // Contacts who invited this phone while Seca Link was off get their answer, once the keys are out.
            viewModelScope.launch(Dispatchers.IO) {
                publishing?.join()
                LinkSms.connectWaiting(getApplication())
            }
        } else {
            watchingNetwork?.cancel()
        }
    }

    /** False without a connection, or when the owner has not let this app on the network. */
    fun networkAvailable(): Boolean = link.network.available()

    /** Follows network access while Seca Link is on: keys that could not leave go as soon as they can. */
    private fun watchNetwork() {
        if (watchingNetwork?.isActive == true) return
        watchingNetwork = viewModelScope.launch {
            link.network.changes().collect { online ->
                val wasOffline = _ui.value.link.offline
                _ui.update { it.copy(link = it.link.copy(offline = !online)) }
                if (online && wasOffline) publishPrekeys()
            }
        }
    }

    /** Publishes this phone's pre-keys on every relay, showing each relay's answer as it arrives. */
    fun publishPrekeys() {
        if (publishing?.isActive == true) return
        publishing = viewModelScope.launch {
            if (!showFingerprint()) return@launch
            if (!link.network.available()) {
                _ui.update { it.copy(link = it.link.copy(offline = true, statuses = emptyMap())) }
                return@launch
            }
            val relays = link.settings.relays()
            _ui.update {
                it.copy(link = it.link.copy(relays = relays, statuses = relays.associateWith { RelayStatus(RelayState.Publishing) }))
            }
            link.publishPrekeys()
                .catch { error -> _ui.update { it.copy(link = it.link.copy(error = text(R.string.link_publish_failed, error.message.orEmpty()))) } }
                .collect { (url, result) ->
                    val status = when (result) {
                        PublishResult.Accepted -> RelayStatus(RelayState.Published)
                        is PublishResult.Refused -> RelayStatus(RelayState.Refused, result.reason)
                        is PublishResult.Unreachable -> RelayStatus(RelayState.Unreachable, result.reason)
                    }
                    _ui.update { it.copy(link = it.link.copy(statuses = it.link.statuses + (url to status))) }
                }
        }
    }

    /** Creates the keys the first time. False when the phone's Keystore refused. */
    private suspend fun showFingerprint(): Boolean {
        // The Keystore reports its failures in several ways, runtime exceptions included.
        val identity = runCatching { link.identity() }.getOrElse { error ->
            _ui.update { it.copy(link = it.link.copy(error = text(R.string.link_keys_unavailable, error.message.orEmpty()))) }
            return false
        }
        _ui.update { it.copy(link = it.link.copy(fingerprint = identity.fingerprint, error = null)) }
        return true
    }

    /** False when [input] cannot be a relay address. */
    fun addRelay(input: String): Boolean {
        val url = LinkSettings.normalize(input) ?: return false
        val relays = link.settings.relays()
        if (url !in relays) {
            link.settings.setRelays(relays + url)
            _ui.update { it.copy(link = it.link.copy(relays = link.settings.relays())) }
            if (link.settings.enabled) publishPrekeys()
        }
        return true
    }

    fun removeRelay(url: String) {
        link.settings.setRelays(link.settings.relays() - url)
        _ui.update { it.copy(link = it.link.copy(relays = link.settings.relays(), statuses = it.link.statuses - url)) }
    }

    fun resetRelays() {
        link.settings.resetRelays()
        _ui.update { it.copy(link = it.link.copy(relays = link.settings.relays())) }
        if (link.settings.enabled) publishPrekeys()
    }

    /** (Re)loads what the granted permissions allow, then follows every change. */
    fun start(smsGranted: Boolean, contactsGranted: Boolean) {
        if (!linkLoaded) {
            linkLoaded = true
            loadLink()
        }
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
                    // Seca Link messages change the list too: a newer snippet, more unread.
                    links.changes(repository).conflate().collect { loadConversations() }
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

    /** Opens the conversation with [address]; from "New message", it takes that screen's place. */
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

    private val conversationPrefs = ConversationPrefs(application)
    private var searching: Job? = null

    /** Filters the list at once, and searches the text of every message once typing pauses. */
    fun setQuery(query: String) {
        _ui.update { it.copy(query = query) }
        searching?.cancel()
        val text = query.trim()
        if (text.length < 2) {
            _ui.update { it.copy(searchHits = emptyList()) }
            return
        }
        searching = viewModelScope.launch {
            delay(SEARCH_PAUSE_MILLIS)
            val hits = repository.search(text)
            _ui.update { it.copy(searchHits = hits) }
        }
    }

    fun setPinned(threadId: Long, pinned: Boolean) {
        conversationPrefs.setPinned(threadId, pinned)
        refreshConversationPrefs()
    }

    fun setArchived(threadId: Long, archived: Boolean) {
        conversationPrefs.setArchived(threadId, archived)
        refreshConversationPrefs()
    }

    /** Files a conversation as advertising, or takes it back out, which trusts it from then on. */
    fun setSpam(threadId: Long, spam: Boolean) {
        conversationPrefs.setSpam(threadId, spam)
        refreshConversationPrefs()
        if (spam) MessageNotifications.cancel(getApplication(), threadId)
    }

    private fun refreshConversationPrefs() {
        _ui.update {
            it.copy(pinned = conversationPrefs.pinned(), archived = conversationPrefs.archived(), spam = conversationPrefs.spam())
        }
    }

    private val scheduledMessages = ScheduledMessages(application)

    /** Sends [text] to [address] at [at], even when the app is closed by then. */
    fun schedule(address: String, text: String, at: Long) {
        val body = text.trim()
        if (body.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            scheduledMessages.add(address, body, at)
            refreshScheduled()
        }
    }

    fun cancelScheduled(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            scheduledMessages.remove(id)
            refreshScheduled()
        }
    }

    fun sendScheduledNow(message: ScheduledMessage) {
        viewModelScope.launch(Dispatchers.IO) {
            scheduledMessages.remove(message.id)
            SmsSender(getApplication()).send(message.address, message.body)
            refreshScheduled()
        }
    }

    private fun refreshScheduled() {
        _ui.update { it.copy(scheduled = scheduledMessages.all()) }
    }

    private val links = LinkConversations(application)

    /** A conversation's messages, SMS and Seca Link together, reloaded whenever either changes. */
    fun messagesOf(threadId: Long, address: String): Flow<List<Message>> = links.conversation(threadId, address, repository)

    /** Goes encrypted through Seca Link when the contact is connected, answering [replyTo] when given; by SMS otherwise. */
    fun send(address: String, text: String, replyTo: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!links.sendText(address, text, replyTo)) SmsSender(getApplication()).send(address, text)
        }
    }

    /** Sends the voice message recorded at [path] through Seca Link, then lets the recording go. */
    fun sendVoice(address: String, path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val sent = links.sendVoice(address, path)
            java.io.File(path).delete()
            if (!sent) withContext(Dispatchers.Main) { toast(text(R.string.voice_not_sent)) }
        }
    }

    /** Reacts to a Seca Link message, or takes the same reaction back. */
    fun react(message: Message, emoji: String) {
        val id = message.linkId ?: return
        viewModelScope.launch(Dispatchers.IO) { links.react(id, emoji) }
    }

    /** Sets how long new messages with [address] are kept, on both phones. */
    fun setTimer(address: String, seconds: Int) {
        viewModelScope.launch(Dispatchers.IO) { links.setTimer(address, seconds) }
    }

    /** Lets go of the messages whose time is up. */
    fun sweepExpired() {
        viewModelScope.launch(Dispatchers.IO) { links.sweepExpired() }
    }

    /** Sends a photo through Seca Link, which only an encrypted conversation carries. */
    fun sendPhoto(address: String, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!links.sendPhoto(address, uri)) withContext(Dispatchers.Main) { toast(text(R.string.photo_not_sent)) }
        }
    }

    /**
     * Offers Seca Link to [address] on its own: a data SMS a phone without Seca
     * never shows, at most once a day, and nothing at all once the contact is
     * connected. Two phones that both have Seca Link find each other without the
     * owner asking for anything.
     */
    fun offerLink(address: String) {
        if (!link.settings.enabled) return
        viewModelScope.launch(Dispatchers.IO) { LinkSms.inviteIfDue(getApplication(), address) }
    }

    /**
     * Looks, as the conversation opens, at whether [address] still has Seca Link:
     * a contact who turned it off or took Seca off their phone stops publishing
     * their keys, and the conversation must stop calling itself encrypted.
     */
    fun checkLink(address: String) {
        if (!link.settings.enabled) return
        viewModelScope.launch(Dispatchers.IO) {
            val number = LinkSms.keyOf(getApplication(), address) ?: return@launch
            runCatching { link.checkPeerStillThere(number) }
        }
    }

    /** Sends the owner's Seca Link invitation to [address] now, also as a text, which every network carries. */
    fun inviteToLink(address: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val sent = LinkSms.inviteNow(getApplication(), address)
            withContext(Dispatchers.Main) {
                toast(text(if (sent) R.string.invitation_sent else R.string.invitation_failed))
            }
        }
    }

    /** Sends a failed message again: through Seca Link if it went that way, else as an SMS in place of the failed one. */
    fun retry(message: Message) {
        viewModelScope.launch(Dispatchers.IO) {
            val linkId = message.linkId
            if (linkId != null) {
                links.retry(linkId)
            } else {
                repository.deleteMessage(message.id)
                SmsSender(getApplication()).send(message.address, message.body)
            }
        }
    }

    /** Sends a Seca Link message that could not leave as an ordinary, unencrypted SMS, as the owner chose. */
    fun sendAsSms(message: Message) {
        viewModelScope.launch(Dispatchers.IO) {
            message.linkId?.let(links::delete)
            SmsSender(getApplication()).send(message.address, message.body)
        }
    }

    fun markRead(threadId: Long, address: String? = null) {
        viewModelScope.launch {
            if (threadId >= 0) {
                repository.markRead(threadId)
                MessageNotifications.cancel(getApplication(), threadId)
            }
            address?.let { withContext(Dispatchers.IO) { links.markRead(it) } }
        }
    }

    /** Tells the contact the owner is writing, when both use Seca Link and the owner allows it. */
    fun typing(address: String) {
        viewModelScope.launch(Dispatchers.IO) { links.typing(address) }
    }

    fun deleteConversation(threadId: Long, address: String? = null) {
        viewModelScope.launch {
            repository.deleteConversation(threadId)
            address?.let { withContext(Dispatchers.IO) { links.deleteConversation(it) } }
        }
    }

    fun deleteMessage(message: Message) {
        viewModelScope.launch {
            val linkId = message.linkId
            if (linkId != null) withContext(Dispatchers.IO) { links.delete(linkId) } else repository.deleteMessage(message.id)
        }
    }

    /** Writes every message to [uri], encrypted with [passphrase], which is wiped from memory once used. */
    fun exportBackup(uri: Uri, passphrase: CharArray) {
        viewModelScope.launch {
            val messages = repository.allForBackup()
            val json = JSONObject()
                .put("app", "seca-messages")
                .put("version", 1)
                .put(
                    "messages",
                    JSONArray().apply {
                        messages.forEach { m ->
                            put(
                                JSONObject()
                                    .put("address", m.address)
                                    .put("body", m.body)
                                    .put("date", m.date)
                                    .put("dateSent", m.dateSent)
                                    .put("type", m.type)
                                    .put("read", m.read),
                            )
                        }
                    },
                )
            val sealed = withContext(Dispatchers.Default) {
                BackupCipher.encrypt(json.toString().toByteArray(Charsets.UTF_8), passphrase).also { passphrase.fill(' ') }
            }
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { it.write(sealed) } != null
                }.getOrDefault(false)
            }
            toast(if (written) text(R.string.backup_done, quantity(R.plurals.messages_count, messages.size)) else text(R.string.backup_failed))
        }
    }

    /** Restores a backup made by [exportBackup]; messages already on the phone are skipped. */
    fun importBackup(uri: Uri, passphrase: CharArray) {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            val plain = bytes?.let { withContext(Dispatchers.Default) { BackupCipher.decrypt(it, passphrase) } }
            passphrase.fill(' ')
            val json = plain?.let { runCatching { JSONObject(it.toString(Charsets.UTF_8)) }.getOrNull() }
            if (json == null || json.optString("app") != "seca-messages") {
                toast(
                    when {
                        bytes == null -> text(R.string.unreadable_file)
                        plain == null -> text(R.string.wrong_password)
                        else -> text(R.string.not_messages_backup)
                    },
                )
                return@launch
            }
            val list = json.optJSONArray("messages") ?: JSONArray()
            val messages = (0 until list.length()).map { index ->
                val item = list.getJSONObject(index)
                BackupMessage(
                    address = item.optString("address"),
                    body = item.optString("body"),
                    date = item.optLong("date"),
                    dateSent = item.optLong("dateSent"),
                    type = item.optInt("type"),
                    read = item.optBoolean("read", true),
                )
            }
            val added = repository.restore(messages)
            toast(text(R.string.backup_restored, quantity(R.plurals.messages_added, added)))
        }
    }

    private fun text(@StringRes id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    private fun quantity(@PluralsRes id: Int, count: Int): String =
        getApplication<Application>().resources.getQuantityString(id, count, count)

    private fun toast(text: String) {
        Toast.makeText(getApplication(), text, Toast.LENGTH_LONG).show()
    }

    /** Sets the palette of every Seca app; null follows the wallpaper. */
    fun setPalette(palette: SecaPalette?) {
        viewModelScope.launch { sharedProfiles.setPalette(palette?.name) }
    }

    // Numbers are read here, off the main thread, before the screen sees them:
    // the lists then only look results up, and scrolling never waits on parsing.
    private suspend fun loadConversations() {
        val list = withContext(Dispatchers.IO) {
            // A Seca Link handshake written as text reads as a short notice rather than its code.
            links.withLatest(repository.conversations()).map { it.copy(snippet = LinkSms.snippetOf(it.snippet)) }
        }
        withContext(Dispatchers.Default) { list.forEach { numbers.prepare(it.address) } }
        // A message arriving in an archived conversation takes it out of the archive.
        _ui.update {
            it.copy(
                conversations = list,
                loaded = true,
                pinned = conversationPrefs.pinned(),
                archived = conversationPrefs.archived(),
                spam = conversationPrefs.spam(),
                // A scheduled message that just left shows up as a change to the messages.
                scheduled = scheduledMessages.all(),
            )
        }
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
