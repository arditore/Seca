package com.seca.phone.call

import android.app.NotificationManager
import android.os.Build

/** Android 14 lets the owner withdraw full-screen notifications; before, the phone app always had them. */
internal fun NotificationManager.fullScreenAllowed(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || canUseFullScreenIntent()
