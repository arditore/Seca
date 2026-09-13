package com.seca.core.model.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupCipherTest {

    private val content = "Camille Durand, 06 12 34 56 78".toByteArray()

    @Test
    fun `a backup opens with its passphrase`() {
        val sealed = BackupCipher.encrypt(content, "correct horse".toCharArray())

        assertArrayEquals(content, BackupCipher.decrypt(sealed, "correct horse".toCharArray()))
    }

    @Test
    fun `a wrong passphrase or a damaged file opens nothing`() {
        val sealed = BackupCipher.encrypt(content, "correct horse".toCharArray())
        val damaged = sealed.copyOf().also { it[it.lastIndex] = (it[it.lastIndex] + 1).toByte() }

        assertNull(BackupCipher.decrypt(sealed, "wrong horse".toCharArray()))
        assertNull(BackupCipher.decrypt(damaged, "correct horse".toCharArray()))
        assertNull(BackupCipher.decrypt("not a backup".toByteArray(), "correct horse".toCharArray()))
    }
}
