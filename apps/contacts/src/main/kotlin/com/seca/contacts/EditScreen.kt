package com.seca.contacts

import android.app.DatePickerDialog
import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
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
import com.seca.core.design.component.SecaProfileBadge
import com.seca.core.design.component.SecaTopBar
import com.seca.core.model.initialsOf
import java.time.LocalDate
import com.seca.core.design.label
import androidx.compose.ui.res.stringResource
import com.seca.core.contacts.describe

/** The kinds offered in the editor; any other kind a contact already has is kept and shown. */
private val PhoneTypes = listOf(Phone.TYPE_MOBILE, Phone.TYPE_HOME, Phone.TYPE_WORK, Phone.TYPE_MAIN, Phone.TYPE_OTHER)
private val EmailTypes = listOf(Email.TYPE_HOME, Email.TYPE_WORK, Email.TYPE_MOBILE, Email.TYPE_OTHER)
private val AddressTypes = listOf(StructuredPostal.TYPE_HOME, StructuredPostal.TYPE_WORK, StructuredPostal.TYPE_OTHER)

@Composable
internal fun EditScreen(
    id: Long?,
    ui: ContactsUi,
    viewModel: ContactsViewModel,
    withWrite: (() -> Unit) -> Unit,
    prefillPhone: String? = null,
) {
    var existing by remember(id) { mutableStateOf<ContactDetail?>(null) }
    var ready by remember(id) { mutableStateOf(false) }
    var givenName by remember(id) { mutableStateOf("") }
    var familyName by remember(id) { mutableStateOf("") }
    val phones = remember(id) { mutableStateListOf<ContactField>() }
    val emails = remember(id) { mutableStateListOf<ContactField>() }
    val addresses = remember(id) { mutableStateListOf<ContactField>() }
    var organization by remember(id) { mutableStateOf("") }
    var jobTitle by remember(id) { mutableStateOf("") }
    var website by remember(id) { mutableStateOf("") }
    var birthday by remember(id) { mutableStateOf("") }
    var note by remember(id) { mutableStateOf("") }
    val resources = LocalResources.current
    // "All" is a way of looking at the list, not a profile: a new contact starts in Principal.
    var profileId by remember(id) {
        mutableStateOf(ui.currentProfileId.takeIf { !ui.showingAll } ?: ProfileStore.Principal.id)
    }

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
                addresses.addAll(loaded.addresses)
                organization = loaded.organization?.value.orEmpty()
                jobTitle = loaded.jobTitle
                website = loaded.website?.value.orEmpty()
                birthday = loaded.birthday?.value.orEmpty()
                note = loaded.note?.value.orEmpty()
                profileId = ui.profileForKey(loaded.lookupKey).id
            }
        }
        // One empty field of each kind is always offered; blank ones are not saved.
        if (phones.isEmpty()) phones.add(ContactField(null, prefillPhone.orEmpty(), Phone.TYPE_MOBILE))
        if (emails.isEmpty()) emails.add(ContactField(null, "", Email.TYPE_HOME))
        ready = true
    }

    val canSave = ready &&
        (givenName.isNotBlank() || familyName.isNotBlank() || phones.any { it.value.isNotBlank() })

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            SecaTopBar(
                title = stringResource(if (id == null) R.string.new_contact else R.string.edit_contact),
                onBack = { viewModel.back() },
                navigationIcon = SecaIcons.Close,
            ) {
                Button(
                    enabled = canSave,
                    onClick = {
                        val input = ContactInput(
                            givenName = givenName,
                            familyName = familyName,
                            phones = phones.toList(),
                            emails = emails.toList(),
                            addresses = addresses.toList(),
                            organization = organization,
                            jobTitle = jobTitle,
                            website = website,
                            birthday = birthday,
                            note = note,
                        )
                        withWrite { viewModel.save(existing, input, profileId) }
                    },
                    modifier = Modifier.padding(end = 8.dp),
                ) { Text(stringResource(R.string.save)) }
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
                        // Takes the colour of the profile picked below, as a preview.
                        tone = ui.profiles.indexOfFirst { it.id == profileId }.coerceAtLeast(0),
                        seed = existing?.displayName ?: "new-contact",
                        expressive = true,
                    )
                }

                FormSection(SecaIcons.PersonOutline) {
                    NameField(givenName, stringResource(R.string.given_name)) { givenName = it }
                    NameField(familyName, stringResource(R.string.family_name)) { familyName = it }
                }

                FormSection(SecaIcons.Phone) {
                    FieldList(
                        phones,
                        label = stringResource(R.string.number),
                        keyboard = KeyboardType.Phone,
                        removeLabel = stringResource(R.string.remove_number),
                        types = PhoneTypes,
                        typeLabel = { Phone.getTypeLabel(resources, it, "").toString() },
                        // Says which country the number was read as: "06…" is French with a French SIM.
                        describe = { ui.numbers.describe(it, resources) },
                    )
                    AddFieldButton(stringResource(R.string.add_number)) { phones.add(ContactField(null, "", Phone.TYPE_MOBILE)) }
                }

                FormSection(SecaIcons.Email) {
                    FieldList(
                        emails,
                        label = stringResource(R.string.email_address),
                        keyboard = KeyboardType.Email,
                        removeLabel = stringResource(R.string.remove_address),
                        types = EmailTypes,
                        typeLabel = { Email.getTypeLabel(resources, it, "").toString() },
                    )
                    AddFieldButton(stringResource(R.string.add_email)) { emails.add(ContactField(null, "", Email.TYPE_HOME)) }
                }

                FormSection(SecaIcons.Place) {
                    FieldList(
                        addresses,
                        label = stringResource(R.string.address),
                        keyboard = KeyboardType.Text,
                        removeLabel = stringResource(R.string.remove_address),
                        types = AddressTypes,
                        typeLabel = { StructuredPostal.getTypeLabel(resources, it, "").toString() },
                    )
                    AddFieldButton(stringResource(R.string.add_address)) {
                        addresses.add(ContactField(null, "", StructuredPostal.TYPE_HOME))
                    }
                }

                FormSection(SecaIcons.Work) {
                    PlainField(organization, stringResource(R.string.company)) { organization = it }
                    PlainField(jobTitle, stringResource(R.string.job_title)) { jobTitle = it }
                }

                FormSection(SecaIcons.Link) {
                    PlainField(website, stringResource(R.string.website), keyboard = KeyboardType.Uri) { website = it }
                }

                FormSection(SecaIcons.Cake) {
                    BirthdayField(birthday) { birthday = it }
                }

                FormSection(SecaIcons.Subject) {
                    PlainField(note, stringResource(R.string.notes), singleLine = false) { note = it }
                }

                // Lined up with the chips rather than with the "Profile" label above them.
                FormSection(SecaIcons.Label, iconTop = 50.dp) {
                    Text(
                        text = stringResource(R.string.profile),
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
                                label = { Text(profile.label()) },
                                leadingIcon = { SecaProfileBadge(profile.label(), tone = index, size = 20.dp) },
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
    types: List<Int>,
    typeLabel: (Int) -> String,
    describe: ((String) -> String?)? = null,
) {
    fields.forEachIndexed { index, field ->
        FieldEditor(
            value = field.value,
            label = label,
            keyboard = keyboard,
            removeLabel = removeLabel,
            type = field.type,
            types = types,
            typeLabel = typeLabel,
            onTypeChange = { fields[index] = field.copy(type = it) },
            supporting = describe?.invoke(field.value),
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
    type: Int,
    types: List<Int>,
    typeLabel: (Int) -> String,
    onTypeChange: (Int) -> Unit,
    removable: Boolean,
    supporting: String?,
    onChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                label = { Text(label) },
                supportingText = supporting?.let { { Text(it) } },
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
        TypeSelector(type, types, typeLabel, onTypeChange)
    }
}

/** A field with nothing but text: a company, a website, a note. */
@Composable
private fun PlainField(
    value: String,
    label: String,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboard,
            capitalization = if (keyboard == KeyboardType.Text) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The birthday, chosen in the system's date picker and kept as the provider writes it. */
@Composable
private fun BirthdayField(value: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    Box {
        OutlinedTextField(
            value = if (value.isBlank()) "" else formatBirthday(value),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.birthday)) },
            shape = MaterialTheme.shapes.medium,
            trailingIcon = {
                if (value.isNotBlank()) {
                    IconButton(onClick = { onChange("") }) {
                        Icon(SecaIcons.Close, contentDescription = stringResource(R.string.remove_date))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        // The field itself opens the picker; typing a date by hand helps no one.
        Box(
            Modifier
                .matchParentSize()
                .clickable(onClickLabel = stringResource(R.string.pick_date)) { pickBirthday(context, value, onChange) },
        )
    }
}

private fun pickBirthday(context: Context, current: String, onChange: (String) -> Unit) {
    val start = runCatching { LocalDate.parse(current) }.getOrNull() ?: LocalDate.now().minusYears(YEARS_BACK)
    DatePickerDialog(
        context,
        { _, year, month, day -> onChange("%04d-%02d-%02d".format(year, month + 1, day)) },
        start.year,
        start.monthValue - 1,
        start.dayOfMonth,
    ).show()
}

/** Where the date picker opens when a contact has no birthday yet. */
private const val YEARS_BACK = 30L

/** Mobile, Home, Work…: the kind of number or address, in the phone's language. */
@Composable
private fun TypeSelector(type: Int, types: List<Int>, typeLabel: (Int) -> String, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text(typeLabel(type))
            Icon(SecaIcons.ArrowDropDown, contentDescription = stringResource(R.string.change_type))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            types.forEach { option ->
                DropdownMenuItem(
                    text = { Text(typeLabel(option)) },
                    trailingIcon = if (option == type) {
                        { Icon(SecaIcons.Check, contentDescription = stringResource(R.string.current_type)) }
                    } else {
                        null
                    },
                    onClick = {
                        open = false
                        onChange(option)
                    },
                )
            }
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
