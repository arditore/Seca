package com.seca.core.suite

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.FileNotFoundException
import kotlin.concurrent.thread

/**
 * One Seca app's part of the suite's backup. Whichever Seca app makes or
 * restores a backup reads each app's part through this provider, and writes it
 * back the same way. The manifest guards it with [SuiteBackup.PERMISSION]: only
 * apps signed with the Seca key reach it.
 *
 * Parts travel through a pipe rather than in one call, so a phone full of
 * messages is never too large to go through.
 */
abstract class SuiteBackupProvider : ContentProvider() {

    /** This app's part, read off the main thread; [SuiteBackup.KEY_SUMMARY] says in a few words what it holds. */
    protected abstract suspend fun exportPart(): JSONObject

    /** Puts this app's part back, off the main thread; returns in a few words what came back. */
    protected abstract suspend fun importPart(part: JSONObject): String

    @Volatile
    private var lastImport: String? = null

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val (read, write) = ParcelFileDescriptor.createReliablePipe()
        return when {
            mode == "r" -> {
                thread(name = "seca-backup-export") {
                    ParcelFileDescriptor.AutoCloseOutputStream(write).use { out ->
                        // A part that could not be read leaves the pipe empty: the backup says so for this app.
                        val part = runCatching { runBlocking { exportPart() } }.getOrNull() ?: return@use
                        out.write(part.toString().toByteArray(Charsets.UTF_8))
                    }
                }
                read
            }
            mode.startsWith("w") -> {
                lastImport = null
                thread(name = "seca-backup-import") {
                    val text = runCatching { ParcelFileDescriptor.AutoCloseInputStream(read).use { it.readBytes().toString(Charsets.UTF_8) } }
                        .getOrNull()
                    lastImport = runCatching { runBlocking { importPart(JSONObject(text.orEmpty())) } }
                        .getOrElse { "restauration impossible" }
                }
                write
            }
            else -> throw FileNotFoundException("Mode non pris en charge : $mode")
        }
    }

    /** What the last restoration gave, once it is done; null while it runs. */
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        // Unlike files, calls are not guarded by the manifest: the permission is checked here.
        if (context?.checkCallingOrSelfPermission(SuiteBackup.PERMISSION) != PackageManager.PERMISSION_GRANTED) return null
        if (method != SuiteBackup.METHOD_IMPORT_RESULT) return null
        return Bundle().apply { putString(SuiteBackup.KEY_RESULT, lastImport) }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? =
        null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
