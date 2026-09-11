package com.seca.contacts

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.seca.core.contacts.ContactDetail
import com.seca.core.contacts.ContactField
import com.seca.core.contacts.ContactInput
import com.seca.core.design.SecaIcons
import com.seca.core.design.component.SecaAvatar
import com.seca.core.model.initialsOf

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
        // One empty field of each kind is always offered; blank ones are not saved.
        if (phones.isEmpty()) phones.add(ContactField(null, "", Phone.TYPE_MOBILE))
        if (emails.isEmpty()) emails.add(ContactField(null, "", Email.TYPE_HOME))
        ready = true
    }

    val canSave = ready &&
        (givenName.isNotBlank() || familyName.isNotBlank() || phones.any { it.value.isNotBlank() })

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            SimpleTopBar(
                title = if (id == null) "Nouveau contact" else "Modifier le contact",
                onBack = { viewModel.back() },
                navigationIcon = SecaIcons.Close,
            ) {
                Button(
                    enabled = canSave,
                    onClick = {
                        val input = ContactInput(givenName, familyName, phones.toList(), emails.toList())
                        withWrite { viewModel.save(existing, input, profileId) }
                    },
                    modifier = Modifier.padding(end = 8.dp),
                ) { Text("Enregistrer") }
            }
        },
    ) { padding ->
        if (ready) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp),
            ) {
                // The avatar follows the name as it is typed.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                ) {
                    SecaAvatar(
                        initials = initialsOf("$givenName $familyName"),
                        photoUri = null,
                        size = 112.dp,
                        seed = existing?.displayName ?: "Nouveau contact",
                        expressive = true,
                    )
                }

                FormSection(SecaIcons.PersonOutline) {
                    NameField(givenName, "Prénom") { givenName = it }
                    NameField(familyName, "Nom") { familyName = it }
                }

                FormSection(SecaIcons.Phone) {
                    FieldList(phones, label = "Numéro", keyboard = KeyboardType.Phone, removeLabel = "Retirer ce numéro")
                    AddFieldButton("Ajouter un numéro") { phones.add(ContactField(null, "", Phone.TYPE_MOBILE)) }
                }

                FormSection(SecaIcons.Email) {
                    FieldList(
                        emails,
                        label = "Adresse e-mail",
                        keyboard = KeyboardType.Email,
                        removeLabel = "Retirer cette adresse",
                    )
                    AddFieldButton("Ajouter une adresse e-mail") { emails.add(ContactField(null, "", Email.TYPE_HOME)) }
                }

                // Lined up with the chips rather than with the "Profil" label above them.
                FormSection(SecaIcons.Label, iconTop = 50.dp) {
                    Text(
                        text = "Profil",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        ui.profiles.forEachIndexed { index, profile ->
                            FilterChip(
                                selected = profile.id == profileId,
                                onClick = { profileId = profile.id },
                                label = { Text(profile.name) },
                                leadingIcon = { ProfileBadge(profile.name, tone = index, size = 20.dp) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One part of the form: its icon at the start, lined up with the first field, and its fields beside it. */
@Composable
private fun FormSection(icon: ImageVector, iconTop: Dp = 24.dp, content: @Composable ColumnScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = iconTop),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
            content = content,
        )
    }
}

@Composable
private fun NameField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Next,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The editable numbers or addresses. The last one left is cleared rather than
 * removed, so the section never loses its field.
 */
@Composable
private fun FieldList(
    fields: SnapshotStateList<ContactField>,
    label: String,
    keyboard: KeyboardType,
    removeLabel: String,
) {
    fields.forEachIndexed { index, field ->
        FieldEditor(
            value = field.value,
            label = label,
            keyboard = keyboard,
            removeLabel = removeLabel,
            // A lone empty field has nothing to remove.
            removable = fields.size > 1 || field.value.isNotEmpty(),
            onChange = { fields[index] = field.copy(value = it) },
            onRemove = { if (fields.size > 1) fields.removeAt(index) else fields[index] = field.copy(value = "") },
        )
    }
}

@Composable
private fun FieldEditor(
    value: String,
    label: String,
    keyboard: KeyboardType,
    removeLabel: String,
    removable: Boolean,
    onChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Next),
            modifier = Modifier.weight(1f),
        )
        if (removable) {
            IconButton(onClick = onRemove) {
                Icon(SecaIcons.Close, contentDescription = removeLabel)
            }
        } else {
            // Keeps the field as wide as its neighbours that do have the button.
            Spacer(Modifier.size(48.dp))
        }
    }
}

@Composable
private fun AddFieldButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(SecaIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}
