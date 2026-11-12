package com.seca.core.contacts

import android.content.ContentResolver
import android.content.ContentValues
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.net.toUri
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
    val BASE_URI: Uri = "content://$AUTHORITY".toUri()
    val PROFILES_URI: Uri = Uri.withAppendedPath(BASE_URI, "profiles")
    val ASSIGNMENTS_URI: Uri = Uri.withAppendedPath(BASE_URI, "assignments")
    val SETTINGS_URI: Uri = Uri.withAppendedPath(BASE_URI, "settings")

    const val COLUMN_ID = "id"
    const val COLUMN_NAME = "name"
    const val COLUMN_LOOKUP_KEY = "lookup_key"
    const val COLUMN_PROFILE_ID = "profile_id"
    const val COLUMN_PALETTE = "palette"
    const val COLUMN_BLACK = "black"

    /** The shape faces are cut to: one name on the settings row, one per contact on the assignments. */
    const val COLUMN_AVATAR_SHAPE = "avatar_shape"

    /** Whether a face takes a colour of its own rather than its profile's (#52). */
    const val COLUMN_COLOUR_PER_PERSON = "colour_per_person"
}

/** What a Seca app needs from Seca Contacts' profiles. */
data class SharedProfiles(
    val profiles: List<Profile> = listOf(Profile.Principal),
    /** Profile id by contact lookup key; contacts not listed are in Principal. */
    val assignments: Map<String, String> = emptyMap(),
    /** The palette picked in Seca Contacts, by name, or null for the wallpaper colours. */
    val palette: String? = null,
    /** Whether the dark theme goes down to black, as OLED screens are at their best. */
    val black: Boolean = false,
    /** The shape faces are cut to, by name, or null for the playful default (#25). */
    val avatarShape: String? = null,
    /** A shape chosen for one contact, by lookup key, where one was. */
    val shapes: Map<String, String> = emptyMap(),
    /** Whether each person gets a colour of their own rather than their profile's (#52). */
    val colourPerPerson: Boolean = false,
    /** Whether Seca Contacts answered: without it there are no profiles to show nor colours to set. */
    val connected: Boolean = false,
) {
    fun profileForKey(lookupKey: String): Profile =
        profiles.firstOrNull { it.id == assignments[lookupKey] } ?: Profile.Principal

    /** The shape one contact's face is cut to: their own where they have one, else everyone's. */
    fun shapeNameFor(lookupKey: String): String? = shapes[lookupKey] ?: avatarShape

    /** A profile's position, which picks its colour in every Seca app. */
    fun toneOf(profile: Profile): Int = profiles.indexOfFirst { it.id == profile.id }.coerceAtLeast(0)

    /**
     * The colour a face takes: its profile's, or one of its own.
     *
     * By profile is the default and says where a contact is filed, which is worth keeping for
     * anyone who files them. For everybody else there is one profile, so every face is the
     * same colour and the colour says nothing. Asked for in #52.
     *
     * Taken from the lookup key rather than rolled, so a person keeps their colour, and the
     * key rather than the name so it survives the address book re-aggregating them.
     */
    fun toneFor(lookupKey: String, profile: Profile): Int =
        if (colourPerPerson) Math.floorMod(lookupKey.hashCode(), TONES) else toneOf(profile)
}

/**
 * How many colours there are to go round.
 *
 * The scheme works them out from the palette and keeps them far enough apart to be told apart,
 * which yields about this many. Past it they repeat, which is the honest ceiling: a list of
 * thirty contacts shows each colour four or five times.
 */
private const val TONES = 7

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
            // The assignments carry a second thing per contact now: the shape, where one was
            // chosen for them. An older Seca Contacts has no such column, and then nobody has.
            val shapes = mutableMapOf<String, String>()
            val assignments = resolver.query(SharedProfilesContract.ASSIGNMENTS_URI, null, null, null, null)?.use { c ->
                val key = c.getColumnIndexOrThrow(SharedProfilesContract.COLUMN_LOOKUP_KEY)
                val profile = c.getColumnIndexOrThrow(SharedProfilesContract.COLUMN_PROFILE_ID)
                val shape = c.getColumnIndex(SharedProfilesContract.COLUMN_AVATAR_SHAPE).takeIf { it >= 0 }
                buildMap {
                    while (c.moveToNext()) {
                        val lookupKey = c.getString(key)
                        c.getString(profile)?.let { put(lookupKey, it) }
                        shape?.let { column -> c.getString(column)?.let { shapes[lookupKey] = it } }
                    }
                }
            }
            val settings = resolver.query(SharedProfilesContract.SETTINGS_URI, null, null, null, null)?.use { c ->
                if (!c.moveToFirst()) {
                    null
                } else {
                    val palette = c.getString(c.getColumnIndexOrThrow(SharedProfilesContract.COLUMN_PALETTE))
                    // An older Seca Contacts has no such column: its dark theme is the usual one.
                    val black = c.getColumnIndex(SharedProfilesContract.COLUMN_BLACK)
                        .takeIf { it >= 0 }?.let { c.getInt(it) == 1 } == true
                    val shape = c.getColumnIndex(SharedProfilesContract.COLUMN_AVATAR_SHAPE)
                        .takeIf { it >= 0 }?.let { c.getString(it) }
                    val perPerson = c.getColumnIndex(SharedProfilesContract.COLUMN_COLOUR_PER_PERSON)
                        .takeIf { it >= 0 }?.let { c.getInt(it) == 1 } == true
                    Settings(palette, black, shape, perPerson)
                }
            }
            SharedProfiles(
                profiles = profiles?.takeIf { it.isNotEmpty() } ?: listOf(Profile.Principal),
                assignments = assignments.orEmpty(),
                palette = settings?.palette,
                black = settings?.black == true,
                avatarShape = settings?.avatarShape,
                shapes = shapes,
                colourPerPerson = settings?.colourPerPerson == true,
                connected = profiles != null,
            )
        } catch (e: SecurityException) {
            SharedProfiles()
        }
    }

    /** Sets the palette of every Seca app, kept by Seca Contacts; false when it cannot be reached. */
    suspend fun setPalette(palette: String?): Boolean =
        write(ContentValues().apply { put(SharedProfilesContract.COLUMN_PALETTE, palette) })

    /** Takes the dark theme of every Seca app down to black, or back to Material's own. */
    suspend fun setBlack(black: Boolean): Boolean =
        write(ContentValues().apply { put(SharedProfilesContract.COLUMN_BLACK, if (black) 1 else 0) })

    /** Cuts every face in every Seca app to one shape, or back to one per person (#25). */
    suspend fun setAvatarShape(shape: String?): Boolean =
        write(ContentValues().apply { put(SharedProfilesContract.COLUMN_AVATAR_SHAPE, shape) })

    /** Gives every person a colour of their own, or puts them back on their profile's (#52). */
    suspend fun setColourPerPerson(on: Boolean): Boolean =
        write(ContentValues().apply { put(SharedProfilesContract.COLUMN_COLOUR_PER_PERSON, if (on) 1 else 0) })

    /** What came back from the settings row, which has grown past a pair. */
    private class Settings(
        val palette: String?,
        val black: Boolean,
        val avatarShape: String?,
        val colourPerPerson: Boolean,
    )

    private suspend fun write(values: ContentValues): Boolean = withContext(Dispatchers.IO) {
        try {
            resolver.update(SharedProfilesContract.SETTINGS_URI, values, null, null) > 0
        } catch (e: SecurityException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        } catch (e: UnsupportedOperationException) {
            // An older Seca Contacts refuses what it does not know how to keep.
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
