package com.seca.contacts

import android.content.pm.PackageManager
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
import androidx.compose.ui.unit.dp
import com.seca.core.design.SecaIcons
import com.seca.core.design.component.SecaAvatar
import com.seca.core.design.component.SecaGroupItem
import com.seca.core.design.component.SecaHint
import com.seca.core.design.component.SecaSectionLabel
import com.seca.core.design.component.SecaTopBar
import com.seca.core.model.initialsOf

/** The seed of "Ma fiche"'s avatar shape, the same in the list and on the card. */
internal const val MY_CARD_SEED = "ma-fiche"

/**
 * "Ma fiche": the owner's name, photo and numbers. The SIM lines are read only
 * when the owner asks; many operators leave the number off the card, so
 * numbers can also be typed in. Everything is saved when leaving the screen.
 */
@Composable
internal fun MyCardScreen(ui: ContactsUi, viewModel: ContactsViewModel) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(ui.myCard.name) }
    val typed = remember { mutableStateListOf<String>().apply { addAll(ui.myCard.numbers.ifEmpty { listOf("") }) } }
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { SecaTopBar(title = "Ma fiche", onBack = leave) },
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
                        modifier = Modifier.clickable(onClickLabel = "Changer la photo") {
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
                        Text("Retirer la photo")
                    }
                }
            }

            SecaSectionLabel("Nom")
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Votre nom") },
                placeholder = { Text("Ma fiche") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            SecaSectionLabel("Cartes SIM")
            when {
                !simAllowed -> SecaGroupItem(index = 0, count = 1, onClick = { simAccess.launch(SimPermissions) }) {
                    SimRow(title = "Lire les numéros des cartes SIM", subtitle = "Lus sur ce téléphone, jamais envoyés")
                }
                ui.simLines.isEmpty() -> SecaHint("Aucune carte SIM active.")
                else -> ui.simLines.forEachIndexed { index, line ->
                    SecaGroupItem(index = index, count = ui.simLines.size) {
                        SimRow(
                            title = line.number?.let(ui.numbers::display) ?: "Numéro absent de la carte",
                            subtitle = "${line.label} · ${if (line.isEsim) "eSIM" else "SIM ${line.slot + 1}"}",
                        )
                    }
                }
            }
            if (simAllowed && ui.simLines.any { it.number == null }) {
                SecaHint("Beaucoup d'opérateurs n'inscrivent pas le numéro sur la carte : ajoutez-le ci-dessous.")
            }

            SecaSectionLabel("Mes numéros")
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                typed.forEachIndexed { index, value ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { typed[index] = it },
                            label = { Text("Numéro") },
                            supportingText = ui.numbers.describe(value)?.let { { Text(it) } },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { if (typed.size > 1) typed.removeAt(index) else typed[index] = "" }) {
                            Icon(SecaIcons.Close, contentDescription = "Retirer ce numéro")
                        }
                    }
                }
                TextButton(onClick = { typed.add("") }) {
                    Icon(SecaIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Ajouter un numéro", modifier = Modifier.padding(start = 8.dp))
                }
            }
            SecaHint("Ma fiche reste dans Seca Contacts : elle n'est ni partagée avec les autres applications, ni synchronisée.")
        }
    }
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
