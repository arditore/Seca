package com.seca.contacts

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.seca.core.contacts.SharedProfilesContract

/**
 * Lets the other Seca apps read the profiles, and nothing else: no writes.
 *
 * Guarded in the manifest by a signature permission, so only apps signed with
 * the same key as Seca Contacts can query it.
 */
class ProfilesProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val store = ProfileStore(requireContext())
        val cursor = when (uri.lastPathSegment) {
            "profiles" -> MatrixCursor(arrayOf(SharedProfilesContract.COLUMN_ID, SharedProfilesContract.COLUMN_NAME))
                .apply { store.profiles().forEach { addRow(arrayOf(it.id, it.name)) } }
            "assignments" -> MatrixCursor(
                arrayOf(SharedProfilesContract.COLUMN_LOOKUP_KEY, SharedProfilesContract.COLUMN_PROFILE_ID),
            ).apply { store.assignments().forEach { (key, profileId) -> addRow(arrayOf(key, profileId)) } }
            "settings" -> MatrixCursor(arrayOf(SharedProfilesContract.COLUMN_PALETTE))
                .apply { addRow(arrayOf(store.palette()?.name)) }
            else -> return null
        }
        cursor.setNotificationUri(requireContext().contentResolver, SharedProfilesContract.BASE_URI)
        return cursor
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException(READ_ONLY)

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException(READ_ONLY)

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException(READ_ONLY)

    private companion object {
        const val READ_ONLY = "Profiles are read-only outside Seca Contacts"
    }
}
