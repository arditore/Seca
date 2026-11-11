package com.seca.core.design.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import com.seca.core.design.R

/**
 * Puts something on the clipboard and says so, once, in the same words everywhere.
 *
 * Android 13 and newer show their own confirmation when anything is copied, and a toast on top
 * of it is the same sentence twice. Older versions show nothing, so the app says it itself.
 */
fun secaCopy(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, context.getString(R.string.design_copied), Toast.LENGTH_SHORT).show()
    }
}
