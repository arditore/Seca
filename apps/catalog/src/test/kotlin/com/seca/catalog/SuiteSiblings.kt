package com.seca.catalog

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
 * The suite bar only shows an app that is actually installed, and a test phone has none of
 * them, so a test that taps the bar has to say which are there first. The same helper sits in
 * `:core:design`'s tests; a test source set is not shared between modules, and one shared
 * module for twenty lines of test scaffolding costs more than the second copy.
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
