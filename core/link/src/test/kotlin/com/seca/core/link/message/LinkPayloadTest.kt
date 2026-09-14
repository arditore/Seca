package com.seca.core.link.message

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class LinkPayloadTest {

    @Test
    fun `every payload comes back as it was sent`() {
        listOf(
            LinkPayload.Text("id-1", "Salut, ça va ? On se voit à 18 h", 1_700_000_000_000),
            LinkPayload.Text("id-2", "Oui !", 1_700_000_000_000, replyTo = "id-1", expiresInSeconds = 3600),
            LinkPayload.Delivered(listOf("a", "b")),
            LinkPayload.Read(listOf("c")),
            LinkPayload.Typing,
            LinkPayload.Reaction("id-1", "❤️"),
            LinkPayload.Reaction("id-1", ""),
            LinkPayload.ExpiryTimer(86_400),
        ).forEach { assertEquals(it, LinkPayload.decode(LinkPayload.encode(it))) }
    }

    @Test
    fun `a text from a version before replies and timers still reads`() {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeByte(1)
            out.writeByte(1)
            listOf("old", "Bonjour").forEach {
                val field = it.toByteArray()
                out.writeInt(field.size)
                out.write(field)
            }
            out.writeLong(42)
        }
        assertEquals(LinkPayload.Text("old", "Bonjour", 42), LinkPayload.decode(LinkPayload.pad(bytes.toByteArray())))
    }

    @Test
    fun `lengths are rounded up, so they give little away`() {
        val short = LinkPayload.encode(LinkPayload.Text("x", "a", 0))
        assertEquals(128, short.size)
        assertEquals(short.size, LinkPayload.encode(LinkPayload.Text("x", "une phrase un peu plus longue", 0)).size)
    }

    @Test
    fun `a media piece comes back whole`() {
        val data = ByteArray(LinkPayload.MEDIA_PART_BYTES) { it.toByte() }
        val digest = ByteArray(32) { 7 }
        val sent = LinkPayload.MediaPart("photo-1", 2, 5, 1_700_000_000_000, "image/webp", digest, data, expiresInSeconds = 300)
        val part = LinkPayload.decode(LinkPayload.encode(sent)) as LinkPayload.MediaPart
        assertEquals("photo-1", part.id)
        assertEquals(2, part.index)
        assertEquals(5, part.count)
        assertEquals(1_700_000_000_000, part.sentAt)
        assertEquals("image/webp", part.mime)
        assertEquals(300, part.expiresInSeconds)
        assertArrayEquals(digest, part.digest)
        assertArrayEquals(data, part.data)
    }

    @Test
    fun `a piece claiming more than a photo may hold is refused`() {
        val oversized = LinkPayload.MediaPart("p", 0, LinkPayload.MAX_MEDIA_PARTS + 1, 0, "image/webp", ByteArray(32), ByteArray(1))
        assertThrows(IllegalArgumentException::class.java) { LinkPayload.encode(oversized) }
    }

    @Test
    fun `a timer longer than four weeks is kept to four weeks`() {
        val decoded = LinkPayload.decode(LinkPayload.encode(LinkPayload.ExpiryTimer(Int.MAX_VALUE)))
        assertEquals(LinkPayload.ExpiryTimer(LinkPayload.MAX_EXPIRY_SECONDS), decoded)
    }

    @Test
    fun `anything else is not a payload`() {
        assertNull(LinkPayload.decode(byteArrayOf(1, 2, 3)))
        assertNull(LinkPayload.decode(ByteArray(128)))
    }
}
