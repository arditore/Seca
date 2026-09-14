package com.seca.phone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher

/** Runs [then] once notifications may be shown: Android 13 asks the owner first, older versions never do. */
internal fun withNotificationPermission(context: Context, launcher: ActivityResultLauncher<String>, then: () -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    ) {
        then()
    } else {
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
