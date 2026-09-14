package com.seca.core.link.message

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class LinkPayloadTest {

    @Test
    fun `every payload comes back as it was sent`() {
        listOf(
            LinkPayload.Text("id-1", "Salut, ça va ? On se voit à 18 h", 1_700_000_000_000),
            LinkPayload.Delivered(listOf("a", "b")),
            LinkPayload.Read(listOf("c")),
            LinkPayload.Typing,
        ).forEach { assertEquals(it, LinkPayload.decode(LinkPayload.encode(it))) }
    }

    @Test
    fun `lengths are rounded up, so they give little away`() {
        val short = LinkPayload.encode(LinkPayload.Text("x", "a", 0))
        assertEquals(128, short.size)
        assertEquals(short.size, LinkPayload.encode(LinkPayload.Text("x", "une phrase un peu plus longue", 0)).size)
    }

    @Test
    fun `a photo piece comes back whole`() {
        val data = ByteArray(LinkPayload.MEDIA_PART_BYTES) { it.toByte() }
        val digest = ByteArray(32) { 7 }
        val sent = LinkPayload.MediaPart("photo-1", 2, 5, 1_700_000_000_000, "image/webp", digest, data)
        val part = LinkPayload.decode(LinkPayload.encode(sent)) as LinkPayload.MediaPart
        assertEquals("photo-1", part.id)
        assertEquals(2, part.index)
        assertEquals(5, part.count)
        assertEquals(1_700_000_000_000, part.sentAt)
        assertEquals("image/webp", part.mime)
        assertArrayEquals(digest, part.digest)
        assertArrayEquals(data, part.data)
    }

    @Test
    fun `a piece claiming more than a photo may hold is refused`() {
        val oversized = LinkPayload.MediaPart("p", 0, LinkPayload.MAX_MEDIA_PARTS + 1, 0, "image/webp", ByteArray(32), ByteArray(1))
        assertThrows(IllegalArgumentException::class.java) { LinkPayload.encode(oversized) }
    }

    @Test
    fun `anything else is not a payload`() {
        assertNull(LinkPayload.decode(byteArrayOf(1, 2, 3)))
        assertNull(LinkPayload.decode(ByteArray(128)))
    }
}
