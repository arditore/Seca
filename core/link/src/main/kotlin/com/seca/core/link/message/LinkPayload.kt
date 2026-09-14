package com.seca.core.link.message

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** What travels inside a Seca Link session: a message, or news about messages. */
sealed interface LinkPayload {

    /** A text message; [id] is what receipts point back to. */
    data class Text(val id: String, val body: String, val sentAt: Long) : LinkPayload

    /** The other phone received these messages. */
    data class Delivered(val ids: List<String>) : LinkPayload

    /** The other person saw these messages. */
    data class Read(val ids: List<String>) : LinkPayload

    /** The other person is typing. */
    data object Typing : LinkPayload

    /**
     * One piece of a photo. A photo travels in [count] pieces small enough for
     * every relay, each in an envelope of its own; [digest], the SHA-256 of the
     * whole file, lets the receiver check what it put back together. [id] is the
     * message's, for receipts.
     */
    class MediaPart(
        val id: String,
        val index: Int,
        val count: Int,
        val sentAt: Long,
        val mime: String,
        val digest: ByteArray,
        val data: ByteArray,
    ) : LinkPayload

    companion object {
        private const val VERSION = 1
        private const val TEXT = 1
        private const val DELIVERED = 2
        private const val READ = 3
        private const val TYPING = 4
        private const val MEDIA_PART = 5
        private const val PAD_BLOCK = 128
        private const val PAD_MARK = 0x80
        private const val MAX_FIELD = 64 * 1024
        private const val MAX_IDS = 500
        private const val DIGEST_SIZE = 32

        /** The most a photo piece carries: its envelope stays under the 131 kB some relays accept. */
        const val MEDIA_PART_BYTES = 48 * 1024

        /** The most pieces a photo may take, about three megabytes. */
        const val MAX_MEDIA_PARTS = 64

        /** Written out, then padded to a multiple of 128 bytes, so a message's length gives little away. */
        fun encode(payload: LinkPayload): ByteArray {
            val bytes = ByteArrayOutputStream()
            DataOutputStream(bytes).use { out ->
                out.writeByte(VERSION)
                when (payload) {
                    is Text -> {
                        out.writeByte(TEXT)
                        out.writeField(payload.id)
                        out.writeField(payload.body)
                        out.writeLong(payload.sentAt)
                    }
                    is Delivered -> {
                        out.writeByte(DELIVERED)
                        out.writeIds(payload.ids)
                    }
                    is Read -> {
                        out.writeByte(READ)
                        out.writeIds(payload.ids)
                    }
                    Typing -> out.writeByte(TYPING)
                    is MediaPart -> {
                        require(payload.count in 1..MAX_MEDIA_PARTS && payload.index in 0 until payload.count)
                        require(payload.digest.size == DIGEST_SIZE && payload.data.size <= MEDIA_PART_BYTES)
                        out.writeByte(MEDIA_PART)
                        out.writeField(payload.id)
                        out.writeInt(payload.index)
                        out.writeInt(payload.count)
                        out.writeLong(payload.sentAt)
                        out.writeField(payload.mime)
                        out.write(payload.digest)
                        out.writeInt(payload.data.size)
                        out.write(payload.data)
                    }
                }
            }
            return pad(bytes.toByteArray())
        }

        /** Null for anything this version does not understand. */
        fun decode(bytes: ByteArray): LinkPayload? = runCatching {
            val plain = unpad(bytes) ?: return null
            DataInputStream(ByteArrayInputStream(plain)).use { input ->
                if (input.readUnsignedByte() != VERSION) return null
                when (input.readUnsignedByte()) {
                    TEXT -> Text(id = input.readField(), body = input.readField(), sentAt = input.readLong())
                    DELIVERED -> Delivered(input.readIds())
                    READ -> Read(input.readIds())
                    TYPING -> Typing
                    MEDIA_PART -> {
                        val id = input.readField()
                        val index = input.readInt()
                        val count = input.readInt()
                        check(count in 1..MAX_MEDIA_PARTS && index in 0 until count)
                        val sentAt = input.readLong()
                        val mime = input.readField()
                        val digest = ByteArray(DIGEST_SIZE).also(input::readFully)
                        val size = input.readInt()
                        check(size in 0..MEDIA_PART_BYTES)
                        MediaPart(id, index, count, sentAt, mime, digest, ByteArray(size).also(input::readFully))
                    }
                    else -> null
                }
            }
        }.getOrNull()

        internal fun pad(bytes: ByteArray): ByteArray {
            val size = (bytes.size + 1 + PAD_BLOCK - 1) / PAD_BLOCK * PAD_BLOCK
            return bytes.copyOf(size).also { it[bytes.size] = PAD_MARK.toByte() }
        }

        internal fun unpad(bytes: ByteArray): ByteArray? {
            val mark = bytes.indexOfLast { it != 0.toByte() }
            if (mark < 0 || bytes[mark] != PAD_MARK.toByte()) return null
            return bytes.copyOf(mark)
        }

        private fun DataOutputStream.writeField(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            require(bytes.size <= MAX_FIELD) { "Message trop long" }
            writeInt(bytes.size)
            write(bytes)
        }

        private fun DataInputStream.readField(): String {
            val size = readInt()
            check(size in 0..MAX_FIELD)
            return ByteArray(size).also(::readFully).toString(Charsets.UTF_8)
        }

        private fun DataOutputStream.writeIds(ids: List<String>) {
            val kept = ids.take(MAX_IDS)
            writeInt(kept.size)
            kept.forEach { writeField(it) }
        }

        private fun DataInputStream.readIds(): List<String> {
            val count = readInt()
            check(count in 0..MAX_IDS)
            return List(count) { readField() }
        }
    }
}
