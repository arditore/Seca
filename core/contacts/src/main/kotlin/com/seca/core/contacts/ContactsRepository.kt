package com.seca.core.contacts

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
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

/** One phone number or e-mail address of a contact; [id] is null until it is saved. */
data class ContactField(val id: Long?, val value: String, val type: Int)

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
            arrayOf(Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY, Contacts.STARRED),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                Head(cursor.getString(0).orEmpty(), cursor.getString(1).orEmpty(), cursor.getInt(2) == 1)
            } else {
                null
            }
        } ?: return@withContext null
        val rawContactId = primaryRawContact(id) ?: return@withContext null

        var nameDataId: Long? = null
        var givenName = ""
        var familyName = ""
        val phones = mutableListOf<ContactField>()
        val emails = mutableListOf<ContactField>()
        resolver.query(
            Data.CONTENT_URI,
            arrayOf(Data._ID, Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3),
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
                    // For a number or an address, DATA1 is the value and DATA2 its type.
                    Phone.CONTENT_ITEM_TYPE -> cursor.getString(2)?.let {
                        phones += ContactField(dataId, it, cursor.getInt(3))
                    }
                    Email.CONTENT_ITEM_TYPE -> cursor.getString(2)?.let {
                        emails += ContactField(dataId, it, cursor.getInt(3))
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
            rawContactId = rawContactId,
            nameDataId = nameDataId,
        )
    }

    /** Creates a contact stored on this device only. Returns its contact id. */
    suspend fun createContact(input: ContactInput): Long? = withContext(Dispatchers.IO) {
        val ops = arrayListOf<ContentProviderOperation>()
        ops += ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
            // A null account is the device's own: attached to no account, never synced.
            .withValue(RawContacts.ACCOUNT_TYPE, null)
            .withValue(RawContacts.ACCOUNT_NAME, null)
            .build()
        if (displayNameOf(input).isNotEmpty()) {
            ops += nameRow(input).withValueBackReference(Data.RAW_CONTACT_ID, 0).build()
        }
        input.phones.filter { it.value.isNotBlank() }.forEach { field ->
            ops += fieldRow(Phone.CONTENT_ITEM_TYPE, field).withValueBackReference(Data.RAW_CONTACT_ID, 0).build()
        }
        input.emails.filter { it.value.isNotBlank() }.forEach { field ->
            ops += fieldRow(Email.CONTENT_ITEM_TYPE, field).withValueBackReference(Data.RAW_CONTACT_ID, 0).build()
        }
        val rawContactId = resolver.applyBatch(ContactsContract.AUTHORITY, ops)
            .firstOrNull()?.uri?.let { ContentUris.parseId(it) }
            ?: return@withContext null
        contactIdOf(rawContactId)
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
            resolver.applyBatch(ContactsContract.AUTHORITY, ops)
        }
    }

    suspend fun deleteContact(id: Long) {
        withContext(Dispatchers.IO) {
            resolver.delete(ContentUris.withAppendedId(Contacts.CONTENT_URI, id), null, null)
        }
    }

    suspend fun setStarred(id: Long, starred: Boolean) {
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply { put(Contacts.STARRED, if (starred) 1 else 0) }
            resolver.update(ContentUris.withAppendedId(Contacts.CONTENT_URI, id), values, null, null)
        }
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
    suspend fun exportAll(): List<VCardContact> = withContext(Dispatchers.IO) {
        val cards = linkedMapOf<Long, ExportCard>()
        resolver.query(
            Data.CONTENT_URI,
            arrayOf(Data.CONTACT_ID, Data.DISPLAY_NAME_PRIMARY, Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3),
            "${Data.MIMETYPE} IN (?, ?, ?)",
            arrayOf(StructuredName.CONTENT_ITEM_TYPE, Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val card = cards.getOrPut(c.getLong(0)) { ExportCard(c.getString(1).orEmpty()) }
                when (c.getString(2)) {
                    StructuredName.CONTENT_ITEM_TYPE -> if (card.given.isEmpty() && card.family.isEmpty()) {
                        card.given = c.getString(4).orEmpty()
                        card.family = c.getString(5).orEmpty()
                    }
                    Phone.CONTENT_ITEM_TYPE -> c.getString(3)?.let { card.phones += ContactField(null, it, c.getInt(4)) }
                    Email.CONTENT_ITEM_TYPE -> c.getString(3)?.let { card.emails += ContactField(null, it, c.getInt(4)) }
                }
            }
        }
        cards.values
            .map { VCardContact(it.given, it.family, it.display, it.phones.distinctBy(ContactField::value), it.emails.distinctBy(ContactField::value)) }
            .sortedBy { it.displayName.lowercase() }
    }

    private class ExportCard(val display: String) {
        var given = ""
        var family = ""
        val phones = mutableListOf<ContactField>()
        val emails = mutableListOf<ContactField>()
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
            .apply { normalizedNumber(mimeType, field.value)?.let { withValue(Phone.NORMALIZED_NUMBER, it) } }

    /**
     * The E.164 form of a phone number, read with the SIM's country. Left out
     * when the number is not understood, so the provider computes its own.
     */
    private fun normalizedNumber(mimeType: String, value: String): String? =
        if (mimeType == Phone.CONTENT_ITEM_TYPE) numbers?.toE164(value) else null

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
            } else if (before.firstOrNull { it.id == savedId }.let { it == null || it.value != field.value || it.type != field.type }) {
                ops += ContentProviderOperation.newUpdate(Data.CONTENT_URI)
                    .withSelection("${Data._ID} = ?", arrayOf(savedId.toString()))
                    .withValue(Data.DATA1, field.value.trim())
                    .withValue(Data.DATA2, field.type)
                    .apply { normalizedNumber(mimeType, field.value)?.let { withValue(Phone.NORMALIZED_NUMBER, it) } }
                    .build()
            }
        }
    }

    private data class Head(val lookupKey: String, val displayName: String, val starred: Boolean)
}

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
