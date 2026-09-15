package com.seca.messages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.seca.core.design.SecaIcons
import com.seca.core.design.component.SecaEmptyState
import com.seca.core.design.component.SecaQrCode
import com.seca.core.design.component.SecaTopBar
import com.seca.core.link.SafetyNumber
import kotlin.io.encoding.Base64
import androidx.lifecycle.viewmodel.compose.viewModel as screenViewModel
import androidx.compose.ui.res.stringResource

private const val DIGITS_PER_GROUP = 5
private const val GROUPS_PER_ROW = 4

/**
 * The safety number of a conversation: sixty digits and a QR code, the same on
 * both phones when no one sits between them.
 */
@Composable
internal fun SafetyNumberScreen(route: SafetyNumberRoute, ui: MessagesUi, viewModel: MessagesViewModel) {
    val links: LinkPeersViewModel = screenViewModel()
    val peer = rememberLinkPeer(route.address)
    val name = ui.nameOf(route.address)
    val safety by produceState<SafetyNumber?>(null, peer?.identityKey) { value = links.safetyNumber(route.address) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { SecaTopBar(title = stringResource(R.string.safety_number), onBack = { viewModel.back() }) },
    ) { padding ->
        val number = safety
        if (peer?.ready != true) {
            // No session yet: the way to open one, face to face.
            ConnectInPerson(route.address, ui, viewModel, Modifier.padding(padding))
            return@Scaffold
        }
        if (number == null) {
            SecaEmptyState(
                icon = SecaIcons.Link,
                title = stringResource(R.string.safety_number_pending),
                description = stringResource(R.string.safety_number_pending_hint, name),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            PersonAvatar(ui.contactOf(route.address), ui, size = 72.dp, expressive = true)
            Text(
                text = name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 12.dp),
            )
            VerifiedMark(peer.verified, Modifier.padding(top = 8.dp))
            SecaQrCode(
                content = Base64.encode(number.code),
                contentDescription = stringResource(R.string.safety_code_to_scan),
                modifier = Modifier.padding(top = 24.dp),
            )
            SafetyDigits(number.digits, Modifier.padding(top = 24.dp))
            Text(
                text = stringResource(R.string.compare_hint, name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp),
            )
            Text(
                text = stringResource(R.string.encrypted_to_hint, name),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(
                onClick = { viewModel.open(SafetyScanRoute(route.address)) },
                modifier = Modifier.padding(top = 24.dp),
            ) { Text(stringResource(R.string.scan_their_code)) }
            if (peer.verified) {
                OutlinedButton(
                    onClick = { links.setVerified(route.address, false) },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text(stringResource(R.string.remove_verification)) }
            } else {
                OutlinedButton(
                    onClick = { links.setVerified(route.address, true) },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text(stringResource(R.string.mark_verified)) }
            }
        }
    }
}

@Composable
private fun VerifiedMark(verified: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = if (verified) colors.primaryContainer else colors.surfaceContainerHigh,
        contentColor = if (verified) colors.onPrimaryContainer else colors.onSurfaceVariant,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Icon(if (verified) SecaIcons.Check else SecaIcons.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(
                text = stringResource(if (verified) R.string.verified else R.string.not_verified),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

/** Twelve groups of five digits, four to a line, easy to read aloud. */
@Composable
private fun SafetyDigits(digits: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        digits.chunked(DIGITS_PER_GROUP).chunked(GROUPS_PER_ROW).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { group ->
                    Text(
                        text = group,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
