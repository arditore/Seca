package com.seca.core.contacts

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SubscriptionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One entry as a SIM card holds it (#97).
 *
 * A card is not a contacts database. Each entry is one name, one number and, on cards that
 * have the room, one email address, and it sits at a numbered place in the card's phonebook.
 * That place is shown, because it is what the card itself calls the entry and what another
 * phone will show as well.
 */
data class SimEntry(
    val subscriptionId: Int,
    /** Where the entry sits in the card's phonebook, as the card numbers it. */
    val index: Int,
    val name: String,
    val number: String,
    val email: String,
)

/** A card, with what it holds. [readable] is false when the card is there but would not be read. */
data class SimPhonebook(
    val subscriptionId: Int,
    /** As printed on the tray, counting from 1. */
    val slot: Int,
    val label: String,
    val entries: List<SimEntry>,
    val readable: Boolean,
)

/**
 * The phonebook written on the SIM card itself.
 *
 * Not the same thing as a contacts account. Some phones copy the card into an account of their
 * own, and on those the card's entries reach every app through `ContactsContract`; a great many
 * phones do not, and on those the card is invisible to every app that only reads contacts the
 * usual way. That was the mistake behind the first answer given on #64: the card had been
 * taken for an account, which it is only sometimes.
 *
 * So the card is read where the card is: `content://icc/adn`, the telephony service's own
 * provider, which every Android has carried since the beginning. It is not in the SDK and it is
 * not identical on every phone, which is why every call here is wrapped: a card that will not
 * answer leaves the page saying so instead of crashing, and a write that is refused is reported
 * rather than assumed.
 *
 * Reading needs `READ_CONTACTS`, writing `WRITE_CONTACTS`, which are the permissions the app
 * already asks for to show contacts at all.
 */
object SimContacts {

    /** The cards in the phone, each with its entries, in slot order. */
    suspend fun read(context: Context): List<SimPhonebook> = withContext(Dispatchers.IO) {
        cards(context).map { card ->
            val entries = entriesOn(context, card.subscriptionId)
            card.copy(entries = entries.orEmpty(), readable = entries != null)
        }
    }

    /**
     * Writes one entry onto the card.
     *
     * The card decides how much of a name it keeps: alphabets differ, and the room for one
     * entry is fixed at a few dozen bytes. What comes back from a later read is what the card
     * actually kept, which is why the page re-reads rather than believing what it sent.
     */
    suspend fun add(context: Context, subscriptionId: Int, name: String, number: String, email: String = ""): Boolean =
        withContext(Dispatchers.IO) {
            if (!canWrite(context) || number.isBlank()) return@withContext false
            val values = ContentValues().apply {
                put(COLUMN_TAG, name.trim())
                put(COLUMN_NUMBER, number.filterNot(Char::isWhitespace))
                if (email.isNotBlank()) put(COLUMN_EMAILS, email.trim())
            }
            runCatching { context.contentResolver.insert(uriFor(subscriptionId), values) }.getOrNull() != null
        }

    /**
     * Takes one entry off the card.
     *
     * The provider is told which entry by what it holds, not by its place: that is the only
     * language its delete understands, and it is also why two identical entries cannot be told
     * apart. The card is re-read afterwards, so the page shows what is really left.
     */
    suspend fun remove(context: Context, entry: SimEntry): Boolean = withContext(Dispatchers.IO) {
        if (!canWrite(context)) return@withContext false
        val where = buildString {
            append("$COLUMN_TAG='${escape(entry.name)}'")
            append(" AND $COLUMN_NUMBER='${escape(entry.number)}'")
            if (entry.email.isNotBlank()) append(" AND $COLUMN_EMAILS='${escape(entry.email)}'")
        }
        runCatching { context.contentResolver.delete(uriFor(entry.subscriptionId), where, null) > 0 }
            .getOrDefault(false)
    }

    /** Whether the app may write to a card at all; a page that cannot write does not offer to. */
    fun canWrite(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** The physical cards in the phone. An eSIM has a profile, not a phonebook, and is left out. */
    // The permission is asked for on the first line of the function; the list is then read
    // inside a runCatching, which is where the check stops being visible to the tools.
    @SuppressLint("MissingPermission")
    private fun cards(context: Context): List<SimPhonebook> {
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        val manager = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        return runCatching {
            manager.activeSubscriptionInfoList.orEmpty()
                .filter { it.simSlotIndex >= 0 && !it.isEmbedded }
                .sortedBy { it.simSlotIndex }
                .map { info ->
                    SimPhonebook(
                        subscriptionId = info.subscriptionId,
                        slot = info.simSlotIndex + 1,
                        label = (info.displayName ?: info.carrierName)?.toString()?.takeIf { it.isNotBlank() } ?: "SIM",
                        entries = emptyList(),
                        readable = false,
                    )
                }
        }.getOrDefault(emptyList())
    }

    /** Null when the card would not be read at all, which is not the same as a card holding nothing. */
    internal fun entriesOn(context: Context, subscriptionId: Int): List<SimEntry>? {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return runCatching {
            context.contentResolver.query(uriFor(subscriptionId), null, null, null, null)?.use { cursor ->
                val name = cursor.getColumnIndex(COLUMN_NAME)
                val number = cursor.getColumnIndex(COLUMN_NUMBER)
                val emails = cursor.getColumnIndex(COLUMN_EMAILS)
                val id = cursor.getColumnIndex(COLUMN_ID)
                buildList {
                    var position = 0
                    while (cursor.moveToNext()) {
                        position++
                        val line = if (number >= 0) cursor.getString(number).orEmpty() else ""
                        val tag = if (name >= 0) cursor.getString(name).orEmpty() else ""
                        if (line.isBlank() && tag.isBlank()) continue
                        add(
                            SimEntry(
                                subscriptionId = subscriptionId,
                                // The card's own numbering where the provider gives one, and
                                // the position in the list where it does not: an entry has to
                                // be nameable either way.
                                index = if (id >= 0) cursor.getInt(id) else position,
                                name = tag,
                                number = line,
                                email = if (emails >= 0) cursor.getString(emails).orEmpty() else "",
                            ),
                        )
                    }
                }
            }
        }.getOrNull()
    }

    /**
     * The card's phonebook, named by subscription where the phone supports it.
     *
     * Without the subscription the provider answers for whichever card the phone calls the
     * default, which on a two-card phone is a coin toss. The older phones that do not know the
     * longer form fall back to it through the caller's own error handling, not here.
     */
    private fun uriFor(subscriptionId: Int): Uri =
        if (subscriptionId >= 0) "$ADN/subId/$subscriptionId".toUri() else ADN.toUri()

    private fun String.toUri(): Uri = Uri.parse(this)

    /** A quote inside a name would end the clause early; the provider takes no bound arguments. */
    private fun escape(value: String): String = value.replace("'", "''")

    private const val ADN = "content://icc/adn"
    private const val COLUMN_NAME = "name"
    private const val COLUMN_NUMBER = "number"
    private const val COLUMN_EMAILS = "emails"
    private const val COLUMN_ID = "_id"

    /** What an insert calls the name. The read calls it something else, which is the provider's doing. */
    private const val COLUMN_TAG = "tag"
}
