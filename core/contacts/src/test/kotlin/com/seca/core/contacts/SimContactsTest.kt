package com.seca.core.contacts

import android.Manifest
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Reading and writing the phonebook on a SIM card (#97).
 *
 * The card answers through a content provider of the telephony service's own, which is not in
 * the SDK and not identical on every phone. What can be held to here is this side of that
 * conversation: which rows are kept, what an entry is called, what an insert sends and what a
 * delete asks for. A stand-in provider plays the card.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SimContactsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var card: FakeCard

    @Before
    fun setUp() {
        card = Robolectric.buildContentProvider(FakeCard::class.java)
            .create(ProviderInfo().apply { authority = "icc" })
            .get()
        shadowOf(context as android.app.Application).grantPermissions(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
        )
    }

    @Test
    fun `an entry keeps its place on the card`() {
        card.rows += arrayOf<Any?>("Ada", "+33612345678", "", 3)
        card.rows += arrayOf<Any?>("Bao", "+8613800138000", "bao@example.org", 4)

        val entries = SimContacts.entriesOn(context, subscriptionId = 1)

        assertEquals(2, entries?.size)
        assertEquals(3, entries?.first()?.index)
        assertEquals("Ada", entries?.first()?.name)
        assertEquals("+33612345678", entries?.first()?.number)
        assertEquals("bao@example.org", entries?.last()?.email)
    }

    @Test
    fun `a card with nothing on it is not the same as a card that will not answer`() {
        assertEquals(emptyList<SimEntry>(), SimContacts.entriesOn(context, subscriptionId = 1))
        card.refuse = true
        assertNull(SimContacts.entriesOn(context, subscriptionId = 1))
    }

    @Test
    fun `an empty place on the card is passed over`() {
        card.rows += arrayOf<Any?>("", "", "", 1)
        card.rows += arrayOf<Any?>("Ada", "+33612345678", "", 2)

        assertEquals(1, SimContacts.entriesOn(context, subscriptionId = 1)?.size)
    }

    @Test
    fun `writing sends the name as the card calls it`() = runBlocking {
        assertTrue(SimContacts.add(context, subscriptionId = 1, name = "Ada", number = "06 12 34 56 78"))

        val written = card.inserted.single()
        assertEquals("Ada", written.getAsString("tag"))
        // Spaces are the owner's way of reading a number, not the card's way of dialling it.
        assertEquals("0612345678", written.getAsString("number"))
    }

    @Test
    fun `a number with nothing in it is never written`() = runBlocking {
        assertFalse(SimContacts.add(context, subscriptionId = 1, name = "Ada", number = "  "))
        assertTrue(card.inserted.isEmpty())
    }

    @Test
    fun `an apostrophe in a name does not break the delete`() = runBlocking {
        val entry = SimEntry(subscriptionId = 1, index = 1, name = "O'Hara", number = "+33612345678", email = "")

        SimContacts.remove(context, entry)

        assertEquals("tag='O''Hara' AND number='+33612345678'", card.deleted.single())
    }

    /** Stands in for the telephony service's phonebook provider. */
    class FakeCard : ContentProvider() {

        val rows = mutableListOf<Array<Any?>>()
        val inserted = mutableListOf<ContentValues>()
        val deleted = mutableListOf<String>()

        /** A phone that will not let the card be read at all, which several do. */
        var refuse = false

        override fun onCreate() = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? {
            if (refuse) throw SecurityException("no")
            return MatrixCursor(arrayOf("name", "number", "emails", "_id")).apply {
                rows.forEach(::addRow)
            }
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? {
            values?.let { inserted += it }
            return Uri.parse("content://icc/adn/1")
        }

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            selection?.let { deleted += it }
            return 1
        }

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

        override fun getType(uri: Uri): String? = null
    }
}
