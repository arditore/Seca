package com.seca.core.link.message

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnvelopeCipherTest {

    private val shared = ByteArray(32) { it.toByte() }
    private val associated = byteArrayOf(9, 9)
    private val plain = "bonjour".toByteArray()

    @Test
    fun `what is sealed opens with the same secret`() {
        assertArrayEquals(plain, EnvelopeCipher.open(shared, EnvelopeCipher.seal(shared, plain, associated), associated))
    }

    @Test
    fun `a changed byte, secret or context does not open`() {
        val sealed = EnvelopeCipher.seal(shared, plain, associated)
        val altered = sealed.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        assertNull(EnvelopeCipher.open(shared, altered, associated))
        assertNull(EnvelopeCipher.open(ByteArray(32), sealed, associated))
        assertNull(EnvelopeCipher.open(shared, sealed, byteArrayOf(1)))
    }
}
