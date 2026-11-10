package com.seca.core.contacts

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import androidx.annotation.RequiresApi
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import com.seca.core.model.PhoneNumber
import com.seca.core.model.SecaContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/**
 * One phone number, e-mail address or postal address of a contact; [id] is null until saved.
 *
 * [label] is the name the owner gave this one when [type] is [CUSTOM_TYPE] - "ABC", "Holiday
 * house". The provider keeps it beside the type, and it is the whole of what a custom type
 * means: without it such a field comes back as a plain one, which is what it used to do here.
 */
data class ContactField(val id: Long?, val value: String, val type: Int, val label: String? = null) {

    /** What to show for this field's kind, when it is the owner's own word rather than a type. */
    val customLabel: String? get() = label?.takeIf { type == CUSTOM_TYPE && it.isNotBlank() }
}

/**
 * The type that means "the owner named this one themselves".
 *
 * Android spells it once per kind - `Phone.TYPE_CUSTOM`, `Email.TYPE_CUSTOM`,
 * `StructuredPostal.TYPE_CUSTOM` - and all three are zero, so one name serves for all three.
 */
const val CUSTOM_TYPE: Int = Phone.TYPE_CUSTOM

/** Everything the contact screen and the editor need about one contact. */
data class ContactDetail(
    val id: Long,
    val lookupKey: String,
    val displayName: String,
    val givenName: String,
    val familyName: String,
    val starred: Boolean,
    val phones: List<ContactField>,
    val emails: List<ContactField>,
    val addresses: List<PostalAddress>,
    /** The company; the job title lives in the same row. */
    val organization: ContactField?,
    val jobTitle: String,
    val website: ContactField?,
    /** The contact's own ringtone, or null for the phone's. */
    val ringtone: String?,
    /** Whether this contact's calls go straight to the voicemail. */
    val sendToVoicemail: Boolean,
    /** As the provider keeps it: "1990-05-12", or "--05-12" for a day without a year. */
    val birthday: ContactField?,
    /** The same, for the day two people mark together; another app may have written it. */
    val anniversary: ContactField?,
    val note: ContactField?,
    /** Where new rows are written: the device-only raw contact when there is one. */
    val rawContactId: Long,
    val nameDataId: Long?,
)

/** What the editor hands back to be saved. */
data class ContactInput(
    val givenName: String,
    val familyName: String,
    val phones: List<ContactField>,
    val emails: List<ContactField>,
    val addresses: List<PostalAddress> = emptyList(),
    val organization: String = "",
    val jobTitle: String = "",
    val website: String = "",
    val birthday: String = "",
    val anniversary: String = "",
    val note: String = "",
)

/**
 * Reads and writes the device's own contacts through the system provider.
 *
 * Everything stays on the device: `ContactsContract` is where every contacts app
 * on the phone keeps its data, so contacts created by another app show up here
 * too. Under GrapheneOS Contact Scopes the provider may return only a subset, or
 * nothing — both are normal results, not errors. Contacts created here go to the
 * device's own account and are never synced anywhere.
 */
class ContactsRepository(
    private val resolver: ContentResolver,
    /** Fills in the E.164 form of saved numbers, the one callers are matched on. */
    private val numbers: PhoneNumbers? = null,
) {

    /** Every visible contact, in the order the system sorts names. */
    suspend fun contacts(): List<SecaContact> = withContext(Dispatchers.IO) {
        groupContacts(queryContacts(), queryPhones())
    }

    /** Emits whenever the contacts provider changes, whichever app changed it. */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(Contacts.CONTENT_URI, true, observer)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    suspend fun contactDetail(id: Long): ContactDetail? = withContext(Dispatchers.IO) {
        val head = resolver.query(
            ContentUris.withAppendedId(Contacts.CONTENT_URI, id),
            arrayOf(
                Contacts.LOOKUP_KEY,
                Contacts.DISPLAY_NAME_PRIMARY,
                Contacts.STARRED,
                Contacts.CUSTOM_RINGTONE,
                Contacts.SEND_TO_VOICEMAIL,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                Head(
                    lookupKey = cursor.getString(0).orEmpty(),
                    displayName = cursor.getString(1).orEmpty(),
                    starred = cursor.getInt(2) == 1,
                    ringtone = cursor.getString(3),
                    sendToVoicemail = cursor.getInt(4) == 1,
                )
            } else {
                null
            }
        } ?: return@withContext null
        val rawContactId = primaryRawContact(id) ?: return@withContext null

        var nameDataId: Long? = null
        var givenName = ""
        var familyName = ""
        var jobTitle = ""
        var organization: ContactField? = null
        var website: ContactField? = null
        var birthday: ContactField? = null
        var anniversary: ContactField? = null
        var note: ContactField? = null
        val phones = mutableListOf<ContactField>()
        val emails = mutableListOf<ContactField>()
        val addresses = mutableListOf<PostalAddress>()
        resolver.query(
            Data.CONTENT_URI,
            // DATA5 to DATA10 are only ever an address's parts; every other kind of row leaves
            // them empty, so one projection serves all of them.
            arrayOf(
                Data._ID, Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA4,
                Data.DATA5, Data.DATA6, Data.DATA7, Data.DATA8, Data.DATA9, Data.DATA10,
            ),
            "${Data.CONTACT_ID} = ?",
            arrayOf(id.toString()),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val dataId = cursor.getLong(0)
                when (cursor.getString(1)) {
                    // For a name, DATA2 is the given name and DATA3 the family name.
                    StructuredName.CONTENT_ITEM_TYPE -> if (nameDataId == null) {
                        nameDataId = dataId
                        givenName = cursor.getString(3).orEmpty()
                        familyName = cursor.getString(4).orEmpty()
                    }
                    // For a number, an e-mail or an address, DATA1 is the value and DATA2 its type.
                    // DATA3 holds the owner's own word for the field, kept for a custom type.
                    Phone.CONTENT_ITEM_TYPE -> cursor.getString(2)?.let {
                        phones += ContactField(dataId, it, cursor.getInt(3), cursor.getString(4))
                    }
                    Email.CONTENT_ITEM_TYPE -> cursor.getString(2)?.let {
                        emails += ContactField(dataId, it, cursor.getInt(3), cursor.getString(4))
                    }
                    // An address is read from its parts, and only guessed at from the one line
                    // when there are no parts to read — which is every address Seca itself
                    // wrote before it knew how to keep them.
                    StructuredPostal.CONTENT_ITEM_TYPE -> addresses += postalFrom(cursor, dataId)
                    // The company sits in DATA1 and the job title in DATA4, in one row.
                    Organization.CONTENT_ITEM_TYPE -> if (organization == null) {
                        organization = ContactField(dataId, cursor.getString(2).orEmpty(), cursor.getInt(3))
                        jobTitle = cursor.getString(5).orEmpty()
                    }
                    Website.CONTENT_ITEM_TYPE -> if (website == null) {
                        cursor.getString(2)?.let { website = ContactField(dataId, it, cursor.getInt(3)) }
                    }
                    Event.CONTENT_ITEM_TYPE -> when (cursor.getInt(3)) {
                        Event.TYPE_BIRTHDAY -> if (birthday == null) {
                            cursor.getString(2)?.let { birthday = ContactField(dataId, it, Event.TYPE_BIRTHDAY) }
                        }
                        Event.TYPE_ANNIVERSARY -> if (anniversary == null) {
                            cursor.getString(2)?.let { anniversary = ContactField(dataId, it, Event.TYPE_ANNIVERSARY) }
                        }
                    }
                    Note.CONTENT_ITEM_TYPE -> if (note == null) {
                        cursor.getString(2)?.takeIf { it.isNotBlank() }?.let { note = ContactField(dataId, it, 0) }
                    }
                }
            }
        }
        ContactDetail(
            id = id,
            lookupKey = head.lookupKey,
            displayName = head.displayName,
            givenName = givenName,
            familyName = familyName,
            starred = head.starred,
            phones = phones,
            emails = emails,
            addresses = addresses,
            organization = organization,
            jobTitle = jobTitle,
            website = website,
            ringtone = head.ringtone,
            sendToVoicemail = head.sendToVoicemail,
            birthday = birthday,
            anniversary = anniversary,
            note = note,
            rawContactId = rawContactId,
            nameDataId = nameDataId,
        )
    }

    /**
     * Creates a contact and returns its contact id.
     *
     * Where it is kept is the owner's choice, not this app's. From Android 16 the phone's own
     * settings name where new contacts go — a cloud account that syncs, the device itself, or the
     * SIM — and an app is asked to respect it: writing on the device while that choice names a
     * cloud account is what Android warns against, and at least one phone refuses it outright.
     * So [placesForNewContact] asks the phone first, and the older ways of saying "device only"
     * are only tried after that. Seca still sends nothing anywhere: it has no Internet permission
     * at all, and a contact that syncs does so through the account's own sync, as it would from
     * the phone's own contacts app.
     *
     * Giving up throws [ContactWriteException] rather than returning quietly, so the screen can
     * say what the provider refused instead of looking as though nothing was pressed.
     */
    suspend fun createContact(input: ContactInput, into: Place? = null): Long? = withContext(Dispatchers.IO) {
        var refusal: Exception? = null
        for (place in placesForNewContact(into)) {
            val ops = arrayListOf<ContentProviderOperation>()
            val raw = ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
            if (place != null) {
                raw.withValue(RawContacts.ACCOUNT_TYPE, place.type).withValue(RawContacts.ACCOUNT_NAME, place.name)
            }
            ops += raw.build()
            if (displayNameOf(input).isNotEmpty()) {
                ops += nameRow(input).withValueBackReference(Data.RAW_CONTACT_ID, 0).build()
            }
            input.phones.filter { it.value.isNotBlank() }.forEach { field ->
                ops += fieldRow(Phone.CONTENT_ITEM_TYPE, field).withValueBackReference(Data.RAW_CONTACT_ID, 0).build()
            }
            input.emails.filter { it.value.isNotBlank() }.forEach { field ->
                ops += fieldRow(Email.CONTENT_ITEM_TYPE, field).withValueBackReference(Data.RAW_CONTACT_ID, 0).build()
            }
            input.addresses.map(PostalAddress::trimmed).filterNot(PostalAddress::isBlank).forEach { address ->
                ops += postalRow(address).withValueBackReference(Data.RAW_CONTACT_ID, 0).build()
            }
            singleRows(input).forEach { row -> ops += row.withValueBackReference(Data.RAW_CONTACT_ID, 0).build() }
            val rawContactId = try {
                resolver.applyBatch(ContactsContract.AUTHORITY, ops).firstOrNull()?.uri?.let { ContentUris.parseId(it) }
            } catch (e: Exception) {
                refusal = e
                null
            }
            if (rawContactId != null) return@withContext contactIdOf(rawContactId)
        }
        throw ContactWriteException(describe(refusal))
    }

    private fun placesForNewContact(into: Place?): List<Place?> = when {
        // The owner named a place on the screen: that is the answer, not a starting point.
        into?.onPhoneOnly == true -> listOf(null, Place(null, null))
        into != null -> listOf(into)
        else -> placesToTry(if (Build.VERSION.SDK_INT >= ANDROID_16) syncedAccountChosen() else null)
    }

    /**
     * Where a contact can be saved: the accounts already holding contacts, then the phone.
     *
     * Read from the contacts themselves rather than from the phone's list of accounts, so
     * Seca needs no permission to see accounts and shows nothing that is not already
     * visible in the address book. An account that holds no contact is not offered: there
     * would be no way to know whether it can take one.
     */
    suspend fun placesToSaveIn(): List<Place> = withContext(Dispatchers.IO) {
        val accounts = LinkedHashSet<Place>()
        runCatching {
            resolver.query(
                RawContacts.CONTENT_URI,
                arrayOf(RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME),
                "${RawContacts.DELETED} = 0",
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val type = cursor.getString(0)
                    val name = cursor.getString(1)
                    if (!type.isNullOrEmpty() && !name.isNullOrEmpty()) accounts += Place(type, name)
                }
            }
        }
        // The phone itself is always a place, and always the last one offered.
        accounts.toList() + Place(null, null)
    }

    /** Where the phone says a new contact goes, which is where Seca puts one unasked. */
    suspend fun placeByDefault(): Place = withContext(Dispatchers.IO) {
        (if (Build.VERSION.SDK_INT >= ANDROID_16) syncedAccountChosen() else null) ?: Place(null, null)
    }

    /**
     * The cloud or SIM account the phone names for new contacts, or null when it names neither.
     *
     * Only those two need naming: a contact meant for the device is written by saying nothing,
     * which the provider reads as "wherever the owner's choice points".
     */
    @RequiresApi(ANDROID_16)
    private fun syncedAccountChosen(): Place? = runCatching {
        val setting = RawContacts.DefaultAccount.getDefaultAccountForNewContacts(resolver)
        when (setting.state) {
            RawContacts.DefaultAccount.DefaultAccountAndState.DEFAULT_ACCOUNT_STATE_CLOUD,
            RawContacts.DefaultAccount.DefaultAccountAndState.DEFAULT_ACCOUNT_STATE_SIM,
            -> setting.account?.let { Place(it.type, it.name) }
            else -> null
        }
    }.getOrNull()

    /** What went wrong, named so that a truncated copy still says which failure it was. */
    private fun describe(refusal: Exception?): String = when {
        refusal == null -> ""
        refusal.message.isNullOrBlank() -> refusal.javaClass.simpleName
        else -> "${refusal.javaClass.simpleName}: ${refusal.message}"
    }

    private companion object {
        /** Android 16, the version that gave the owner a say in where new contacts are kept. */
        const val ANDROID_16 = 36
    }

    suspend fun updateContact(detail: ContactDetail, input: ContactInput) {
        withContext(Dispatchers.IO) {
            val ops = arrayListOf<ContentProviderOperation>()
            val nameDataId = detail.nameDataId
            if (nameDataId != null) {
                ops += ContentProviderOperation.newUpdate(Data.CONTENT_URI)
                    .withSelection("${Data._ID} = ?", arrayOf(nameDataId.toString()))
                    .withValue(StructuredName.GIVEN_NAME, input.givenName.trim())
                    .withValue(StructuredName.FAMILY_NAME, input.familyName.trim())
                    .withValue(StructuredName.DISPLAY_NAME, displayNameOf(input).ifEmpty { null })
                    .build()
            } else if (displayNameOf(input).isNotEmpty()) {
                ops += nameRow(input).withValue(Data.RAW_CONTACT_ID, detail.rawContactId).build()
            }
            syncFields(ops, detail.rawContactId, Phone.CONTENT_ITEM_TYPE, detail.phones, input.phones)
            syncFields(ops, detail.rawContactId, Email.CONTENT_ITEM_TYPE, detail.emails, input.emails)
            syncAddresses(ops, detail.rawContactId, detail.addresses, input.addresses)
            syncSingle(
                ops = ops,
                rawContactId = detail.rawContactId,
                mimeType = Organization.CONTENT_ITEM_TYPE,
                existing = detail.organization,
                value = input.organization,
                // A job title alone is worth a row, even without a company.
                present = input.organization.isNotBlank() || input.jobTitle.isNotBlank(),
            ) {
                withValue(Organization.TITLE, input.jobTitle.trim())
                withValue(Organization.TYPE, Organization.TYPE_WORK)
            }
            syncSingle(ops, detail.rawContactId, Website.CONTENT_ITEM_TYPE, detail.website, input.website)
            syncSingle(ops, detail.rawContactId, Event.CONTENT_ITEM_TYPE, detail.birthday, input.birthday) {
                withValue(Event.TYPE, Event.TYPE_BIRTHDAY)
            }
            syncSingle(ops, detail.rawContactId, Event.CONTENT_ITEM_TYPE, detail.anniversary, input.anniversary) {
                withValue(Event.TYPE, Event.TYPE_ANNIVERSARY)
            }
            syncSingle(ops, detail.rawContactId, Note.CONTENT_ITEM_TYPE, detail.note, input.note)
            resolver.applyBatch(ContactsContract.AUTHORITY, ops)
        }
    }

    suspend fun deleteContact(id: Long) {
        withContext(Dispatchers.IO) {
            resolver.delete(ContentUris.withAppendedId(Contacts.CONTENT_URI, id), null, null)
        }
    }

    /**
     * Gives a contact the picture in [jpeg], or takes their picture away with null.
     *
     * Written through the provider's own display-photo door, which takes a full-sized image and
     * makes the small one from it itself - so the picture looks right both in a list row and on
     * the contact's page, and every other app on the phone sees the same one.
     *
     * A contact whose rows belong to an account that will not be written to falls back to the
     * plain photo row, which is all some sync adapters accept. False when neither works, so the
     * screen can say so instead of appearing to have done something.
     */
    suspend fun setPhoto(contactId: Long, jpeg: ByteArray?): Boolean = withContext(Dispatchers.IO) {
        val rawContactId = primaryRawContact(contactId) ?: return@withContext false
        if (jpeg == null) {
            return@withContext runCatching {
                resolver.delete(
                    Data.CONTENT_URI,
                    "${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?",
                    arrayOf(rawContactId.toString(), Photo.CONTENT_ITEM_TYPE),
                ) >= 0
            }.getOrDefault(false)
        }
        val through = Uri.withAppendedPath(
            ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId),
            RawContacts.DisplayPhoto.CONTENT_DIRECTORY,
        )
        val written = runCatching {
            resolver.openAssetFileDescriptor(through, "rw")?.createOutputStream()?.use { it.write(jpeg) } != null
        }.getOrDefault(false)
        if (written) return@withContext true
        runCatching {
            val values = ContentValues().apply {
                put(Data.RAW_CONTACT_ID, rawContactId)
                put(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
                put(Photo.PHOTO, jpeg)
            }
            val existing = resolver.query(
                Data.CONTENT_URI,
                arrayOf(Data._ID),
                "${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?",
                arrayOf(rawContactId.toString(), Photo.CONTENT_ITEM_TYPE),
                null,
            )?.use { if (it.moveToFirst()) it.getLong(0) else null }
            if (existing == null) {
                resolver.insert(Data.CONTENT_URI, values) != null
            } else {
                resolver.update(Data.CONTENT_URI, values, "${Data._ID} = ?", arrayOf(existing.toString())) > 0
            }
        }.getOrDefault(false)
    }

    /** Gives a contact their own ringtone, or null to go back to the phone's. */
    suspend fun setRingtone(id: Long, ringtone: String?) {
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply { put(Contacts.CUSTOM_RINGTONE, ringtone) }
            resolver.update(ContentUris.withAppendedId(Contacts.CONTENT_URI, id), values, null, null)
        }
    }

    /** Sends this contact's calls straight to the voicemail, without ringing. */
    suspend fun setSendToVoicemail(id: Long, on: Boolean) {
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply { put(Contacts.SEND_TO_VOICEMAIL, if (on) 1 else 0) }
            resolver.update(ContentUris.withAppendedId(Contacts.CONTENT_URI, id), values, null, null)
        }
    }

    suspend fun setStarred(id: Long, starred: Boolean) {
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply { put(Contacts.STARRED, if (starred) 1 else 0) }
            resolver.update(ContentUris.withAppendedId(Contacts.CONTENT_URI, id), values, null, null)
        }
    }

    /**
     * Joins several contacts into one, as any phone's contacts app does: Android keeps every
     * original entry and shows them together, so nothing is deleted and no account is touched.
     * Returns the id of the joined contact, or null when there was nothing to join.
     */
    suspend fun mergeContacts(ids: List<Long>): Long? = withContext(Dispatchers.IO) {
        val raws = ids.flatMap { id ->
            resolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts._ID),
                "${ContactsContract.RawContacts.CONTACT_ID} = ?",
                arrayOf(id.toString()),
                null,
            )?.use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }.orEmpty()
        }.distinct()
        if (raws.size < 2) return@withContext null
        val first = raws.first()
        val operations = ArrayList(
            raws.drop(1).map { other ->
                ContentProviderOperation.newUpdate(ContactsContract.AggregationExceptions.CONTENT_URI)
                    .withValue(ContactsContract.AggregationExceptions.TYPE, ContactsContract.AggregationExceptions.TYPE_KEEP_TOGETHER)
                    .withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID1, first)
                    .withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID2, other)
                    .build()
            },
        )
        resolver.applyBatch(ContactsContract.AUTHORITY, operations)
        resolver.query(
            ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, first),
            arrayOf(ContactsContract.RawContacts.CONTACT_ID),
            null,
            null,
            null,
        )?.use { if (it.moveToFirst()) it.getLong(0) else null }
    }

    suspend fun lookupKeyOf(contactId: Long): String? = withContext(Dispatchers.IO) {
        resolver.query(
            ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId),
            arrayOf(Contacts.LOOKUP_KEY),
            null,
            null,
            null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    }

    /** Every visible contact as a vCard carries it, read in one pass over the provider. */
    suspend fun exportAll(): List<VCardContact> = exportEntries().map { it.second }

    /** Every visible contact with its lookup key, for a backup that keeps each contact's profile. */
    suspend fun exportEntries(): List<Pair<String, VCardContact>> = withContext(Dispatchers.IO) {
        val cards = linkedMapOf<Long, ExportCard>()
        resolver.query(
            Data.CONTENT_URI,
            arrayOf(
                Data.CONTACT_ID,
                Data.DISPLAY_NAME_PRIMARY,
                Data.MIMETYPE,
                Data.DATA1,
                Data.DATA2,
                Data.DATA3,
                Data.DATA4,
                Data.LOOKUP_KEY,
                // An address's parts, so a backup carries a town rather than one long line.
                Data.DATA5,
                Data.DATA6,
                Data.DATA7,
                Data.DATA8,
                Data.DATA9,
                Data.DATA10,
            ),
            "${Data.MIMETYPE} IN (?, ?, ?, ?, ?, ?, ?)",
            arrayOf(
                StructuredName.CONTENT_ITEM_TYPE,
                Phone.CONTENT_ITEM_TYPE,
                Email.CONTENT_ITEM_TYPE,
                StructuredPostal.CONTENT_ITEM_TYPE,
                Organization.CONTENT_ITEM_TYPE,
                Website.CONTENT_ITEM_TYPE,
                Note.CONTENT_ITEM_TYPE,
            ),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val card = cards.getOrPut(c.getLong(0)) { ExportCard(c.getString(1).orEmpty(), c.getString(7).orEmpty()) }
                when (c.getString(2)) {
                    StructuredName.CONTENT_ITEM_TYPE -> if (card.given.isEmpty() && card.family.isEmpty()) {
                        card.given = c.getString(4).orEmpty()
                        card.family = c.getString(5).orEmpty()
                    }
                    // DATA3 carries the owner's own word for a field; without it a custom
                    // one came out of a backup as a plain one.
                    Phone.CONTENT_ITEM_TYPE -> c.getString(3)?.let {
                        card.phones += ContactField(null, it, c.getInt(4), c.getString(5))
                    }
                    Email.CONTENT_ITEM_TYPE -> c.getString(3)?.let {
                        card.emails += ContactField(null, it, c.getInt(4), c.getString(5))
                    }
                    StructuredPostal.CONTENT_ITEM_TYPE -> card.addresses += exportedPostal(c)
                    Organization.CONTENT_ITEM_TYPE -> if (card.organization.isEmpty() && card.jobTitle.isEmpty()) {
                        card.organization = c.getString(3).orEmpty()
                        card.jobTitle = c.getString(6).orEmpty()
                    }
                    Website.CONTENT_ITEM_TYPE -> if (card.website.isEmpty()) card.website = c.getString(3).orEmpty()
                    Note.CONTENT_ITEM_TYPE -> if (card.note.isEmpty()) card.note = c.getString(3).orEmpty()
                }
            }
        }
        // Dates come from their own rows, and a contact may have both kinds.
        resolver.query(
            Data.CONTENT_URI,
            arrayOf(Data.CONTACT_ID, Data.DATA1, Data.DATA2),
            "${Data.MIMETYPE} = ? AND ${Data.DATA2} IN (?, ?)",
            arrayOf(Event.CONTENT_ITEM_TYPE, Event.TYPE_BIRTHDAY.toString(), Event.TYPE_ANNIVERSARY.toString()),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val card = cards[c.getLong(0)] ?: continue
                val date = c.getString(1).orEmpty()
                if (c.getInt(2) == Event.TYPE_BIRTHDAY) {
                    if (card.birthday.isEmpty()) card.birthday = date
                } else if (card.anniversary.isEmpty()) {
                    card.anniversary = date
                }
            }
        }
        // Pictures, read one at a time and only for the contacts that have one: asking for
        // the whole column would pull every image into memory at once.
        resolver.query(
            Data.CONTENT_URI,
            arrayOf(Data.CONTACT_ID),
            "${Data.MIMETYPE} = ?",
            arrayOf(Photo.CONTENT_ITEM_TYPE),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val card = cards[id] ?: continue
                if (card.photo != null) continue
                card.photo = runCatching {
                    Contacts.openContactPhotoInputStream(
                        resolver,
                        ContentUris.withAppendedId(Contacts.CONTENT_URI, id),
                        true,
                    )?.use { stream -> stream.readBytes() }
                }.getOrNull()?.takeIf { it.isNotEmpty() }
            }
        }
        cards.values
            .map { card ->
                card.lookupKey to VCardContact(
                    givenName = card.given,
                    familyName = card.family,
                    displayName = card.display,
                    phones = card.phones.distinctBy(ContactField::value),
                    emails = card.emails.distinctBy(ContactField::value),
                    addresses = card.addresses.distinctBy(PostalAddress::formatted),
                    organization = card.organization,
                    jobTitle = card.jobTitle,
                    website = card.website,
                    birthday = card.birthday,
                    anniversary = card.anniversary,
                    note = card.note,
                    photo = card.photo,
                )
            }
            .sortedBy { it.second.displayName.lowercase() }
    }

    private class ExportCard(val display: String, val lookupKey: String) {
        var given = ""
        var family = ""
        var organization = ""
        var jobTitle = ""
        var website = ""
        var birthday = ""
        var anniversary = ""
        var note = ""
        var photo: ByteArray? = null
        val phones = mutableListOf<ContactField>()
        val emails = mutableListOf<ContactField>()
        val addresses = mutableListOf<PostalAddress>()
    }

    /** The contact behind a contacts link handed over by another app, or null. */
    suspend fun contactIdFor(uri: Uri): Long? = withContext(Dispatchers.IO) {
        try {
            Contacts.lookupContact(resolver, uri)?.let(ContentUris::parseId)
        } catch (e: SecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun queryContacts(): List<ContactRow> {
        val projection = arrayOf(
            Contacts._ID,
            Contacts.LOOKUP_KEY,
            Contacts.DISPLAY_NAME_PRIMARY,
            Contacts.STARRED,
            Contacts.PHOTO_THUMBNAIL_URI,
        )
        return resolver.query(Contacts.CONTENT_URI, projection, null, null, Contacts.SORT_KEY_PRIMARY)
            ?.use { cursor ->
                val id = cursor.getColumnIndexOrThrow(Contacts._ID)
                val lookup = cursor.getColumnIndexOrThrow(Contacts.LOOKUP_KEY)
                val name = cursor.getColumnIndexOrThrow(Contacts.DISPLAY_NAME_PRIMARY)
                val starred = cursor.getColumnIndexOrThrow(Contacts.STARRED)
                val photo = cursor.getColumnIndexOrThrow(Contacts.PHOTO_THUMBNAIL_URI)
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            ContactRow(
                                id = cursor.getLong(id),
                                lookupKey = cursor.getString(lookup).orEmpty(),
                                name = cursor.getString(name).orEmpty(),
                                starred = cursor.getInt(starred) == 1,
                                photoUri = cursor.getString(photo),
                            ),
                        )
                    }
                }
            }
            .orEmpty()
    }

    private fun queryPhones(): List<PhoneRow> {
        val projection = arrayOf(Phone.CONTACT_ID, Phone.NUMBER)
        return resolver.query(Phone.CONTENT_URI, projection, null, null, null)
            ?.use { cursor ->
                val contactId = cursor.getColumnIndexOrThrow(Phone.CONTACT_ID)
                val number = cursor.getColumnIndexOrThrow(Phone.NUMBER)
                buildList {
                    while (cursor.moveToNext()) {
                        val raw = cursor.getString(number) ?: continue
                        add(PhoneRow(contactId = cursor.getLong(contactId), number = raw))
                    }
                }
            }
            .orEmpty()
    }

    private fun contactIdOf(rawContactId: Long): Long? =
        resolver.query(
            ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId),
            arrayOf(RawContacts.CONTACT_ID),
            null,
            null,
            null,
        )?.use { if (it.moveToFirst()) it.getLong(0) else null }

    private fun primaryRawContact(contactId: Long): Long? =
        resolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts._ID, RawContacts.ACCOUNT_TYPE),
            "${RawContacts.CONTACT_ID} = ? AND ${RawContacts.DELETED} = 0",
            arrayOf(contactId.toString()),
            null,
        )?.use { cursor ->
            var first: Long? = null
            while (cursor.moveToNext()) {
                val rawId = cursor.getLong(0)
                // Prefer the device-only raw contact, so edits never reach a synced account.
                if (cursor.isNull(1)) return@use rawId
                if (first == null) first = rawId
            }
            first
        }

    private fun nameRow(input: ContactInput): ContentProviderOperation.Builder =
        ContentProviderOperation.newInsert(Data.CONTENT_URI)
            .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
            .withValue(StructuredName.GIVEN_NAME, input.givenName.trim())
            .withValue(StructuredName.FAMILY_NAME, input.familyName.trim())
            .withValue(StructuredName.DISPLAY_NAME, displayNameOf(input))

    private fun fieldRow(mimeType: String, field: ContactField): ContentProviderOperation.Builder =
        ContentProviderOperation.newInsert(Data.CONTENT_URI)
            .withValue(Data.MIMETYPE, mimeType)
            .withValue(Data.DATA1, field.value.trim())
            .withValue(Data.DATA2, field.type)
            .withValue(Data.DATA3, field.customLabel)
            .apply { normalizedNumber(mimeType, field.value)?.let { withValue(Phone.NORMALIZED_NUMBER, it) } }

    /**
     * An address as the provider keeps it: the parts where there are parts, and the one line
     * read apart where there are not.
     *
     * Every address Seca wrote before this had a formatted line and nothing else, so reading
     * only the parts would have emptied them. Reading only the line would throw away what
     * another app wrote properly. Both, in that order.
     */
    private fun postalFrom(cursor: android.database.Cursor, dataId: Long): PostalAddress {
        val saved = PostalAddress(
            id = dataId,
            street = cursor.getString(POSTAL_STREET).orEmpty(),
            postcode = cursor.getString(POSTAL_POSTCODE).orEmpty(),
            city = cursor.getString(POSTAL_CITY).orEmpty(),
            region = cursor.getString(POSTAL_REGION).orEmpty(),
            country = cursor.getString(POSTAL_COUNTRY).orEmpty(),
            type = cursor.getInt(3),
            label = cursor.getString(4),
        )
        if (!saved.isBlank) return saved
        return PostalAddress.from(cursor.getString(2).orEmpty())
            .copy(id = dataId, type = cursor.getInt(3), label = cursor.getString(4))
    }

    /** The same, from the wider query the export uses, where the columns sit elsewhere. */
    private fun exportedPostal(c: android.database.Cursor): PostalAddress {
        val saved = PostalAddress(
            street = c.getString(EXPORT_STREET).orEmpty(),
            postcode = c.getString(EXPORT_POSTCODE).orEmpty(),
            city = c.getString(EXPORT_CITY).orEmpty(),
            region = c.getString(EXPORT_REGION).orEmpty(),
            country = c.getString(EXPORT_COUNTRY).orEmpty(),
            type = c.getInt(4),
            label = c.getString(5),
        )
        if (!saved.isBlank) return saved
        return PostalAddress.from(c.getString(3).orEmpty()).copy(type = c.getInt(4), label = c.getString(5))
    }

    /**
     * An address written as its parts, with the line people read alongside them.
     *
     * The line is assembled rather than typed, so it can never disagree with the parts; the
     * provider and every app that syncs reads the parts.
     */
    private fun postalRow(address: PostalAddress): ContentProviderOperation.Builder =
        ContentProviderOperation.newInsert(Data.CONTENT_URI)
            .withValue(Data.MIMETYPE, StructuredPostal.CONTENT_ITEM_TYPE)
            .withPostal(address)

    private fun ContentProviderOperation.Builder.withPostal(address: PostalAddress): ContentProviderOperation.Builder =
        withValue(StructuredPostal.FORMATTED_ADDRESS, address.formatted)
            .withValue(StructuredPostal.TYPE, address.type)
            .withValue(StructuredPostal.LABEL, address.customLabel)
            .withValue(StructuredPostal.STREET, address.street)
            .withValue(StructuredPostal.POSTCODE, address.postcode)
            .withValue(StructuredPostal.CITY, address.city)
            .withValue(StructuredPostal.REGION, address.region)
            .withValue(StructuredPostal.COUNTRY, address.country)

    /**
     * Turns the editor's addresses into deletes, updates and inserts.
     *
     * Apart from [syncFields] because an address is not one value: an update has to write every
     * part, including the ones that are now empty. Writing only what was filled in would leave
     * a town behind from a previous address under a street that no longer has one.
     */
    private fun syncAddresses(
        ops: MutableList<ContentProviderOperation>,
        rawContactId: Long,
        before: List<PostalAddress>,
        after: List<PostalAddress>,
    ) {
        val kept = after.map(PostalAddress::trimmed).filterNot(PostalAddress::isBlank)
        val keptIds = kept.mapNotNull { it.id }.toSet()
        before.mapNotNull { it.id }.filterNot { it in keptIds }.forEach { removedId ->
            ops += ContentProviderOperation.newDelete(Data.CONTENT_URI)
                .withSelection("${Data._ID} = ?", arrayOf(removedId.toString()))
                .build()
        }
        kept.forEach { address ->
            val savedId = address.id
            if (savedId == null) {
                ops += postalRow(address).withValue(Data.RAW_CONTACT_ID, rawContactId).build()
            } else if (before.firstOrNull { it.id == savedId } != address) {
                ops += ContentProviderOperation.newUpdate(Data.CONTENT_URI)
                    .withSelection("${Data._ID} = ?", arrayOf(savedId.toString()))
                    .withPostal(address)
                    .build()
            }
        }
    }

    /**
     * The E.164 form of a phone number, read with the SIM's country. Left out
     * when the number is not understood, so the provider computes its own.
     */
    private fun normalizedNumber(mimeType: String, value: String): String? =
        if (mimeType == Phone.CONTENT_ITEM_TYPE) numbers?.toE164(value) else null

    /** The rows a contact has at most one of: company and job title, website, birthday, note. */
    private fun singleRows(input: ContactInput): List<ContentProviderOperation.Builder> = buildList {
        if (input.organization.isNotBlank() || input.jobTitle.isNotBlank()) {
            add(
                dataRow(Organization.CONTENT_ITEM_TYPE)
                    .withValue(Organization.COMPANY, input.organization.trim())
                    .withValue(Organization.TITLE, input.jobTitle.trim())
                    .withValue(Organization.TYPE, Organization.TYPE_WORK),
            )
        }
        if (input.website.isNotBlank()) {
            add(dataRow(Website.CONTENT_ITEM_TYPE).withValue(Website.URL, input.website.trim()))
        }
        if (input.birthday.isNotBlank()) {
            add(
                dataRow(Event.CONTENT_ITEM_TYPE)
                    .withValue(Event.START_DATE, input.birthday.trim())
                    .withValue(Event.TYPE, Event.TYPE_BIRTHDAY),
            )
        }
        if (input.anniversary.isNotBlank()) {
            add(
                dataRow(Event.CONTENT_ITEM_TYPE)
                    .withValue(Event.START_DATE, input.anniversary.trim())
                    .withValue(Event.TYPE, Event.TYPE_ANNIVERSARY),
            )
        }
        if (input.note.isNotBlank()) {
            add(dataRow(Note.CONTENT_ITEM_TYPE).withValue(Note.NOTE, input.note.trim()))
        }
    }

    private fun dataRow(mimeType: String): ContentProviderOperation.Builder =
        ContentProviderOperation.newInsert(Data.CONTENT_URI).withValue(Data.MIMETYPE, mimeType)

    /** Writes, changes or removes a row the contact has at most one of. */
    private fun syncSingle(
        ops: MutableList<ContentProviderOperation>,
        rawContactId: Long,
        mimeType: String,
        existing: ContactField?,
        value: String,
        present: Boolean = value.isNotBlank(),
        extra: ContentProviderOperation.Builder.() -> Unit = {},
    ) {
        val savedId = existing?.id
        when {
            savedId == null && !present -> Unit
            savedId == null -> ops += dataRow(mimeType)
                .withValue(Data.RAW_CONTACT_ID, rawContactId)
                .withValue(Data.DATA1, value.trim())
                .apply(extra)
                .build()
            !present -> ops += ContentProviderOperation.newDelete(Data.CONTENT_URI)
                .withSelection("${Data._ID} = ?", arrayOf(savedId.toString()))
                .build()
            else -> ops += ContentProviderOperation.newUpdate(Data.CONTENT_URI)
                .withSelection("${Data._ID} = ?", arrayOf(savedId.toString()))
                .withValue(Data.DATA1, value.trim())
                .apply(extra)
                .build()
        }
    }

    /** Turns the editor's list into deletes, updates and inserts against the saved rows. */
    private fun syncFields(
        ops: MutableList<ContentProviderOperation>,
        rawContactId: Long,
        mimeType: String,
        before: List<ContactField>,
        after: List<ContactField>,
    ) {
        val kept = after.filter { it.value.isNotBlank() }
        val keptIds = kept.mapNotNull { it.id }.toSet()
        before.mapNotNull { it.id }.filterNot { it in keptIds }.forEach { removedId ->
            ops += ContentProviderOperation.newDelete(Data.CONTENT_URI)
                .withSelection("${Data._ID} = ?", arrayOf(removedId.toString()))
                .build()
        }
        kept.forEach { field ->
            val savedId = field.id
            if (savedId == null) {
                ops += fieldRow(mimeType, field).withValue(Data.RAW_CONTACT_ID, rawContactId).build()
            } else if (before.firstOrNull { it.id == savedId }.let { it == null || it != field }) {
                ops += ContentProviderOperation.newUpdate(Data.CONTENT_URI)
                    .withSelection("${Data._ID} = ?", arrayOf(savedId.toString()))
                    .withValue(Data.DATA1, field.value.trim())
                    .withValue(Data.DATA2, field.type)
                    .withValue(Data.DATA3, field.customLabel)
                    .apply { normalizedNumber(mimeType, field.value)?.let { withValue(Phone.NORMALIZED_NUMBER, it) } }
                    .build()
            }
        }
    }

    private data class Head(
        val lookupKey: String,
        val displayName: String,
        val starred: Boolean,
        val ringtone: String?,
        val sendToVoicemail: Boolean,
    )
}

/**
 * Where an address's parts sit in the two queries that read them.
 *
 * Android numbers them DATA4 to DATA10 in the order street, post-office box, neighbourhood,
 * city, region, postcode, country. Seca keeps five of those seven; the two it does not model
 * are left alone rather than cleared, so an app that writes them keeps them.
 */
private const val POSTAL_STREET = 5
private const val POSTAL_CITY = 8
private const val POSTAL_REGION = 9
private const val POSTAL_POSTCODE = 10
private const val POSTAL_COUNTRY = 11

/** The export's own query has the lookup key wedged between DATA4 and DATA5, hence the jump. */
private const val EXPORT_STREET = 6
private const val EXPORT_CITY = 10
private const val EXPORT_REGION = 11
private const val EXPORT_POSTCODE = 12
private const val EXPORT_COUNTRY = 13

private fun displayNameOf(input: ContactInput): String =
    listOf(input.givenName.trim(), input.familyName.trim()).filter { it.isNotEmpty() }.joinToString(" ")

internal data class ContactRow(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val starred: Boolean,
    val photoUri: String?,
)

internal data class PhoneRow(val contactId: Long, val number: String)

/** Attaches each contact's numbers, dropping the same number written twice. */
internal fun groupContacts(contacts: List<ContactRow>, phones: List<PhoneRow>): List<SecaContact> {
    val numbersByContact = phones.groupBy(PhoneRow::contactId, PhoneRow::number)
    return contacts.map { row ->
        SecaContact(
            id = row.id,
            displayName = row.name,
            phoneNumbers = numbersByContact[row.id].orEmpty()
                .distinctBy { PhoneNumber(it).digits }
                .map(::PhoneNumber),
            isFavorite = row.starred,
            photoUri = row.photoUri,
            lookupKey = row.lookupKey,
        )
    }
}

/**
 * The phone's contacts provider refused to write a contact.
 *
 * [message] is what it said, when it said anything: there is no catalogue of reasons a provider
 * can refuse, and a phone maker is free to add its own, so the words are carried to the screen
 * rather than translated into a guess.
 */
class ContactWriteException(message: String) : Exception(message)

/**
 * One place a contact can be kept: an account, by type and name.
 *
 * Both null means the phone itself, with no account behind it - a contact that syncs
 * nowhere and leaves with no one.
 */
data class Place(val type: String?, val name: String?) {

    /** Kept on the phone and nowhere else. */
    val onPhoneOnly: Boolean get() = type.isNullOrEmpty()
}

/**
 * The places a new contact is offered to, in order, until one is accepted.
 *
 * 1. [chosen], the account the phone names for new contacts when that is a cloud or SIM account.
 *    From Android 16 the owner picks this in the phone's settings, and an app that ignores it
 *    writes a contact where the owner did not ask for it — or is refused outright, which is what
 *    a Pixel on Android 17 does when its choice names a cloud account.
 * 2. No account columns at all. Android saves such a contact wherever the owner's choice points,
 *    so this covers "on this phone" without naming it, and it is the only form phones before
 *    Android 16 were ever asked to understand.
 * 3. The columns set to nothing. How a device-only contact was written before that choice
 *    existed, kept because some providers still want exactly that.
 *
 * Nothing further is tried: guessing an account from where the other contacts live would put a
 * contact in a syncing account the owner did not choose, and a refusal said out loud is better
 * than that.
 */
internal fun placesToTry(chosen: Place?): List<Place?> =
    listOfNotNull(chosen) + listOf(null, Place(null, null))
