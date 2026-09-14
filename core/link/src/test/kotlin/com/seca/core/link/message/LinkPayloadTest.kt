package com.seca.core.link.message

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `anything else is not a payload`() {
        assertNull(LinkPayload.decode(byteArrayOf(1, 2, 3)))
        assertNull(LinkPayload.decode(ByteArray(128)))
    }
}
