package com.seca.phone.screening

/**
 * French numbers reserved for telemarketing. Since 2023 the ARCEP requires
 * automated and unsolicited sales calls to come from these ranges, so a call
 * from one of them is an advertisement by definition.
 */
internal object Telemarketing {

    private val FrenchRanges = listOf("162", "163", "270", "271", "377", "378", "424", "425", "568", "569", "948", "949")

    /** [e164] as "+33162…"; any other country is left alone. */
    fun isTelemarketing(e164: String): Boolean =
        e164.startsWith("+33") && FrenchRanges.any { e164.startsWith("+33$it") }
}
