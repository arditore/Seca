package com.seca.core.contacts

import android.content.ContentResolver
import android.content.ContentValues
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.seca.core.model.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/**
 * How Seca Contacts shares its profiles with the other Seca apps.
 *
 * Profiles live in Seca Contacts' private storage. It exposes them read-only
 * through a provider guarded by [PERMISSION], a signature permission: only
 * apps signed with the same key as Seca Contacts can read it, so no other app
 * on the phone learns which contact is "Travail" or "Famille".
 */
object SharedProfilesContract {
    const val AUTHORITY = "com.seca.contacts.profiles"
    const val PERMISSION = "com.seca.permission.READ_PROFILES"
    val BASE_URI: Uri = Uri.parse("content://$AUTHORITY")
    val PROFILES_URI: Uri = Uri.withAppendedPath(BASE_URI, "profiles")
    val ASSIGNMENTS_URI: Uri = Uri.withAppendedPath(BASE_URI, "assignments")
    val SETTINGS_URI: Uri = Uri.withAppendedPath(BASE_URI, "settings")

    const val COLUMN_ID = "id"
    const val COLUMN_NAME = "name"
    const val COLUMN_LOOKUP_KEY = "lookup_key"
    const val COLUMN_PROFILE_ID = "profile_id"
    const val COLUMN_PALETTE = "palette"
}

/** What a Seca app needs from Seca Contacts' profiles. */
data class SharedProfiles(
    val profiles: List<Profile> = listOf(Profile.Principal),
    /** Profile id by contact lookup key; contacts not listed are in Principal. */
    val assignments: Map<String, String> = emptyMap(),
    /** The palette picked in Seca Contacts, by name, or null for the wallpaper colours. */
    val palette: String? = null,
    /** Whether Seca Contacts answered: without it there are no profiles to show nor colours to set. */
    val connected: Boolean = false,
) {
    fun profileForKey(lookupKey: String): Profile =
        profiles.firstOrNull { it.id == assignments[lookupKey] } ?: Profile.Principal

    /** A profile's position, which picks its colour in every Seca app. */
    fun toneOf(profile: Profile): Int = profiles.indexOfFirst { it.id == profile.id }.coerceAtLeast(0)
}

/**
 * Reads Seca Contacts' profiles. Falls back to the defaults — everyone in
 * Principal, wallpaper colours — when Seca Contacts is missing or was signed
 * with another key.
 */
class SharedProfilesClient(private val resolver: ContentResolver) {

    suspend fun load(): SharedProfiles = withContext(Dispatchers.IO) {
        try {
            val profiles = resolver.query(SharedProfilesContract.PROFILES_URI, null, null, null, null)?.use { c ->
                val id = c.getColumnIndexOrThrow(SharedProfilesContract.COLUMN_ID)
                val name = c.getColumnIndexOrThrow(SharedProfilesContract.COLUMN_NAME)
                buildList { while (c.moveToNext()) add(Profile(c.getString(id), c.getString(name))) }
            }
            val assignments = resolver.query(SharedProfilesContract.ASSIGNMENTS_URI, null, null, null, null)?.use { c ->
                val key = c.getColumnIndexOrThrow(SharedProfilesContract.COLUMN_LOOKUP_KEY)
                val profile = c.getColumnIndexOrThrow(SharedProfilesContract.COLUMN_PROFILE_ID)
                buildMap { while (c.moveToNext()) put(c.getString(key), c.getString(profile)) }
            }
            val palette = resolver.query(SharedProfilesContract.SETTINGS_URI, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(SharedProfilesContract.COLUMN_PALETTE)) else null
            }
            SharedProfiles(
                profiles = profiles?.takeIf { it.isNotEmpty() } ?: listOf(Profile.Principal),
                assignments = assignments.orEmpty(),
                palette = palette,
                connected = profiles != null,
            )
        } catch (e: SecurityException) {
            SharedProfiles()
        }
    }

    /** Sets the palette of every Seca app, kept by Seca Contacts; false when it cannot be reached. */
    suspend fun setPalette(palette: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply { put(SharedProfilesContract.COLUMN_PALETTE, palette) }
            resolver.update(SharedProfilesContract.SETTINGS_URI, values, null, null) > 0
        } catch (e: SecurityException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    /** Emits whenever Seca Contacts changes its profiles or settings. */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        val registered = try {
            resolver.registerContentObserver(SharedProfilesContract.BASE_URI, true, observer)
            true
        } catch (e: SecurityException) {
            // Seca Contacts missing or not allowed: there is simply nothing to follow.
            false
        }
        awaitClose { if (registered) resolver.unregisterContentObserver(observer) }
    }
}
