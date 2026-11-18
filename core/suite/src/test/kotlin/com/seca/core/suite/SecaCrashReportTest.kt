package com.seca.core.suite

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The crash report is the one thing in the suite that only runs when everything else has
 * already gone wrong, so it is the one thing that has to be tested rather than tried.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SecaCrashReportTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private var original: Thread.UncaughtExceptionHandler? = null

    @Before
    fun remember() {
        original = Thread.getDefaultUncaughtExceptionHandler()
        SecaCrashReport.clear(context)
    }

    @After
    fun putBack() {
        Thread.setDefaultUncaughtExceptionHandler(original)
        SecaCrashReport.clear(context)
    }

    @Test
    fun `nothing is offered when nothing crashed`() {
        assertNull(SecaCrashReport.pending(context))
    }

    @Test
    fun `a crash is kept, with the exception and where it came from`() {
        SecaCrashReport.install(context, "Seca Messages")
        Thread.getDefaultUncaughtExceptionHandler()
            ?.uncaughtException(Thread.currentThread(), IllegalStateException("the thing that broke"))

        val report = SecaCrashReport.pending(context)
        assertTrue("nothing was kept", report != null)
        assertTrue("the app is not named: $report", report!!.contains("Seca Messages"))
        assertTrue("the phone is not named: $report", report.contains("Android"))
        assertTrue("the exception is missing: $report", report.contains("IllegalStateException"))
        assertTrue("the message is missing: $report", report.contains("the thing that broke"))
        assertTrue("the trace is missing: $report", report.contains("at "))
    }

    @Test
    fun `saying no forgets it`() {
        SecaCrashReport.install(context, "Seca Phone")
        Thread.getDefaultUncaughtExceptionHandler()
            ?.uncaughtException(Thread.currentThread(), RuntimeException("once"))
        assertTrue(SecaCrashReport.pending(context) != null)

        SecaCrashReport.clear(context)
        assertNull(SecaCrashReport.pending(context))
    }

    @Test
    fun `the handler that was there still runs, so Android still ends the process`() {
        var sawIt: Throwable? = null
        Thread.setDefaultUncaughtExceptionHandler { _, error -> sawIt = error }
        SecaCrashReport.install(context, "Seca Contacts")

        val thrown = RuntimeException("passed along")
        Thread.getDefaultUncaughtExceptionHandler()?.uncaughtException(Thread.currentThread(), thrown)

        assertEquals(thrown, sawIt)
    }

    @Test
    fun `installing twice does not stack handlers`() {
        SecaCrashReport.install(context, "Seca Messages")
        val once = Thread.getDefaultUncaughtExceptionHandler()
        SecaCrashReport.install(context, "Seca Messages")
        assertEquals(once, Thread.getDefaultUncaughtExceptionHandler())
    }
}
