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
    /** The id both phones know the message by, for receipts and reactions. */
    val id: String,
    /** International format. */
    val number: String,
    val body: String,
    val date: Long,
    val status: LinkStatus,
    val read: Boolean,
    /** The type of the photo or recording the message carries, kept by [LinkMedia]; null for text. */
    val media: String? = null,
    /** The id of the message this one answers. */
    val replyTo: String? = null,
    /** This phone's reaction to the message. */
    val myReaction: String? = null,
    /** The contact's reaction to the message. */
    val theirReaction: String? = null,
    /** When both phones let the message go, in milliseconds; 0 keeps it. */
    val expiresAt: Long = 0L,
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
            put(REPLY_TO, message.replyTo)
            put(EXPIRES_AT, message.expiresAt)
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

    fun setMyReaction(id: String, emoji: String?) {
        val values = ContentValues().apply { put(MY_REACTION, emoji) }
        if (database.update(MESSAGES, values, "$ID = ?", arrayOf(id)) > 0) changed.tryEmit(Unit)
    }

    /** Only a message of the conversation with [number] takes that contact's reaction. */
    fun setTheirReaction(id: String, number: String, emoji: String?) {
        val values = ContentValues().apply { put(THEIR_REACTION, emoji) }
        if (database.update(MESSAGES, values, "$ID = ? AND $NUMBER = ?", arrayOf(id, number)) > 0) changed.tryEmit(Unit)
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

    /** Removes the messages whose time is up, and returns them so their photos and recordings go too. */
    fun deleteExpired(now: Long = System.currentTimeMillis()): List<LinkMessage> {
        val selection = "$EXPIRES_AT > 0 AND $EXPIRES_AT <= ?"
        val args = arrayOf(now.toString())
        val expired = query(selection, args, "$DATE ASC")
        if (expired.isEmpty()) return expired
        database.delete(MESSAGES, selection, args)
        changed.tryEmit(Unit)
        return expired
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
        database.query(MESSAGES, COLUMNS, selection, args, null, null, order).use { c ->
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
                            replyTo = if (c.isNull(7)) null else c.getString(7),
                            myReaction = if (c.isNull(8)) null else c.getString(8),
                            theirReaction = if (c.isNull(9)) null else c.getString(9),
                            expiresAt = c.getLong(10),
                        ),
                    )
                }
            }
        }

    private class Helper(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE $MESSAGES ($ID TEXT PRIMARY KEY, $NUMBER TEXT NOT NULL, $BODY TEXT NOT NULL, " +
                    "$DATE INTEGER NOT NULL, $STATUS INTEGER NOT NULL, $READ INTEGER NOT NULL DEFAULT 0, $MEDIA TEXT, " +
                    "$REPLY_TO TEXT, $MY_REACTION TEXT, $THEIR_REACTION TEXT, $EXPIRES_AT INTEGER NOT NULL DEFAULT 0)",
            )
            db.execSQL("CREATE INDEX messages_by_number ON $MESSAGES ($NUMBER, $DATE)")
            db.execSQL("CREATE TABLE $SEEN ($EVENT TEXT PRIMARY KEY, $AT INTEGER NOT NULL)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Version 2: photos.
            if (oldVersion < 2) db.execSQL("ALTER TABLE $MESSAGES ADD COLUMN $MEDIA TEXT")
            // Version 3: replies, reactions and messages that expire.
            if (oldVersion < 3) {
                db.execSQL("ALTER TABLE $MESSAGES ADD COLUMN $REPLY_TO TEXT")
                db.execSQL("ALTER TABLE $MESSAGES ADD COLUMN $MY_REACTION TEXT")
                db.execSQL("ALTER TABLE $MESSAGES ADD COLUMN $THEIR_REACTION TEXT")
                db.execSQL("ALTER TABLE $MESSAGES ADD COLUMN $EXPIRES_AT INTEGER NOT NULL DEFAULT 0")
            }
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
        private const val VERSION = 3
        private const val MESSAGES = "messages"
        private const val SEEN = "seen"
        private const val ID = "id"
        private const val NUMBER = "number"
        private const val BODY = "body"
        private const val DATE = "date"
        private const val STATUS = "status"
        private const val READ = "read"
        private const val MEDIA = "media"
        private const val REPLY_TO = "reply_to"
        private const val MY_REACTION = "my_reaction"
        private const val THEIR_REACTION = "their_reaction"
        private const val EXPIRES_AT = "expires_at"
        private const val EVENT = "event"
        private const val AT = "at"
        private const val SEEN_KEPT_MILLIS = 7L * 24 * 60 * 60 * 1000
        private val COLUMNS = arrayOf(ID, NUMBER, BODY, DATE, STATUS, READ, MEDIA, REPLY_TO, MY_REACTION, THEIR_REACTION, EXPIRES_AT)

        private val changed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        /** Emits whenever a Seca Link message is added or changes, from the service or a screen. */
        val changes: SharedFlow<Unit> = changed.asSharedFlow()
    }
}
