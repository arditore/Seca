package com.seca.core.design.component

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import com.seca.core.design.SecaAppIdentity
import org.robolectric.Shadows.shadowOf

/**
 * Puts the sibling apps on the pretend phone.
 *
 * The suite bar only shows an app that is actually installed, because somebody who had
 * installed one of the three was being offered two tabs that led to a shop. A test phone has
 * none of them installed, so a test about what the bar shows has to say which are there
 * before it can mean anything.
 */
internal fun installSiblings(vararg apps: SecaAppIdentity) {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val packages = shadowOf(context.packageManager)
    apps.forEach { app ->
        packages.installPackage(PackageInfo().apply { packageName = app.packageName })
        val launcher = ComponentName(app.packageName, "${app.packageName}.MainActivity")
        packages.addActivityIfNotPresent(launcher)
        packages.addIntentFilterForActivity(
            launcher,
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
        )
    }
}
