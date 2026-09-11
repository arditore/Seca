package com.seca.contacts

import android.telephony.PhoneNumberUtils
import java.util.Locale

/** Spaces a number the way the phone's region writes it, or leaves it as stored when that fails. */
internal fun formatNumber(raw: String): String =
    PhoneNumberUtils.formatNumber(raw, Locale.getDefault().country) ?: raw
