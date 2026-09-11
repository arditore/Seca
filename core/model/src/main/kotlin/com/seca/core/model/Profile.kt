package com.seca.core.model

/** A category of contacts, such as "Travail". Every contact belongs to exactly one. */
data class Profile(val id: String, val name: String) {
    companion object {
        /** Where contacts sit until they are put elsewhere; it cannot be renamed or deleted. */
        val Principal = Profile(id = "principal", name = "Principal")
    }
}
