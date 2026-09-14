package com.seca.core.design.privacy

import android.app.Activity
import android.os.Build

/** Android 13 can hide an app's preview in the recent apps by itself; before, FLAG_SECURE already blanks it. */
internal fun Activity.showInRecentsPreview(shown: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(shown)
}
