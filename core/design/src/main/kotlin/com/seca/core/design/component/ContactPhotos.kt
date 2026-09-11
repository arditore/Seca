package com.seca.core.design.component

import android.content.ContentResolver
import android.content.ContentUris
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

private const val PHOTO_CACHE_BYTES = 16 * 1024 * 1024

/** Decoded contact photos, kept while the app runs and weighed by their size in memory. */
private val PhotoCache = object : LruCache<String, ImageBitmap>(PHOTO_CACHE_BYTES) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
}

/**
 * A contact's small photo from the contacts provider, decoded off the main
 * thread; null while it loads, or when the contact has none.
 */
@Composable
fun rememberContactThumbnail(photoUri: String?): ImageBitmap? =
    rememberCachedPhoto(photoUri) { resolver -> photoUri?.let { resolver.openInputStream(Uri.parse(it)) } }

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
    val photo = remember(key) { mutableStateOf(key?.let { PhotoCache.get(it) }) }
    LaunchedEffect(key) {
        if (key == null || photo.value != null) return@LaunchedEffect
        photo.value = withContext(Dispatchers.IO) {
            runCatching { open(resolver)?.use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
        }?.also { PhotoCache.put(key, it) }
    }
    return photo.value
}
