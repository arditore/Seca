package com.seca.core.contacts

import android.provider.ContactsContract.CommonDataKinds.StructuredPostal

/**
 * An address, in the parts every other address book keeps it in.
 *
 * Seca used to hold one line of text. That is enough to show somebody, and it is not enough for
 * anything else: written out, the whole line lands in the street component, so Thunderbird and
 * every other client sees a town and a postcode crammed into the street; read back, the parts
 * another app wrote cannot be edited one at a time, because there is only one box to put them
 * in. An address is five things, and the only way to keep them five is to hold them as five.
 *
 * [formatted] is the line people read, assembled here rather than stored, so it can never
 * disagree with the parts it is made of.
 */
data class PostalAddress(
    val id: Long? = null,
    val street: String = "",
    val postcode: String = "",
    val city: String = "",
    val region: String = "",
    val country: String = "",
    val type: Int = StructuredPostal.TYPE_HOME,
    val label: String? = null,
) {

    /** What to show for this address's kind, when it is the owner's own word rather than a type. */
    val customLabel: String? get() = label?.takeIf { type == CUSTOM_TYPE && it.isNotBlank() }

    val isBlank: Boolean get() = parts.all { it.isBlank() }

    private val parts: List<String> get() = listOf(street, postcode, city, region, country)

    /**
     * The address on one line, the way an envelope reads.
     *
     * The postcode and the town belong together, so they are joined by a space and the rest by
     * commas: "12 rue des Lilas, 21000 Dijon, France" rather than a list of five things.
     */
    val formatted: String
        get() {
            val town = listOf(postcode, city).filter { it.isNotBlank() }.joinToString(" ")
            return listOf(street, town, region, country)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString(", ")
        }

    fun trimmed(): PostalAddress = copy(
        street = street.trim(),
        postcode = postcode.trim(),
        city = city.trim(),
        region = region.trim(),
        country = country.trim(),
    )

    companion object {

        /**
         * An address from a line of text, for what Seca itself wrote before it knew better.
         *
         * A guess, and it says so by only guessing where the shape is unmistakable: the last
         * piece is the country when it has no digits, and a piece that begins with digits and
         * carries a word after them is a postcode and a town. Anything it cannot read stays in
         * the street, where it is at least whole and visible, rather than being scattered into
         * fields it does not belong to.
         */
        fun from(line: String): PostalAddress {
            val pieces = line.split(',', '\n').map { it.trim() }.filter { it.isNotBlank() }
            if (pieces.size < 2) return PostalAddress(street = line.trim())
            val rest = pieces.toMutableList()
            val country = if (rest.size > 2 && rest.last().none(Char::isDigit)) rest.removeAt(rest.lastIndex) else ""
            val town = rest.lastOrNull()?.let(::townOf)
            if (town != null) rest.removeAt(rest.lastIndex)
            return PostalAddress(
                street = rest.joinToString(", "),
                postcode = town?.first.orEmpty(),
                city = town?.second.orEmpty(),
                country = country,
            )
        }

        /** "21000 Dijon" as a postcode and a town, or null when it is not that shape. */
        private fun townOf(piece: String): Pair<String, String>? {
            val match = TOWN.matchEntire(piece.trim()) ?: return null
            val postcode = match.groupValues[1]
            if (postcode.none(Char::isDigit)) return null
            return postcode to match.groupValues[2].trim()
        }

        /**
         * A postcode and then a town.
         *
         * The postcode is written in capitals and digits only, which is what tells it from the
         * rest of the address: "SW1A 1AA London" and "1012 AB Amsterdam" split correctly, and
         * "Flat 3 London" does not split at all, because "Flat" is not how anybody writes a
         * postcode. It must carry a digit somewhere, or a country code would pass for one.
         *
         * Leaving the capitals rule to the expression rather than checking it afterwards lets
         * it back up and try again: "21000 Saint Denis" finds "21000" and a town of two words,
         * where a check after the fact would have given up on the whole thing.
         */
        private val TOWN = Regex("""^([0-9A-Z][0-9A-Z\-]{1,8}(?: [0-9A-Z]{1,4})?)\s+(\p{L}[^,]*)$""")
    }
}
