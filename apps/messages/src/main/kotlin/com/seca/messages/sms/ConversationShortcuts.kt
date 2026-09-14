package com.seca.messages.sms

import android.content.pm.ShortcutInfo
import android.os.Build

/**
 * Conversation shortcuts are published from Android 13 on, where they can be
 * kept off the launcher: before, the names of recent conversations would show
 * on the app's icon.
 */
internal val conversationShortcutsAllowed: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

internal fun ShortcutInfo.Builder.keptOffLauncher(): ShortcutInfo.Builder = apply {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setExcludedFromSurfaces(ShortcutInfo.SURFACE_LAUNCHER)
}
