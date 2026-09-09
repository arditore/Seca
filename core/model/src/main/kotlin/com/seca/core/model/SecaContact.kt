package com.seca.core.model

/** A contact as Seca understands it, independent of any storage concern. */
data class SecaContact(
    val id: Long,
    val displayName: String,
    val phoneNumbers: List<PhoneNumber>,
    val isFavorite: Boolean,
    val photoUri: String?,
) {
    /** Up to two letters used when no photo is available. */
    val initials: String
        get() = displayName
            .split(' ', '\t', '\n')
            .mapNotNull { word -> word.firstOrNull { it.isLetter() } }
            .take(2)
            .joinToString("")
            .uppercase()
}
