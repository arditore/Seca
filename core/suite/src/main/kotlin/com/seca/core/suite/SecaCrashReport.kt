package com.seca.core.suite

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Keeps the last crash on the phone, so the next one can be answered with a line number.
 *
 * Seca sends nothing anywhere and has no crash reporting, which is the point of it and also
 * why every report arrives blind: somebody writes that the app closes when they open their own
 * conversation, and there is nothing to read but the sentence. Guessing from a sentence has
 * been wrong three times out of three in this project.
 *
 * So the trace is written here, to the app's own storage, and offered the next time the app
 * opens. Nothing leaves the phone unless the owner reads it and chooses to send it. It is shown
 * in full first, because a stack trace can carry a number or a name in a message and nobody
 * should be asked to send a thing they have not seen.
 *
 * One file, overwritten. The crash that matters is the one that just happened.
 */
object SecaCrashReport {

    private const val FILE = "last-crash.txt"

    /**
     * Starts keeping them. Called when the app's own screen opens, which is early enough: a
     * crash before that has no owner watching to report it.
     *
     * The handler that was there is called afterwards, so Android still ends the process and
     * still says the app stopped. Swallowing that would leave a frozen screen and a worse
     * problem than the one being recorded.
     */
    fun install(context: Context, appName: String) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is Handler) return
        Thread.setDefaultUncaughtExceptionHandler(Handler(app, appName, previous))
    }

    /** The trace from the last crash, or null when the last run ended properly. */
    fun pending(context: Context): String? = runCatching {
        File(context.applicationContext.filesDir, FILE).takeIf { it.isFile }?.readText()
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /** Forgets it, once the owner has sent it or said no. */
    fun clear(context: Context) {
        runCatching { File(context.applicationContext.filesDir, FILE).delete() }
    }

    private class Handler(
        private val app: Context,
        private val appName: String,
        private val previous: Thread.UncaughtExceptionHandler?,
    ) : Thread.UncaughtExceptionHandler {

        override fun uncaughtException(thread: Thread, error: Throwable) {
            // Writing must not be what stops the phone from reporting the crash properly.
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                File(app.filesDir, FILE).writeText(
                    buildString {
                        appendLine("$appName ${versionOf(app)}")
                        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
                        appendLine("thread: ${thread.name}")
                        appendLine()
                        append(trace)
                    },
                )
            }
            previous?.uncaughtException(thread, error)
        }

        private fun versionOf(context: Context): String = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        }.getOrDefault("")
    }
}
