package com.seca.core.link

import org.signal.libsignal.protocol.fingerprint.ScannableFingerprint

/** What two people compare to be sure no one sits between their phones. */
class SafetyNumber internal constructor(
    /** Sixty digits, the same on both phones. */
    val digits: String,
    private val scannable: ScannableFingerprint,
) {
    /** The bytes to show as a QR code. */
    val code: ByteArray get() = scannable.serialized

    /** Whether a code scanned on the other phone matches this one. */
    fun matches(scanned: ByteArray): Boolean = runCatching { scannable.compareTo(scanned) }.getOrDefault(false)
}
