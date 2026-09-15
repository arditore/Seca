package com.seca.contacts

import android.content.pm.PackageManager
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.seca.core.contacts.ContactField
import com.seca.core.contacts.VCard
import com.seca.core.contacts.VCardContact
import com.seca.core.design.SecaIcons
import com.seca.core.design.component.SecaAvatar
import com.seca.core.design.component.SecaGroupItem
import com.seca.core.design.component.SecaHint
import com.seca.core.design.component.SecaQrCode
import com.seca.core.design.component.SecaSectionLabel
import com.seca.core.design.component.SecaTopBar
import com.seca.core.model.initialsOf
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalResources
import com.seca.core.contacts.describe

/** The seed of "My card"'s avatar shape, the same in the list and on the card. */
internal const val MY_CARD_SEED = "ma-fiche"

/**
 * "My card": the owner's name, photo and numbers. The SIM lines are read only
 * when the owner asks; many operators leave the number off the card, so
 * numbers can also be typed in. Everything is saved when leaving the screen.
 * A QR code hands the card to someone standing by, without any network.
 */
@Composable
internal fun MyCardScreen(ui: ContactsUi, viewModel: ContactsViewModel) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(ui.myCard.name) }
    val typed = remember { mutableStateListOf<String>().apply { addAll(ui.myCard.numbers.ifEmpty { listOf("") }) } }
    var sharing by remember { mutableStateOf(false) }
    val shareable = myCardNumbers(ui, typed)
    val leave: () -> Unit = {
        viewModel.saveMyCard(name, typed.toList())
        viewModel.back()
    }
    BackHandler(onBack = leave)
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::setMyPhoto)
    }
    val simAccess = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.refreshSimLines()
    }
    val simAllowed = SimPermissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    val myCardLabel = stringResource(R.string.my_card)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { SecaTopBar(title = myCardLabel, onBack = leave) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            ) {
                // The system photo picker hands over the one chosen image: no access to the gallery.
                Box {
                    SecaAvatar(
                        initials = initialsOf(name),
                        photoUri = null,
                        size = 136.dp,
                        seed = MY_CARD_SEED,
                        expressive = true,
                        photo = ui.myPhoto,
                        modifier = Modifier.clickable(onClickLabel = stringResource(R.string.change_photo)) {
                            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    ) {
                        Icon(
                            SecaIcons.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                if (ui.myPhoto != null) {
                    TextButton(onClick = viewModel::removeMyPhoto, modifier = Modifier.padding(top = 8.dp)) {
                        Text(stringResource(R.string.remove_photo))
                    }
                }
                FilledTonalButton(
                    onClick = { sharing = true },
                    enabled = shareable.isNotEmpty(),
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Icon(SecaIcons.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.share_qr), modifier = Modifier.padding(start = 8.dp))
                }
            }

            SecaSectionLabel(stringResource(R.string.name))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.your_name)) },
                placeholder = { Text(myCardLabel) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            SecaSectionLabel(stringResource(R.string.sim_cards))
            when {
                !simAllowed -> SecaGroupItem(index = 0, count = 1, onClick = { simAccess.launch(SimPermissions) }) {
                    SimRow(title = stringResource(R.string.read_sim_numbers), subtitle = stringResource(R.string.read_sim_numbers_hint))
                }
                ui.simLines.isEmpty() -> SecaHint(stringResource(R.string.no_sim))
                else -> ui.simLines.forEachIndexed { index, line ->
                    SecaGroupItem(index = index, count = ui.simLines.size) {
                        SimRow(
                            title = line.number?.let(ui.numbers::display) ?: stringResource(R.string.sim_no_number),
                            subtitle = "${line.label} · ${if (line.isEsim) "eSIM" else stringResource(R.string.sim_slot, line.slot + 1)}",
                        )
                    }
                }
            }
            if (simAllowed && ui.simLines.any { it.number == null }) {
                SecaHint(stringResource(R.string.sim_number_missing_hint))
            }

            SecaSectionLabel(stringResource(R.string.my_numbers))
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                typed.forEachIndexed { index, value ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { typed[index] = it },
                            label = { Text(stringResource(R.string.number)) },
                            supportingText = ui.numbers.describe(value, LocalResources.current)?.let { { Text(it) } },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { if (typed.size > 1) typed.removeAt(index) else typed[index] = "" }) {
                            Icon(SecaIcons.Close, contentDescription = stringResource(R.string.remove_number))
                        }
                    }
                }
                TextButton(onClick = { typed.add("") }) {
                    Icon(SecaIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.add_number), modifier = Modifier.padding(start = 8.dp))
                }
            }
            SecaHint(stringResource(R.string.my_card_privacy))
        }
    }

    if (sharing) {
        val card = remember(name, shareable) { myCardVCard(name, shareable) }
        AlertDialog(
            onDismissRequest = { sharing = false },
            title = { Text(name.ifBlank { myCardLabel }) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    SecaQrCode(card, contentDescription = stringResource(R.string.my_card_qr))
                    Text(
                        text = stringResource(R.string.my_card_qr_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { sharing = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}

/** The owner's numbers, from the SIM cards and typed in, each once and in international format. */
private fun myCardNumbers(ui: ContactsUi, typed: List<String>): List<String> =
    (ui.simLines.mapNotNull { it.number } + typed)
        .map(String::trim)
        .filter(String::isNotEmpty)
        .map { ui.numbers.toE164(it) ?: it }
        .distinct()

/** A vCard with the name and numbers only: small enough for a QR code any camera reads. */
private fun myCardVCard(name: String, numbers: List<String>): String {
    val shown = name.trim()
    val contact = VCardContact(
        givenName = shown.substringBefore(' '),
        familyName = shown.substringAfter(' ', ""),
        displayName = shown,
        phones = numbers.map { ContactField(id = null, value = it, type = Phone.TYPE_MOBILE) },
        emails = emptyList(),
    )
    return VCard.write(listOf(contact))
}

@Composable
private fun SimRow(title: String, subtitle: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Icon(SecaIcons.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
