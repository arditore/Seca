package com.seca.messages

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.seca.core.design.SecaIcons
import com.seca.core.design.component.SecaEmptyState
import com.seca.core.design.component.SecaTopBar
import com.seca.core.link.SafetyNumber
import kotlin.io.encoding.Base64
import androidx.lifecycle.viewmodel.compose.viewModel as screenViewModel

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
        topBar = { SecaTopBar(title = "Numéro de sécurité", onBack = { viewModel.back() }) },
    ) { padding ->
        val number = safety
        if (peer?.ready != true || number == null) {
            SecaEmptyState(
                icon = SecaIcons.Link,
                title = "Pas encore connecté",
                description = "Seca Link se connecte à $name après un premier SMS, quand vous l'avez activé tous les deux.",
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
            QrCode(number.code, Modifier.padding(top = 24.dp))
            SafetyDigits(number.digits, Modifier.padding(top = 24.dp))
            Text(
                text = "Comparez ces chiffres avec ceux du téléphone de $name, ou montrez-lui ce code. " +
                    "S'ils sont identiques, personne ne s'interpose entre vous.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp),
            )
            Text(
                text = "Les messages chiffrés par Seca Link arrivent dans une prochaine version : pour l'instant, " +
                    "la conversation passe encore par SMS.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (peer.verified) {
                OutlinedButton(
                    onClick = { links.setVerified(route.address, false) },
                    modifier = Modifier.padding(top = 24.dp),
                ) { Text("Retirer la vérification") }
            } else {
                Button(
                    onClick = { links.setVerified(route.address, true) },
                    modifier = Modifier.padding(top = 24.dp),
                ) { Text("Marquer comme vérifié") }
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
                text = if (verified) "Vérifié" else "Non vérifié",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

/** Dark modules on white, whatever the theme, so any camera reads it. */
@Composable
private fun QrCode(bytes: ByteArray, modifier: Modifier = Modifier) {
    val image = remember(bytes) {
        val matrix = QRCodeWriter().encode(Base64.encode(bytes), BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
        val dark = Color.Black.toArgb()
        val light = Color.White.toArgb()
        val bitmap = createBitmap(matrix.width, matrix.height)
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) bitmap[x, y] = if (matrix[x, y]) dark else light
        }
        bitmap.asImageBitmap()
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White)
            .padding(20.dp),
    ) {
        Image(
            bitmap = image,
            contentDescription = "Code de sécurité à scanner",
            filterQuality = FilterQuality.None,
            modifier = Modifier.size(200.dp),
        )
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
