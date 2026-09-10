package com.seca.core.contacts

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Contacts
import com.seca.core.model.PhoneNumber
import com.seca.core.model.SecaContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/**
 * Reads the device's own contacts from the system provider.
 *
 * Everything stays on the device: `ContactsContract` is where every contacts app
 * on the phone keeps its data, so contacts created by another app show up here
 * too. Under GrapheneOS Contact Scopes the provider may return only a subset, or
 * nothing — both are normal results, not errors.
 */
class ContactsRepository(private val resolver: ContentResolver) {

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

    private fun queryContacts(): List<ContactRow> {
        val projection = arrayOf(
            Contacts._ID,
            Contacts.DISPLAY_NAME_PRIMARY,
            Contacts.STARRED,
            Contacts.PHOTO_THUMBNAIL_URI,
        )
        return resolver.query(Contacts.CONTENT_URI, projection, null, null, Contacts.SORT_KEY_PRIMARY)
            ?.use { cursor ->
                val id = cursor.getColumnIndexOrThrow(Contacts._ID)
                val name = cursor.getColumnIndexOrThrow(Contacts.DISPLAY_NAME_PRIMARY)
                val starred = cursor.getColumnIndexOrThrow(Contacts.STARRED)
                val photo = cursor.getColumnIndexOrThrow(Contacts.PHOTO_THUMBNAIL_URI)
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            ContactRow(
                                id = cursor.getLong(id),
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
}

internal data class ContactRow(
    val id: Long,
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
        )
    }
}
