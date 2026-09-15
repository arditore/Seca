package com.seca.core.suite

import android.content.Context
import android.net.Uri
import com.seca.core.model.backup.BackupCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import androidx.annotation.StringRes

/**
 * The backup of the whole Seca suite, made or restored from any of its apps:
 * each installed app's part — contacts and profiles, calls and filtering, SMS
 * and conversations — together in one file encrypted with a passphrase.
 *
 * Seca Link's keys never leave the phone's secure hardware: after a
 * restoration on another phone, contacts are connected again.
 */
class SuiteBackup(context: Context) {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    enum class App(val key: String, @StringRes val label: Int, val authority: String) {
        Contacts("contacts", R.string.suite_app_contacts, "com.seca.contacts.backup"),
        Phone("phone", R.string.suite_app_phone, "com.seca.phone.backup"),
        Messages("messages", R.string.suite_app_messages, "com.seca.messages.backup"),
    }

    /** What a backup or a restoration gave: a line per app, or why nothing could be done. */
    sealed interface Outcome {
        data class Done(val lines: List<String>) : Outcome
        data class Failed(val reason: String) : Outcome
    }

    suspend fun export(target: Uri, passphrase: CharArray): Outcome = withContext(Dispatchers.IO) {
        val parts = JSONObject()
        val lines = App.entries.filter(::installed).map { app ->
            val part = runCatching {
                resolver.openInputStream(uriOf(app))?.use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) }
            }.getOrNull()
            if (part != null) {
                parts.put(app.key, part)
                lineOf(app, part.optString(KEY_SUMMARY).ifBlank { appContext.getString(R.string.suite_saved) })
            } else {
                lineOf(app, appContext.getString(R.string.suite_not_saved))
            }
        }
        if (parts.length() == 0) {
            passphrase.fill(' ')
            return@withContext Outcome.Failed(appContext.getString(R.string.suite_no_app))
        }
        val backup = JSONObject()
            .put("app", SUITE)
            .put("version", VERSION)
            .put("createdAt", System.currentTimeMillis())
            .put("parts", parts)
        val sealed = withContext(Dispatchers.Default) {
            BackupCipher.encrypt(backup.toString().toByteArray(Charsets.UTF_8), passphrase).also { passphrase.fill(' ') }
        }
        val written = runCatching { resolver.openOutputStream(target, "wt")?.use { it.write(sealed) } != null }.getOrDefault(false)
        if (written) Outcome.Done(lines) else Outcome.Failed(appContext.getString(R.string.suite_write_failed))
    }

    suspend fun restore(source: Uri, passphrase: CharArray): Outcome = withContext(Dispatchers.IO) {
        val bytes = runCatching { resolver.openInputStream(source)?.use { it.readBytes() } }.getOrNull()
        if (bytes == null) {
            passphrase.fill(' ')
            return@withContext Outcome.Failed(appContext.getString(R.string.suite_unreadable))
        }
        val plain = withContext(Dispatchers.Default) { BackupCipher.decrypt(bytes, passphrase).also { passphrase.fill(' ') } }
            ?: return@withContext Outcome.Failed(appContext.getString(R.string.suite_wrong_password))
        val backup = runCatching { JSONObject(plain.toString(Charsets.UTF_8)) }.getOrNull()?.takeIf { it.optString("app") == SUITE }
            ?: return@withContext Outcome.Failed(appContext.getString(R.string.suite_not_suite))
        val parts = backup.optJSONObject("parts") ?: JSONObject()
        val lines = App.entries.mapNotNull { app ->
            val part = parts.optJSONObject(app.key) ?: return@mapNotNull null
            if (!installed(app)) return@mapNotNull lineOf(app, appContext.getString(R.string.suite_not_installed))
            val sent = runCatching {
                resolver.openOutputStream(uriOf(app), "w")?.use { it.write(part.toString().toByteArray(Charsets.UTF_8)) } != null
            }.getOrDefault(false)
            lineOf(app, if (sent) resultOf(app) else appContext.getString(R.string.suite_unreachable))
        }
        if (lines.isEmpty()) Outcome.Failed(appContext.getString(R.string.suite_empty)) else Outcome.Done(lines)
    }

    /** Waits for an app to finish putting its part back. */
    private suspend fun resultOf(app: App): String {
        repeat(MAX_POLLS) {
            val result = runCatching { resolver.call(uriOf(app), METHOD_IMPORT_RESULT, null, null)?.getString(KEY_RESULT) }.getOrNull()
            if (result != null) return result
            delay(POLL_MILLIS)
        }
        return appContext.getString(R.string.suite_still_restoring)
    }

    private fun lineOf(app: App, text: String): String =
        appContext.getString(R.string.suite_line, appContext.getString(app.label), text)

    private fun installed(app: App): Boolean =
        runCatching { appContext.packageManager.resolveContentProvider(app.authority, 0) != null }.getOrDefault(false)

    private fun uriOf(app: App): Uri = Uri.parse("content://${app.authority}/part")

    companion object {
        const val PERMISSION = "com.seca.permission.BACKUP"
        const val METHOD_IMPORT_RESULT = "import_result"
        const val KEY_RESULT = "result"

        /** In each part: a few words on what it holds. */
        const val KEY_SUMMARY = "summary"

        private const val SUITE = "seca-suite"
        private const val VERSION = 1
        private const val POLL_MILLIS = 250L
        private const val MAX_POLLS = 4 * 60 * 10
    }
}
