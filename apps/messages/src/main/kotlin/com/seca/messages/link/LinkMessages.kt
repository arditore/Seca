package com.seca.messages.link

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Where a Seca Link message stands; the order matters, a message only moves forward. */
enum class LinkStatus(val code: Int) { Sending(0), Sent(1), Delivered(2), Read(3), Failed(4), Received(5) }

data class LinkMessage(
    /** The id both phones know the message by, for receipts. */
    val id: String,
    /** International format. */
    val number: String,
    val body: String,
    val date: Long,
    val status: LinkStatus,
    val read: Boolean,
    /** The type of the photo the message carries, kept by [LinkMedia]; null for text. */
    val media: String? = null,
) {
    val outgoing: Boolean get() = status != LinkStatus.Received
}

/**
 * The Seca Link messages, kept in this app's own database, apart from
 * Android's SMS store, which other apps holding the SMS permission can read.
 */
class LinkMessages(context: Context) {

    private val database: SQLiteDatabase = Helper.of(context).writableDatabase

    fun forNumber(number: String): List<LinkMessage> = query("$NUMBER = ?", arrayOf(number), "$DATE ASC")

    /** The newest message of each conversation, by number. */
    fun latestByNumber(): Map<String, LinkMessage> =
        query(null, null, "$DATE ASC").associateBy { it.number }

    fun byId(id: String): LinkMessage? = query("$ID = ?", arrayOf(id), "$DATE ASC").firstOrNull()

    fun unread(number: String): List<LinkMessage> =
        query("$NUMBER = ? AND $STATUS = ? AND $READ = 0", arrayOf(number, LinkStatus.Received.code.toString()), "$DATE ASC")

    fun unreadCounts(): Map<String, Int> =
        query("$STATUS = ? AND $READ = 0", arrayOf(LinkStatus.Received.code.toString()), "$DATE ASC").groupingBy { it.number }.eachCount()

    /** False when a message with this id is already kept: a copy from another relay. */
    fun add(message: LinkMessage): Boolean {
        val values = ContentValues().apply {
            put(ID, message.id)
            put(NUMBER, message.number)
            put(BODY, message.body)
            put(DATE, message.date)
            put(STATUS, message.status.code)
            put(READ, if (message.read) 1 else 0)
            put(MEDIA, message.media)
        }
        val added = database.insertWithOnConflict(MESSAGES, null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
        if (added) changed.tryEmit(Unit)
        return added
    }

    /** Moves messages forward to [status]; never back, and received messages are left alone. */
    fun setStatus(ids: List<String>, status: LinkStatus) {
        if (ids.isEmpty()) return
        val marks = ids.joinToString(",") { "?" }
        val values = ContentValues().apply { put(STATUS, status.code) }
        val failed = LinkStatus.Failed.code
        val received = LinkStatus.Received.code
        database.update(
            MESSAGES,
            values,
            "$ID IN ($marks) AND $STATUS != $received AND ($STATUS < ${status.code} OR $STATUS = $failed)",
            ids.toTypedArray(),
        )
        changed.tryEmit(Unit)
    }

    /** Marks the conversation read; returns the ids that were still unread, for the read receipt. */
    fun markRead(number: String): List<String> {
        val ids = unread(number).map { it.id }
        if (ids.isEmpty()) return ids
        val values = ContentValues().apply { put(READ, 1) }
        database.update(MESSAGES, values, "$NUMBER = ? AND $READ = 0", arrayOf(number))
        changed.tryEmit(Unit)
        return ids
    }

    fun delete(id: String) {
        database.delete(MESSAGES, "$ID = ?", arrayOf(id))
        changed.tryEmit(Unit)
    }

    fun deleteConversation(number: String) {
        database.delete(MESSAGES, "$NUMBER = ?", arrayOf(number))
        changed.tryEmit(Unit)
    }

    /** Whether an envelope was already opened, so the same one from several relays counts once. */
    fun isSeen(eventId: String): Boolean =
        database.query(SEEN, arrayOf(EVENT), "$EVENT = ?", arrayOf(eventId), null, null, null).use { it.moveToFirst() }

    fun markSeen(eventId: String) {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(EVENT, eventId)
            put(AT, now)
        }
        database.insertWithOnConflict(SEEN, null, values, SQLiteDatabase.CONFLICT_IGNORE)
        database.delete(SEEN, "$AT < ?", arrayOf((now - SEEN_KEPT_MILLIS).toString()))
    }

    private fun query(selection: String?, args: Array<String>?, order: String): List<LinkMessage> =
        database.query(MESSAGES, arrayOf(ID, NUMBER, BODY, DATE, STATUS, READ, MEDIA), selection, args, null, null, order).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        LinkMessage(
                            id = c.getString(0),
                            number = c.getString(1),
                            body = c.getString(2),
                            date = c.getLong(3),
                            status = LinkStatus.entries.firstOrNull { it.code == c.getInt(4) } ?: LinkStatus.Failed,
                            read = c.getInt(5) == 1,
                            media = if (c.isNull(6)) null else c.getString(6),
                        ),
                    )
                }
            }
        }

    private class Helper(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE $MESSAGES ($ID TEXT PRIMARY KEY, $NUMBER TEXT NOT NULL, $BODY TEXT NOT NULL, " +
                    "$DATE INTEGER NOT NULL, $STATUS INTEGER NOT NULL, $READ INTEGER NOT NULL DEFAULT 0, $MEDIA TEXT)",
            )
            db.execSQL("CREATE INDEX messages_by_number ON $MESSAGES ($NUMBER, $DATE)")
            db.execSQL("CREATE TABLE $SEEN ($EVENT TEXT PRIMARY KEY, $AT INTEGER NOT NULL)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Version 2: photos.
            if (oldVersion < 2) db.execSQL("ALTER TABLE $MESSAGES ADD COLUMN $MEDIA TEXT")
        }

        companion object {
            @Volatile
            private var instance: Helper? = null

            fun of(context: Context): Helper = instance ?: synchronized(this) {
                instance ?: Helper(context.applicationContext).also { instance = it }
            }
        }
    }

    companion object {
        private const val NAME = "seca_link.db"
        private const val VERSION = 2
        private const val MESSAGES = "messages"
        private const val SEEN = "seen"
        private const val ID = "id"
        private const val NUMBER = "number"
        private const val BODY = "body"
        private const val DATE = "date"
        private const val STATUS = "status"
        private const val READ = "read"
        private const val MEDIA = "media"
        private const val EVENT = "event"
        private const val AT = "at"
        private const val SEEN_KEPT_MILLIS = 7L * 24 * 60 * 60 * 1000

        private val changed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        /** Emits whenever a Seca Link message is added or changes, from the service or a screen. */
        val changes: SharedFlow<Unit> = changed.asSharedFlow()
    }
}
