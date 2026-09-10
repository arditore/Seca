package com.seca.core.model

/** A contact as Seca understands it, independent of any storage concern. */
data class SecaContact(
    val id: Long,
    val displayName: String,
    val phoneNumbers: List<PhoneNumber>,
    val isFavorite: Boolean,
    val photoUri: String?,
    /** The provider's stable key: unlike [id], it survives contacts being re-aggregated. */
    val lookupKey: String = "",
) {
    /** Up to two letters used when no photo is available. */
    val initials: String
        get() = initialsOf(displayName)
}

private val Whitespace = Regex("\\s+")

/** Up to two letters taken from the first words of [name], for avatars. */
fun initialsOf(name: String): String = name
    .split(Whitespace)
    .mapNotNull { word -> word.firstOrNull { it.isLetter() } }
    .take(2)
    .joinToString("")
    .uppercase()
