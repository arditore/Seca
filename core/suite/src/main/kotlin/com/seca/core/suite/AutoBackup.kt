package com.seca.core.suite

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** One thing in the owner's folder: what it is called and how to reach it. */
private class Entry(val id: String, val name: String, val isFolder: Boolean)

/**
 * Writes the backups the owner asked to have written for them.
 *
 * Two folders, named for what is in them, inside the one folder they chose:
 *
 * ```
 * Seca complete/        seca-2026-11-05-1530.seca      encrypted, everything, to put a phone back
 * Contacts, calls, texts/
 *                       contacts-2026-11-05-1530.vcf   vCard
 *                       messages-2026-11-05-1530.xml   the dialect every archiving tool reads
 *                       calls-2026-11-05-1530.xml      the same, for the call log
 * ```
 *
 * They do not hold the same thing, and the names say so. The complete one is everything, sealed
 * with the owner's phrase. The other is readable by anything that finds it — which is what it is
 * for, and also why it is off until asked for — and carries no profiles, no call filtering, no
 * pinned conversations and no Seca Link keys, because no portable format has anywhere to put them.
 *
 * Each folder keeps its own count of copies, so pruning one never touches the other.
 */
object AutoBackup {

    /** What one run did, in the owner's words, or what stopped it. */
    sealed interface Result {
        data class Written(val files: List<String>) : Result
        data class Failed(val reason: String) : Result
    }

    suspend fun run(context: Context): Result = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val settings = AutoBackupSettings(app)
        val chosen = settings.folder ?: return@withContext failed(settings, app.getString(R.string.auto_no_folder))
        val root = rootOf(app.contentResolver, chosen)
            ?: return@withContext failed(settings, app.getString(R.string.auto_folder_gone))
        val phrase = settings.passphrase()
            ?: return@withContext failed(settings, app.getString(R.string.auto_no_passphrase))

        val resolver = app.contentResolver
        val stamp = LocalDateTime.now().format(STAMP)
        val written = mutableListOf<String>()

        val complete = folder(resolver, chosen, root, app.getString(R.string.auto_folder_complete))
            ?: return@withContext failed(settings, app.getString(R.string.auto_folder_failed))
        val sealedName = "seca-$stamp.seca"
        val target = create(resolver, chosen, complete, TYPE_SEALED, sealedName)
            ?: return@withContext failed(settings, app.getString(R.string.auto_write_failed))
        when (val outcome = SuiteBackup(app).export(target, phrase)) {
            is SuiteBackup.Outcome.Failed -> {
                // A file holding nothing is worse than no file: it would be kept, counted, and
                // one day restored from.
                runCatching { DocumentsContract.deleteDocument(resolver, target) }
                return@withContext failed(settings, outcome.reason)
            }
            is SuiteBackup.Outcome.Done -> written += sealedName
        }
        prune(resolver, chosen, complete, settings.keep)

        if (settings.portable) {
            val open = folder(resolver, chosen, root, app.getString(R.string.auto_folder_portable))
            if (open != null) {
                val backup = SuiteBackup(app)
                backup.installedApps().forEach { which ->
                    val text = backup.exportPortable(which) ?: return@forEach
                    val name = "${which.portableName}-$stamp.${which.portableExtension}"
                    val file = create(resolver, chosen, open, typeOf(which.portableExtension), name) ?: return@forEach
                    val put = runCatching {
                        resolver.openOutputStream(file, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
                    }.getOrDefault(false)
                    if (put) {
                        written += name
                    } else {
                        runCatching { DocumentsContract.deleteDocument(resolver, file) }
                    }
                }
                prune(resolver, chosen, open, settings.keep * PORTABLE_FILES_PER_RUN)
            }
        }

        settings.lastRun = System.currentTimeMillis()
        settings.lastFailure = null
        Result.Written(written)
    }

    private fun failed(settings: AutoBackupSettings, reason: String): Result {
        settings.lastFailure = reason
        return Result.Failed(reason)
    }

    /** The chosen folder itself, or null when it has been moved, deleted or revoked. */
    private fun rootOf(resolver: ContentResolver, tree: Uri): String? = runCatching {
        val id = DocumentsContract.getTreeDocumentId(tree)
        // Asking for its children is the cheapest honest test that it is still there.
        resolver.query(
            DocumentsContract.buildChildDocumentsUriUsingTree(tree, id),
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
            null,
            null,
            null,
        )?.use { }
        id
    }.getOrNull()

    /** The folder by that name inside [parent], made if it is not there yet. */
    private fun folder(resolver: ContentResolver, tree: Uri, parent: String, name: String): String? {
        children(resolver, tree, parent).firstOrNull { it.isFolder && it.name == name }?.let { return it.id }
        val made = create(resolver, tree, parent, DocumentsContract.Document.MIME_TYPE_DIR, name)
        return made?.let { DocumentsContract.getDocumentId(it) }
    }

    private fun create(resolver: ContentResolver, tree: Uri, parent: String, mimeType: String, name: String): Uri? =
        runCatching {
            DocumentsContract.createDocument(
                resolver,
                DocumentsContract.buildDocumentUriUsingTree(tree, parent),
                mimeType,
                name,
            )
        }.getOrNull()

    private fun children(resolver: ContentResolver, tree: Uri, parent: String): List<Entry> = runCatching {
        resolver.query(
            DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent),
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        Entry(
                            id = cursor.getString(0),
                            name = cursor.getString(1).orEmpty(),
                            isFolder = cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                        ),
                    )
                }
            }
        }
    }.getOrNull().orEmpty()

    /**
     * Keeps the newest [keep] files and deletes the rest.
     *
     * By the name rather than by the date the file system reports: the names carry the moment
     * they were written, in an order that sorts, and a folder synced to a computer and back can
     * come home with every date set to today.
     */
    private fun prune(resolver: ContentResolver, tree: Uri, parent: String, keep: Int) {
        if (keep <= 0) return
        children(resolver, tree, parent)
            .filterNot(Entry::isFolder)
            .sortedByDescending(Entry::name)
            .drop(keep)
            .forEach { entry ->
                runCatching {
                    DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, entry.id))
                }
            }
    }

    private fun typeOf(extension: String): String = when (extension) {
        "vcf" -> "text/vcard"
        "xml" -> "text/xml"
        else -> TYPE_SEALED
    }

    private const val TYPE_SEALED = "application/octet-stream"

    /** Contacts, calls and texts: three files from one run, so three times the count is kept. */
    private const val PORTABLE_FILES_PER_RUN = 3

    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")
}
