package com.seca.contacts

import android.app.DatePickerDialog
import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
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
import com.seca.core.contacts.CUSTOM_TYPE
import com.seca.core.contacts.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.seca.core.design.component.rememberContactPhoto
import kotlinx.coroutines.launch
import com.seca.core.contacts.ContactField
import com.seca.core.contacts.ContactInput
import com.seca.core.contacts.PostalAddress
import com.seca.core.design.SecaIcons
import com.seca.core.design.component.SecaAvatar
import com.seca.core.design.component.SecaProfileBadge
import com.seca.core.design.component.SecaTopBar
import com.seca.core.model.initialsOf
import java.time.LocalDate
import com.seca.core.design.label
import androidx.compose.ui.res.stringResource
import com.seca.core.contacts.describe
import com.seca.core.design.component.SecaChoicePill

/** The kinds offered in the editor; any other kind a contact already has is kept and shown. */
// Custom comes last in each list: it is the one that asks a question back.
private val PhoneTypes =
    listOf(Phone.TYPE_MOBILE, Phone.TYPE_HOME, Phone.TYPE_WORK, Phone.TYPE_MAIN, Phone.TYPE_OTHER, CUSTOM_TYPE)
private val EmailTypes = listOf(Email.TYPE_HOME, Email.TYPE_WORK, Email.TYPE_MOBILE, Email.TYPE_OTHER, CUSTOM_TYPE)
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
    val addresses = remember(id) { mutableStateListOf<PostalAddress>() }
    var organization by remember(id) { mutableStateOf("") }
    var jobTitle by remember(id) { mutableStateOf("") }
    var website by remember(id) { mutableStateOf("") }
    var birthday by remember(id) { mutableStateOf("") }
    var anniversary by remember(id) { mutableStateOf("") }
    var note by remember(id) { mutableStateOf("") }
    val resources = LocalResources.current
    // Where a new contact goes. An existing one is not moved from here: changing the
    // account a contact lives in is another operation, with its own ways of going wrong,
    // and Android has a screen for it.
    var places by remember { mutableStateOf(emptyList<Place>()) }
    var savingIn by remember(id) { mutableStateOf<Place?>(null) }
    var choosingPlace by remember { mutableStateOf(false) }
    // The picture the contact already has, and what the owner has decided to do about it.
    val saved = rememberContactPhoto(id)
    var picture by remember(id) { mutableStateOf<ContactsViewModel.Picture>(ContactsViewModel.Picture.Unchanged) }
    var preview by remember(id) { mutableStateOf<ImageBitmap?>(null) }
    val scope = rememberCoroutineScope()
    // Picked, then framed. A photograph is rarely a portrait, and the middle of one is a chin.
    var framing by remember(id) { mutableStateOf<Uri?>(null) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
        if (picked != null) framing = picked
    }
    framing?.let { picked ->
        PhotoCropDialog(
            uri = picked,
            onCancel = { framing = null },
            onCropped = { jpeg ->
                framing = null
                picture = ContactsViewModel.Picture.Chosen(jpeg)
                preview = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)?.asImageBitmap()
            },
        )
    }
    // "All" is a way of looking at the list, not a profile: a new contact starts in Principal.
    var profileId by remember(id) {
        mutableStateOf(ui.currentProfileId.takeIf { !ui.showingAll } ?: ProfileStore.Principal.id)
    }

    LaunchedEffect(id) {
        if (id == null) {
            val (available, byDefault) = viewModel.savingPlaces()
            places = available
            savingIn = available.firstOrNull { it == byDefault } ?: byDefault
        }
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
                anniversary = loaded.anniversary?.value.orEmpty()
                note = loaded.note?.value.orEmpty()
                profileId = ui.profileForKey(loaded.lookupKey).id
            }
        }
        // One empty field of each kind is always offered; blank ones are not saved.
        if (phones.isEmpty()) phones.add(ContactField(null, prefillPhone.orEmpty(), Phone.TYPE_MOBILE))
        if (emails.isEmpty()) emails.add(ContactField(null, "", Email.TYPE_HOME))
        ready = true
    }

    if (choosingPlace) {
        SavingPlaceDialog(
            places = places,
            chosen = savingIn,
            onDismiss = { choosingPlace = false },
            onPick = {
                savingIn = it
                choosingPlace = false
            },
        )
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
                            anniversary = anniversary,
                            note = note,
                        )
                        withWrite { viewModel.save(existing, input, profileId, savingIn, picture) }
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
                // The avatar follows the name as it is typed, and is where the picture is
                // changed: the editor used to draw a made-up one even for a contact who had
                // a photograph, which read as the photograph having been lost.
                val shown = preview ?: saved.takeIf { picture != ContactsViewModel.Picture.Removed }
                PhotoHeader(
                    initials = initialsOf("$givenName $familyName"),
                    tone = ui.profiles.indexOfFirst { it.id == profileId }.coerceAtLeast(0),
                    seed = existing?.displayName ?: "new-contact",
                    photo = shown,
                    onPick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onRemove = if (shown == null) {
                        null
                    } else {
                        {
                            picture = ContactsViewModel.Picture.Removed
                            preview = null
                        }
                    },
                )

                FormCard(SecaIcons.PersonOutline, stringResource(R.string.name)) {
                    NameField(givenName, stringResource(R.string.given_name)) { givenName = it }
                    NameField(familyName, stringResource(R.string.family_name)) { familyName = it }
                }

                // Said even when there is only one place: an address book that quietly
                // writes into an account that syncs should say so, whosever account it is.
                if (id == null && savingIn != null) {
                    FormCard(SecaIcons.Shield, stringResource(R.string.saved_in)) {
                        SavingPlaceRow(
                            place = savingIn,
                            choosable = places.size > 1,
                            onChoose = { choosingPlace = true },
                        )
                    }
                }

                FormCard(SecaIcons.Phone, stringResource(R.string.number)) {
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

                FormCard(SecaIcons.Email, stringResource(R.string.email_address)) {
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

                FormCard(SecaIcons.Place, stringResource(R.string.address)) {
                    addresses.forEachIndexed { index, address ->
                        AddressFields(
                            address = address,
                            typeLabel = { StructuredPostal.getTypeLabel(resources, it, "").toString() },
                            removeLabel = stringResource(R.string.remove_address),
                            onChange = { addresses[index] = it },
                            // The last one left is emptied rather than removed, as a number is,
                            // so the section never loses the fields that let one be typed.
                            onRemove = {
                                if (addresses.size > 1) addresses.removeAt(index) else addresses[index] = PostalAddress()
                            },
                        )
                    }
                    AddFieldButton(stringResource(R.string.add_address)) { addresses.add(PostalAddress()) }
                }

                // Six cards of one field each used to stand here, one under another, each with
                // a little grey icon in the margin. They are the same card on the page that
                // shows the contact, and they are one card here now too.
                FormCard(SecaIcons.Subject, stringResource(R.string.about)) {
                    PlainField(organization, stringResource(R.string.company)) { organization = it }
                    PlainField(jobTitle, stringResource(R.string.job_title)) { jobTitle = it }
                    PlainField(website, stringResource(R.string.website), keyboard = KeyboardType.Uri) { website = it }
                    DateField(birthday, stringResource(R.string.birthday)) { birthday = it }
                    DateField(anniversary, stringResource(R.string.anniversary)) { anniversary = it }
                    PlainField(note, stringResource(R.string.notes), singleLine = false) { note = it }
                }

                FormCard(SecaIcons.Label, stringResource(R.string.profile)) {
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

/**
 * The face at the top of the editor.
 *
 * The picture is the first thing anybody looks at and it used to be two words of blue text
 * under a circle. A badge on the corner says the circle is a button, which is the one thing
 * nobody could tell before.
 */
@Composable
private fun PhotoHeader(
    initials: String,
    tone: Int,
    seed: String,
    photo: ImageBitmap?,
    onPick: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(colors.surfaceContainerLow)
            .padding(vertical = 24.dp),
    ) {
        Box {
            SecaAvatar(
                initials = initials,
                photoUri = null,
                size = 112.dp,
                // Takes the colour of the profile picked below, as a preview.
                tone = tone,
                seed = seed,
                expressive = true,
                photo = photo,
                modifier = Modifier.clickable(onClickLabel = stringResource(R.string.change_photo), onClick = onPick),
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(40.dp)
                    // A ring in the card's own colour, so the badge sits on the picture rather
                    // than inside it: without it a dark photograph swallows the whole thing.
                    .clip(CircleShape)
                    .background(colors.surfaceContainerLow)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(colors.primaryContainer)
                    .clickable(onClickLabel = stringResource(R.string.change_photo), onClick = onPick),
            ) {
                Icon(
                    imageVector = SecaIcons.Photo,
                    contentDescription = null,
                    tint = colors.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 16.dp),
        ) {
            FilledTonalButton(onClick = onPick) {
                Text(stringResource(if (photo == null) R.string.add_photo else R.string.change_photo))
            }
            if (onRemove != null) {
                TextButton(onClick = onRemove) { Text(stringResource(R.string.remove_photo)) }
            }
        }
    }
}

/**
 * One part of the form: a heading, then its fields on a card of their own.
 *
 * The editor used to be a single column of boxes with a grey icon in the margin beside each
 * one, which is what a form looks like when nobody has decided where it ends. The page that
 * shows a contact has always grouped its rows under a heading; this is the same furniture, so
 * reading a contact and writing one look like the same app.
 */
@Composable
private fun FormCard(icon: ImageVector, title: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.primary,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surfaceContainerLow)
                .padding(horizontal = 14.dp, vertical = 14.dp),
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
 * One address, in the parts an address has.
 *
 * It was one box, and one box is why an address typed into Seca arrived in every other client
 * as a town and a postcode crammed into the street line: there was nowhere else to put them.
 * Five boxes is more to look at, and it is the only way the parts survive leaving the phone.
 *
 * The postcode and the town share a line, because they are read together and because a
 * postcode on a line of its own wastes the width of a screen.
 */
@Composable
private fun AddressFields(
    address: PostalAddress,
    typeLabel: (Int) -> String,
    removeLabel: String,
    onChange: (PostalAddress) -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlainField(address.street, stringResource(R.string.street), modifier = Modifier.weight(1f)) {
                onChange(address.copy(street = it))
            }
            IconButton(onClick = onRemove) {
                Icon(SecaIcons.Close, contentDescription = removeLabel)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlainField(address.postcode, stringResource(R.string.postcode), modifier = Modifier.weight(POSTCODE_SHARE)) {
                onChange(address.copy(postcode = it))
            }
            PlainField(address.city, stringResource(R.string.city), modifier = Modifier.weight(CITY_SHARE)) {
                onChange(address.copy(city = it))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlainField(address.region, stringResource(R.string.region), modifier = Modifier.weight(1f)) {
                onChange(address.copy(region = it))
            }
            PlainField(address.country, stringResource(R.string.country), modifier = Modifier.weight(1f)) {
                onChange(address.copy(country = it))
            }
        }
        TypeSelector(address.type, AddressTypes, typeLabel) { onChange(address.copy(type = it)) }
    }
}

/** A postcode is short and a town is not, so they do not take the same half of the row. */
private const val POSTCODE_SHARE = 0.4f
private const val CITY_SHARE = 0.6f

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
    // One number is the default whether anybody says so or not, so the choice is only offered
    // where there is a choice to make (#34).
    val choosable = fields.count { it.value.isNotBlank() } > 1
    fields.forEachIndexed { index, field ->
        FieldEditor(
            value = field.value,
            label = label,
            keyboard = keyboard,
            removeLabel = removeLabel,
            type = field.type,
            types = types,
            typeLabel = typeLabel,
            customLabel = field.label.orEmpty(),
            onCustomLabelChange = { fields[index] = field.copy(label = it) },
            onTypeChange = { fields[index] = field.copy(type = it) },
            supporting = describe?.invoke(field.value),
            // A lone empty field has nothing to remove.
            removable = fields.size > 1 || field.value.isNotEmpty(),
            onChange = { fields[index] = field.copy(value = it) },
            onRemove = { if (fields.size > 1) fields.removeAt(index) else fields[index] = field.copy(value = "") },
            isDefault = field.isDefault,
            // Exactly one, or none: choosing this one unchooses whichever held it before.
            onMakeDefault = if (choosable && field.value.isNotBlank()) {
                {
                    val wanted = !field.isDefault
                    fields.indices.forEach { other ->
                        fields[other] = fields[other].copy(isDefault = wanted && other == index)
                    }
                }
            } else {
                null
            },
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
    customLabel: String,
    onCustomLabelChange: (String) -> Unit,
    onTypeChange: (Int) -> Unit,
    removable: Boolean,
    supporting: String?,
    onChange: (String) -> Unit,
    onRemove: () -> Unit,
    isDefault: Boolean = false,
    /** Null where there is nothing to choose between, and so nothing to offer. */
    onMakeDefault: (() -> Unit)? = null,
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            TypeSelector(type, types, typeLabel, onTypeChange)
            onMakeDefault?.let { choose ->
                SecaChoicePill(
                    label = stringResource(R.string.default_one),
                    selected = isDefault,
                    onClick = choose,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            // Only a field the owner is naming themselves asks for a word.
            if (type == CUSTOM_TYPE) {
                OutlinedTextField(
                    value = customLabel,
                    onValueChange = onCustomLabelChange,
                    label = { Text(stringResource(R.string.label_name)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp, end = 48.dp),
                )
            }
        }
    }
}

/** A field with nothing but text: a company, a website, a note. */
@Composable
private fun PlainField(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
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
        modifier = modifier.fillMaxWidth(),
    )
}

/** A date, chosen in the system's picker and kept as the provider writes it. */
@Composable
private fun DateField(value: String, label: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    Box {
        OutlinedTextField(
            value = if (value.isBlank()) "" else formatBirthday(value),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
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

/** Where this contact will be kept, and the way to change it when there is a choice. */
@Composable
private fun SavingPlaceRow(place: Place?, choosable: Boolean, onChoose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(if (choosable) Modifier.clickable(onClick = onChoose) else Modifier)
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.saved_in),
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
        )
        Text(
            text = placeName(place),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurface,
        )
        Text(
            text = stringResource(
                when {
                    place == null || place.onPhoneOnly -> R.string.saved_on_phone_hint
                    else -> R.string.saved_in_account_hint
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun SavingPlaceDialog(places: List<Place>, chosen: Place?, onDismiss: () -> Unit, onPick: (Place) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(SecaIcons.Shield, contentDescription = null) },
        title = { Text(stringResource(R.string.saved_in)) },
        text = {
            Column {
                places.forEach { place ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .selectable(selected = place == chosen, role = Role.RadioButton) { onPick(place) }
                            .padding(vertical = 6.dp),
                    ) {
                        RadioButton(selected = place == chosen, onClick = null)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(
                                text = placeName(place),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            // The account's type as the phone spells it: not pretty, but true,
                            // and it is what tells two accounts of the same name apart.
                            place.type?.takeIf { it.isNotEmpty() }?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun placeName(place: Place?): String =
    place?.name?.takeIf { !place.onPhoneOnly && it.isNotEmpty() } ?: stringResource(R.string.on_this_phone)
