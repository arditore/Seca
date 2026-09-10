package com.seca.contacts

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.seca.core.contacts.ContactDetail
import com.seca.core.contacts.ContactField
import com.seca.core.contacts.ContactInput
import com.seca.core.design.SecaIcons

@Composable
internal fun EditScreen(
    id: Long?,
    ui: ContactsUi,
    viewModel: ContactsViewModel,
    withWrite: (() -> Unit) -> Unit,
) {
    var existing by remember(id) { mutableStateOf<ContactDetail?>(null) }
    var ready by remember(id) { mutableStateOf(false) }
    var givenName by remember(id) { mutableStateOf("") }
    var familyName by remember(id) { mutableStateOf("") }
    val phones = remember(id) { mutableStateListOf<ContactField>() }
    val emails = remember(id) { mutableStateListOf<ContactField>() }
    var profileId by remember(id) { mutableStateOf(ui.currentProfileId) }

    LaunchedEffect(id) {
        if (id != null) {
            val loaded = viewModel.detail(id)
            existing = loaded
            if (loaded != null) {
                // A contact saved with only a single name still shows that name here.
                givenName = loaded.givenName.ifEmpty { if (loaded.familyName.isEmpty()) loaded.displayName else "" }
                familyName = loaded.familyName
                phones.addAll(loaded.phones)
                emails.addAll(loaded.emails)
                profileId = ui.profileForKey(loaded.lookupKey).id
            }
        }
        if (phones.isEmpty()) phones.add(ContactField(null, "", Phone.TYPE_MOBILE))
        ready = true
    }

    val canSave = ready &&
        (givenName.isNotBlank() || familyName.isNotBlank() || phones.any { it.value.isNotBlank() })

    Scaffold(
        topBar = {
            SimpleTopBar(
                title = if (id == null) "Nouveau contact" else "Modifier",
                onBack = { viewModel.back() },
                navigationIcon = SecaIcons.Close,
            ) {
                TextButton(
                    enabled = canSave,
                    onClick = {
                        val input = ContactInput(givenName, familyName, phones.toList(), emails.toList())
                        withWrite { viewModel.save(existing, input, profileId) }
                    },
                ) { Text("Enregistrer") }
            }
        },
    ) { padding ->
        if (ready) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                OutlinedTextField(
                    value = givenName,
                    onValueChange = { givenName = it },
                    label = { Text("Prénom") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = familyName,
                    onValueChange = { familyName = it },
                    label = { Text("Nom") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                FormLabel("Téléphone")
                phones.forEachIndexed { index, field ->
                    FieldEditor(
                        value = field.value,
                        label = "Numéro",
                        keyboard = KeyboardType.Phone,
                        onChange = { phones[index] = field.copy(value = it) },
                        onRemove = { phones.removeAt(index) },
                    )
                }
                AddFieldButton("Ajouter un numéro") { phones.add(ContactField(null, "", Phone.TYPE_MOBILE)) }

                FormLabel("E-mail")
                emails.forEachIndexed { index, field ->
                    FieldEditor(
                        value = field.value,
                        label = "Adresse e-mail",
                        keyboard = KeyboardType.Email,
                        onChange = { emails[index] = field.copy(value = it) },
                        onRemove = { emails.removeAt(index) },
                    )
                }
                AddFieldButton("Ajouter une adresse e-mail") { emails.add(ContactField(null, "", Email.TYPE_HOME)) }

                FormLabel("Profil")
                ProfilePicker(ui, selectedId = profileId, onSelect = { profileId = it })
            }
        }
    }
}

@Composable
private fun FormLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun FieldEditor(
    value: String,
    label: String,
    keyboard: KeyboardType,
    onChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove) {
            Icon(SecaIcons.Close, contentDescription = "Retirer")
        }
    }
}

@Composable
private fun AddFieldButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(SecaIcons.Add, contentDescription = null)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun ProfilePicker(ui: ContactsUi, selectedId: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selected = ui.profiles.firstOrNull { it.id == selectedId } ?: ProfileStore.Principal
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable { open = true }
                .padding(vertical = 8.dp),
        ) {
            Text(selected.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Icon(SecaIcons.ArrowDropDown, contentDescription = "Choisir le profil")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ProfileMenuItems(
                profiles = ui.profiles,
                selectedId = selected.id,
                onSelect = {
                    open = false
                    onSelect(it.id)
                },
            )
        }
    }
}
