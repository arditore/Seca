package com.seca.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SecaContactTest {

    private fun contact(name: String) = SecaContact(
        id = 1L,
        displayName = name,
        phoneNumbers = emptyList(),
        isFavorite = false,
        photoUri = null,
    )

    @Test
    fun `initials use the first letter of the first two words`() {
        assertEquals("CD", contact("Camille Durand").initials)
    }

    @Test
    fun `initials fall back to a single letter for one word`() {
        assertEquals("C", contact("Camille").initials)
    }

    @Test
    fun `initials are empty for a blank name`() {
        assertEquals("", contact("   ").initials)
    }

    @Test
    fun `initials skip non letter leading characters`() {
        assertEquals("A", contact("+ Alice").initials)
    }
}
