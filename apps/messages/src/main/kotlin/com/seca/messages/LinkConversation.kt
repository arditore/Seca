package com.seca.messages

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seca.core.design.SecaIcons
import com.seca.core.link.LinkPeer
import com.seca.core.link.LinkPeers
import com.seca.core.link.SafetyNumber
import com.seca.core.link.SecaLink
import com.seca.core.link.handshake.Handshake
import com.seca.messages.sms.LinkSms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The safety number screen for the conversation with [address]. */
data class SafetyNumberRoute(val address: String) : MessagesScreen

/** Seca Link as the conversations see it: which numbers are connected, verified, or have a new key. */
class LinkPeersViewModel(application: Application) : AndroidViewModel(application) {

    private val link = SecaLink(application)
    private val _peers = MutableStateFlow<Map<String, LinkPeer>>(emptyMap())
    val peers: StateFlow<Map<String, LinkPeer>> = _peers.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            load()
            // The SMS receivers change peers too, while the app is open.
            LinkPeers.changes.collect { load() }
        }
    }

    private fun load() {
        _peers.value = runCatching { link.peers.all() }.getOrDefault(emptyMap())
    }

    /** The number as Seca Link names the contact; null for a short code. */
    fun keyOf(address: String): String? = LinkSms.keyOf(getApplication(), address)

    suspend fun safetyNumber(address: String): SafetyNumber? =
        keyOf(address)?.let { runCatching { link.safetyNumber(it) }.getOrNull() }

    fun setVerified(address: String, verified: Boolean) {
        val key = keyOf(address) ?: return
        viewModelScope.launch(Dispatchers.IO) { link.setVerified(key, verified) }
    }

    fun acknowledgeKeyChange(address: String) {
        val key = keyOf(address) ?: return
        viewModelScope.launch(Dispatchers.IO) { link.acknowledgeKeyChange(key) }
    }

    /** This phone's code, for a contact to scan in person; null while Seca Link is off. */
    suspend fun myCode(): String? = withContext(Dispatchers.IO) {
        val text = runCatching { link.myHandshake()?.text() }.getOrNull() ?: return@withContext null
        // Only the code itself: a smaller QR code, quicker to read.
        CodeInText.find(text)?.value
    }

    /** What came of a code scanned on the contact's phone. */
    sealed interface Scanned {
        data object NotSeca : Scanned
        data object Connected : Scanned
        data class Failed(val reason: String) : Scanned
    }

    /** A code scanned on [address]'s phone: the session opens, and this phone's answer goes back by SMS. */
    suspend fun connectScanned(address: String, text: String): Scanned {
        val handshake = Handshake.fromText(text) ?: return Scanned.NotSeca
        return when (val received = withContext(Dispatchers.IO) { LinkSms.receive(getApplication(), address, handshake, byText = true) }) {
            is SecaLink.Received.Connected -> Scanned.Connected
            is SecaLink.Received.Failed -> Scanned.Failed(received.reason)
            SecaLink.Received.Ignored -> Scanned.Failed("Activez Seca Link pour vous connecter")
            null -> Scanned.Failed("Ce numéro ne peut pas utiliser Seca Link")
        }
    }

    /** Tries the connection with [number] again now; the answer leaves once it opens. */
    fun retry(number: String) {
        viewModelScope.launch(Dispatchers.IO) { LinkSms.retry(getApplication(), number) }
    }

    /** Sends the invitation to [number] again, also as a text. */
    fun invite(number: String) {
        viewModelScope.launch(Dispatchers.IO) { LinkSms.inviteNow(getApplication(), number) }
    }

    private companion object {
        val CodeInText = Regex("seca-link:\\S+")
    }
}

/** What Seca Link knows of the contact at [address], kept up to date. */
@Composable
internal fun rememberLinkPeer(address: String): LinkPeer? {
    val links: LinkPeersViewModel = viewModel()
    val peers by links.peers.collectAsState()
    val key = remember(address) { links.keyOf(address) }
    return key?.let { peers[it] }
}

/** Shown above a conversation when the contact's key changed, until the owner acknowledges it. */
@Composable
internal fun KeyChangedBanner(address: String, name: String, onVerify: () -> Unit) {
    val links: LinkPeersViewModel = viewModel()
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.errorContainer,
        contentColor = colors.onErrorContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(SecaIcons.Shield, contentDescription = null)
                Text(
                    text = "La clé de sécurité de $name a changé",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                text = "Un nouveau téléphone ou une réinstallation l'expliquent souvent. Si vous ne l'attendiez pas, " +
                    "comparez vos numéros de sécurité.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, end = 8.dp),
            )
            Row {
                TextButton(onClick = onVerify) { Text("Vérifier") }
                TextButton(onClick = { links.acknowledgeKeyChange(address) }) { Text("J'ai compris") }
            }
        }
    }
}
