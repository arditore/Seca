package com.seca.core.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Where a new contact is offered, and in what order.
 *
 * The order is the whole point: the account the owner chose in the phone's settings comes first,
 * because from Android 16 writing elsewhere is either against their wishes or refused outright.
 */
class NewContactPlaceTest {

    @Test
    fun `the account the phone names is tried first`() {
        val chosen = Place(type = "com.infomaniak", name = "philippe@example.org")
        val places = placesToTry(chosen)
        assertEquals(3, places.size)
        assertEquals("com.infomaniak", places[0]?.type)
        assertEquals("philippe@example.org", places[0]?.name)
    }

    @Test
    fun `with nothing chosen the provider is left to decide first`() {
        val places = placesToTry(null)
        assertEquals(2, places.size)
        // Naming no account at all: the provider applies the owner's own choice.
        assertNull(places[0])
    }

    @Test
    fun `saying nothing always comes before saying nothing explicitly`() {
        listOf(null, Place("com.google", "someone@example.org")).forEach { chosen ->
            val places = placesToTry(chosen)
            val unspecified = places.indexOfFirst { it == null }
            val explicitlyNone = places.indexOfFirst { it != null && it.type == null && it.name == null }
            assertEquals(places.size - 1, explicitlyNone)
            assertEquals(true, unspecified < explicitlyNone)
        }
    }

    @Test
    fun `the last place is the device, named as nothing`() {
        val last = placesToTry(Place("com.google", "someone@example.org")).last()
        assertNull(last?.type)
        assertNull(last?.name)
    }
}
