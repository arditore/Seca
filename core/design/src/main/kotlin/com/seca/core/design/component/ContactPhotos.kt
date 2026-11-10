package com.seca.core.design.component

import android.content.ContentResolver
import android.content.ContentUris
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

private const val PHOTO_CACHE_BYTES = 16 * 1024 * 1024

/** Decoded contact photos, kept while the app runs and weighed by their size in memory. */
private val PhotoCache = object : LruCache<String, ImageBitmap>(PHOTO_CACHE_BYTES) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
}

/**
 * Forgets what a contact looked like, after their photograph has been changed or taken away.
 *
 * The cache lives as long as the process and nothing used to remove anything from it, so a new
 * photograph was invisible and a removed one went on being shown until the app was killed and
 * started again. Both entries go: the large one, keyed by the contact, and any thumbnail whose
 * address names them — the provider usually hands out a fresh address for a new picture, but
 * it is not obliged to, and a stale face is the one thing this must never leave behind.
 */
fun forgetContactPhoto(contactId: Long) {
    PhotoCache.remove("contact-$contactId")
    PhotoCache.snapshot().keys
        .filter { it.contains("/contacts/$contactId/") || it.contains("/$contactId/photo") }
        .forEach(PhotoCache::remove)
    // Emptying the cache is not enough on its own. A screen that has already asked for a
    // photograph never asks again: it remembers the answer against the contact, and the
    // contact has not changed. This is the signal that makes it ask once more.
    PhotoEpoch.intValue++
}

/**
 * Counts how many times a photograph anywhere has changed.
 *
 * One number for all of them rather than one each: changing a photograph is rare, re-reading
 * an unchanged one costs a lookup in the cache above, and a map of counters would be a second
 * thing to keep correct for no gain anybody could see.
 */
private val PhotoEpoch = mutableIntStateOf(0)

/**
 * A contact's small photo from the contacts provider, decoded off the main
 * thread; null while it loads, or when the contact has none.
 */
@Composable
fun rememberContactThumbnail(photoUri: String?): ImageBitmap? =
    rememberCachedPhoto(photoUri) { resolver -> photoUri?.let { resolver.openInputStream(it.toUri()) } }

/** A contact's full-size photo, for the screens where the avatar is large. */
@Composable
fun rememberContactPhoto(contactId: Long?): ImageBitmap? =
    rememberCachedPhoto(contactId?.let { "contact-$it" }) { resolver ->
        contactId?.let {
            ContactsContract.Contacts.openContactPhotoInputStream(
                resolver,
                ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, it),
                true,
            )
        }
    }

/**
 * Keyed on [key], so a list row reused for another contact never shows the
 * previous one's photo, even for a frame.
 */
@Composable
private fun rememberCachedPhoto(key: String?, open: (ContentResolver) -> InputStream?): ImageBitmap? {
    val resolver = LocalContext.current.contentResolver
    val epoch = PhotoEpoch.intValue
    val photo = remember(key, epoch) { mutableStateOf(key?.let { PhotoCache.get(it) }) }
    LaunchedEffect(key, epoch) {
        if (key == null || photo.value != null) return@LaunchedEffect
        photo.value = withContext(Dispatchers.IO) {
            runCatching { open(resolver)?.use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
        }?.also { PhotoCache.put(key, it) }
    }
    return photo.value
}
