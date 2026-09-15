package com.seca.messages

import android.app.Application
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SuitePermissionsTest {

    @Test
    fun `declares every Seca permission it asks for`() {
        // Android only grants a signature permission that was already defined when the app was
        // installed. Declaring it here too lets the suite work in whatever order its apps are installed.
        val context = ApplicationProvider.getApplicationContext<Application>()
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val requested = info.requestedPermissions.orEmpty().filter { it.startsWith("com.seca.permission.") }.toSet()
        val declared = info.permissions.orEmpty().map { it.name }.toSet()
        assertEquals(emptySet<String>(), requested - declared)
    }
}
