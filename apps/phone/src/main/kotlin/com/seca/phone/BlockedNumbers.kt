package com.seca.phone

import android.content.Context
import android.provider.BlockedNumberContract
import android.provider.BlockedNumberContract.BlockedNumbers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One number Android is refusing, as its own list keeps it. */
internal data class BlockedNumber(val id: Long, val number: String)

/**
 * Android's blocked list, read and written from inside Seca Phone.
 *
 * The list belongs to the system and is shared by every app, which is the point: a number
 * blocked here is blocked for the phone, not only for this app. Only the default phone app
 * and the default messaging app may touch it, and Seca Phone is one of those or it is not
 * allowed to show the page at all.
 *
 * It used to be a row that left the app for Android's own screen. Being sent to another app to
 * undo something you did here is the kind of seam a suite exists to remove.
 */
internal object BlockedNumbers {

    /** Whether this phone and this app may see the list at all. */
    fun available(context: Context): Boolean =
        runCatching { BlockedNumberContract.canCurrentUserBlockNumbers(context) }.getOrDefault(false)

    /** Every number on the list, newest first, which is the order somebody added them in. */
    suspend fun all(context: Context): List<BlockedNumber> = withContext(Dispatchers.IO) {
        if (!available(context)) return@withContext emptyList()
        runCatching {
            context.contentResolver.query(
                BlockedNumbers.CONTENT_URI,
                arrayOf(BlockedNumbers.COLUMN_ID, BlockedNumbers.COLUMN_ORIGINAL_NUMBER),
                null,
                null,
                "${BlockedNumbers.COLUMN_ID} DESC",
            )?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val number = cursor.getString(1)
                        if (!number.isNullOrBlank()) add(BlockedNumber(cursor.getLong(0), number))
                    }
                }
            }
        }.getOrNull().orEmpty()
    }

    /** Lets one number through again. */
    suspend fun remove(context: Context, id: Long): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.delete(
                BlockedNumbers.CONTENT_URI,
                "${BlockedNumbers.COLUMN_ID} = ?",
                arrayOf(id.toString()),
            ) > 0
        }.getOrDefault(false)
    }

    /** Adds a number somebody typed, which is how a number nobody has called yet gets blocked. */
    suspend fun add(context: Context, number: String): Boolean = withContext(Dispatchers.IO) {
        val typed = number.trim()
        if (typed.isEmpty()) return@withContext false
        blockNumber(context, typed)
    }
}
