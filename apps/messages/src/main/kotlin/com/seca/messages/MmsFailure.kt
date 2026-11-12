package com.seca.messages

import androidx.annotation.StringRes
import android.telephony.SmsManager

/**
 * Why a picture message did not go, as the carrier put it.
 *
 * Half of these are something the owner can put right in ten seconds, and the rest are worth
 * knowing are not their fault. A message that says only "failed" leaves somebody retrying the
 * same thing on a phone with its data turned off.
 *
 * Null for a reason with no name, where "failed" is all there is honestly to say.
 */
@StringRes
internal fun mmsFailureReason(code: Int): Int? = when (code) {
    SmsManager.MMS_ERROR_NO_DATA_NETWORK -> R.string.mms_no_network
    SmsManager.MMS_ERROR_DATA_DISABLED -> R.string.mms_data_off
    SmsManager.MMS_ERROR_MMS_DISABLED_BY_CARRIER -> R.string.mms_off_at_carrier
    SmsManager.MMS_ERROR_INACTIVE_SUBSCRIPTION -> R.string.mms_sim_inactive
    SmsManager.MMS_ERROR_INVALID_APN, SmsManager.MMS_ERROR_CONFIGURATION_ERROR -> R.string.mms_apn
    SmsManager.MMS_ERROR_UNABLE_CONNECT_MMS, SmsManager.MMS_ERROR_HTTP_FAILURE -> R.string.mms_no_server
    SmsManager.MMS_ERROR_RETRY -> R.string.mms_retry
    else -> null
}
