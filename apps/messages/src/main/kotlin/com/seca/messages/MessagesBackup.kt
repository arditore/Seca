package com.seca.messages

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import com.seca.core.link.LinkSettings
import com.seca.core.suite.SuiteBackup
import com.seca.core.suite.SuiteBackupProvider
import com.seca.messages.link.LinkMessage
import com.seca.messages.link.LinkMessages
import com.seca.messages.link.LinkStatus
import com.seca.messages.link.LinkTimers
import com.seca.messages.sms.BackupMessage
import com.seca.messages.sms.CodeCleanup
import com.seca.messages.sms.CodeLifetime
import com.seca.messages.sms.MessagesRepository
import com.seca.messages.sms.ScheduledMessages
import com.seca.messages.sms.SpamFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Seca Messages' part of the suite's backup: the SMS, how conversations are
 * sorted, the messages waiting for their time, and Seca Link's settings and
 * text messages. Seca Link's keys stay in the phone's secure hardware, and
 * photos, recordings and messages that expire are not kept.
 */
internal class MessagesBackup(private val context: Context) {

    private val repository = MessagesRepository(context)
    private val prefs = ConversationPrefs(context)
    private val scheduled = ScheduledMessages(context)
    private val linkSettings = LinkSettings(context)
    private val links = LinkMessages(context)
    private val timers = LinkTimers.of(context)

    suspend fun export(): JSONObject = withContext(Dispatchers.IO) {
        val readable = granted(Manifest.permission.READ_SMS)
        val sms = if (readable) repository.allForBackup() else emptyList()
        // Conversations are known by thread on this phone; another phone numbers them its own way.
        val addressOf = if (readable) repository.conversations().associate { it.threadId to it.address } else emptyMap()
        fun addresses(ids: Collection<Long>) = JSONArray(ids.mapNotNull(addressOf::get))
        val now = System.currentTimeMillis()
        val linkMessages = links.all().filter { it.media == null && it.expiresAt == 0L }
        JSONObject()
            .put("app", APP)
            .put("version", VERSION)
            .put(
                "messages",
                JSONArray().apply {
                    sms.forEach { m ->
                        put(
                            JSONObject()
                                .put("address", m.address)
                                .put("body", m.body)
                                .put("date", m.date)
                                .put("dateSent", m.dateSent)
                                .put("type", m.type)
                                .put("read", m.read),
                        )
                    }
                },
            )
            .put(
                "conversations",
                JSONObject()
                    .put("pinned", addresses(prefs.pinned()))
                    .put("archived", addresses(prefs.archived()))
                    .put("spam", addresses(prefs.spam()))
                    .put("trusted", addresses(prefs.trusted())),
            )
            .put(
                "scheduled",
                JSONArray().apply {
                    scheduled.all().filter { it.at > now }.forEach {
                        put(JSONObject().put("address", it.address).put("body", it.body).put("at", it.at))
                    }
                },
            )
            .put(
                "settings",
                JSONObject()
                    .put("codeLifetime", CodeCleanup.lifetime(context).name)
                    .put("spamFilter", SpamFilter.enabled(context)),
            )
            .put(
                "link",
                JSONObject()
                    .put("readReceipts", linkSettings.readReceipts)
                    .put("typingIndicator", linkSettings.typingIndicator)
                    .put("useTor", linkSettings.useTor)
                    .put("relays", JSONArray(linkSettings.relays()))
                    .put("timers", JSONObject(timers.timers.value))
                    .put(
                        "messages",
                        JSONArray().apply {
                            linkMessages.forEach { m ->
                                put(
                                    JSONObject()
                                        .put("id", m.id)
                                        .put("number", m.number)
                                        .put("body", m.body)
                                        .put("date", m.date)
                                        .put("status", m.status.name)
                                        .put("replyTo", m.replyTo),
                                )
                            }
                        },
                    ),
            )
            .put(
                SuiteBackup.KEY_SUMMARY,
                if (readable) {
                    "${SuiteBackup.plural(sms.size, "SMS", "SMS")} et ${SuiteBackup.plural(linkMessages.size, "message Seca Link", "messages Seca Link")}"
                } else {
                    "réglages et Seca Link seulement, l'accès aux SMS est refusé"
                },
            )
    }

    /** Puts back what this phone is missing, replacing nothing the owner set here. Returns what came back. */
    suspend fun restore(part: JSONObject): String = withContext(Dispatchers.IO) {
        val lines = mutableListOf<String>()
        part.optJSONObject("settings")?.let { saved ->
            CodeLifetime.entries.firstOrNull { it.name == saved.optString("codeLifetime") }?.let { CodeCleanup.setLifetime(context, it) }
            if (saved.has("spamFilter")) SpamFilter.setEnabled(context, saved.optBoolean("spamFilter", true))
        }
        part.optJSONObject("link")?.let { link ->
            val added = restoreLink(link)
            if (added > 0) lines += SuiteBackup.plural(added, "message Seca Link", "messages Seca Link")
        }

        val saved = part.optJSONArray("messages") ?: JSONArray()
        if (saved.length() > 0) {
            // Only the default messaging app may write SMS.
            if (Telephony.Sms.getDefaultSmsPackage(context) == context.packageName) {
                val messages = (0 until saved.length()).mapNotNull { index ->
                    val item = saved.optJSONObject(index) ?: return@mapNotNull null
                    BackupMessage(
                        address = item.optString("address"),
                        body = item.optString("body"),
                        date = item.optLong("date"),
                        dateSent = item.optLong("dateSent"),
                        type = item.optInt("type"),
                        read = item.optBoolean("read", true),
                    )
                }
                lines.add(0, SuiteBackup.plural(repository.restore(messages), "SMS ajouté", "SMS ajoutés"))
            } else {
                lines.add(0, "SMS non remis : choisissez Seca Messages comme appli SMS, puis restaurez à nouveau")
            }
        }

        // After the SMS, so their conversations exist.
        if (granted(Manifest.permission.READ_SMS)) part.optJSONObject("conversations")?.let { restoreConversations(it) }

        val waiting = part.optJSONArray("scheduled") ?: JSONArray()
        val now = System.currentTimeMillis()
        val present = scheduled.all()
        var planned = 0
        for (index in 0 until waiting.length()) {
            val item = waiting.optJSONObject(index) ?: continue
            val address = item.optString("address")
            val body = item.optString("body")
            val at = item.optLong("at")
            if (address.isEmpty() || body.isEmpty() || at <= now) continue
            if (present.any { it.address == address && it.body == body && it.at == at }) continue
            scheduled.add(address, body, at)
            planned++
        }
        if (planned > 0) lines += SuiteBackup.plural(planned, "message programmé", "messages programmés")

        lines.joinToString(" ; ").ifEmpty { "réglages remis" }
    }

    private fun restoreLink(link: JSONObject): Int {
        if (link.has("readReceipts")) linkSettings.readReceipts = link.optBoolean("readReceipts", true)
        if (link.has("typingIndicator")) linkSettings.typingIndicator = link.optBoolean("typingIndicator", true)
        if (link.has("useTor")) linkSettings.useTor = link.optBoolean("useTor", false)
        val relays = link.optJSONArray("relays")?.let { array -> (0 until array.length()).map(array::getString) }.orEmpty()
        if (relays.isNotEmpty() && linkSettings.relays() == LinkSettings.DefaultRelays) linkSettings.setRelays(relays)
        link.optJSONObject("timers")?.let { saved ->
            saved.keys().forEach { number -> if (timers[number] == 0) timers.set(number, saved.optInt(number)) }
        }
        val messages = link.optJSONArray("messages") ?: return 0
        var added = 0
        for (index in 0 until messages.length()) {
            val item = messages.optJSONObject(index) ?: continue
            val id = item.optString("id")
            val number = item.optString("number")
            if (id.isEmpty() || number.isEmpty()) continue
            // A message still leaving when the backup was made never went.
            val status = LinkStatus.entries.firstOrNull { it.name == item.optString("status") }
                ?.let { if (it == LinkStatus.Sending) LinkStatus.Failed else it }
                ?: LinkStatus.Received
            val message = LinkMessage(
                id = id,
                number = number,
                body = item.optString("body"),
                date = item.optLong("date"),
                status = status,
                read = true,
                replyTo = if (item.isNull("replyTo")) null else item.optString("replyTo"),
            )
            if (links.add(message)) added++
        }
        return added
    }

    private suspend fun restoreConversations(saved: JSONObject) {
        val threadOf = repository.conversations().associate { it.address to it.threadId }
        fun threads(key: String): List<Long> =
            saved.optJSONArray(key)?.let { array -> (0 until array.length()).mapNotNull { threadOf[array.optString(it)] } }.orEmpty()
        val spam = prefs.spam()
        val trusted = prefs.trusted()
        threads("spam").filterNot { it in spam || it in trusted }.forEach { prefs.setSpam(it, true) }
        threads("trusted").filterNot { it in trusted }.forEach { prefs.setSpam(it, false) }
        val archived = prefs.archived()
        threads("archived").filterNot { it in archived }.forEach { prefs.setArchived(it, true) }
        val pinned = prefs.pinned()
        threads("pinned").filterNot { it in pinned }.forEach { prefs.setPinned(it, true) }
    }

    private fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val APP = "seca-messages"
        const val VERSION = 1
    }
}

/** Seca Messages' part of the suite's backup, for the Seca apps signed with the same key only. */
class MessagesSuiteBackup : SuiteBackupProvider() {

    override suspend fun exportPart(): JSONObject = MessagesBackup(requireContext()).export()

    override suspend fun importPart(part: JSONObject): String = MessagesBackup(requireContext()).restore(part)
}
