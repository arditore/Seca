package com.seca.messages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.seca.core.design.SecaIcons
import com.seca.core.design.component.SecaEmptyState
import com.seca.core.design.component.SecaQrCode
import androidx.lifecycle.viewmodel.compose.viewModel as screenViewModel

/**
 * Before a session with [address]: this phone's code to show, and the camera
 * to read the contact's. Face to face, the connection needs no SMS to get
 * through; one scan is enough, the answer then leaves by SMS.
 */
@Composable
internal fun ConnectInPerson(address: String, ui: MessagesUi, viewModel: MessagesViewModel, modifier: Modifier = Modifier) {
    val links: LinkPeersViewModel = screenViewModel()
    val name = ui.nameOf(address)
    if (!ui.link.enabled) {
        SecaEmptyState(
            icon = SecaIcons.Link,
            title = "Seca Link est désactivé",
            description = "Activez-le dans les réglages pour chiffrer vos conversations avec $name.",
            modifier = modifier,
            action = { Button(onClick = { viewModel.open(MessagesScreen.Settings) }) { Text("Ouvrir les réglages") } },
        )
        return
    }
    val code by produceState<String?>(initialValue = null) { value = links.myCode() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        PersonAvatar(ui.contactOf(address), ui, size = 72.dp, expressive = true)
        Text(
            text = "Connecter en face à face",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = "Montrez ce code à $name, ou scannez le sien : vos messages seront chiffrés sans attendre de SMS.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        val shown = code
        if (shown == null) {
            CircularProgressIndicator(Modifier.padding(top = 48.dp).size(48.dp))
        } else {
            SecaQrCode(content = shown, contentDescription = "Votre code Seca Link", modifier = Modifier.padding(top = 24.dp), size = 220.dp)
        }
        Button(
            onClick = { viewModel.open(SafetyScanRoute(address, connect = true)) },
            modifier = Modifier.padding(top = 24.dp),
        ) { Text("Scanner son code") }
        OutlinedButton(
            onClick = { viewModel.inviteToLink(address) },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Inviter par SMS plutôt") }
        Text(
            text = "Un seul scan suffit : la réponse part ensuite par SMS. Seca Messages doit être installé et Seca Link activé sur les deux téléphones.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
