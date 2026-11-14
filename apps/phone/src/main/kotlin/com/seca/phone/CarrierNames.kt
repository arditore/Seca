package com.seca.phone

import android.content.Context

/**
 * The names carriers sent with incoming calls. Telecom does not keep them in the call
 * history, so the phone app keeps the latest per number, on this phone only, for the
 * 200 most recent callers.
 *
 * Read once into memory, then every lookup is a map hit; the file is written only when a
 * name is new or changed.
 */
object CarrierNames {
    private const val FILE = "carrier_names"
    private const val ALL = "all"
    private const val LIMIT = 200

    /** Oldest first, so the first entry is the one to drop. */
    private var names: LinkedHashMap<String, String>? = null

    /** Numbers match by their last nine digits, so "+33 6 12 34 56 78" and "06 12 34 56 78" are one. */
    internal fun keyOf(number: String): String = number.filter(Char::isDigit).takeLast(9)

    fun remember(context: Context, number: String, name: String) {
        val key = keyOf(number).ifEmpty { return }
        val clean = name.filterNot { it == '\n' || it == '\t' }.trim().ifEmpty { return }
        synchronized(this) {
            val all = loaded(context)
            if (all[key] == clean) return
            all.remove(key)
            all[key] = clean
            while (all.size > LIMIT) all.remove(all.keys.first())
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putString(ALL, all.entries.joinToString("\n") { "${it.key}\t${it.value}" })
                .apply()
        }
    }

    fun of(context: Context, number: String): String? {
        val key = keyOf(number).ifEmpty { return null }
        return synchronized(this) { loaded(context)[key] }
    }

    private fun loaded(context: Context): LinkedHashMap<String, String> = names ?: run {
        val read = LinkedHashMap<String, String>()
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(ALL, null)
            ?.lineSequence()
            ?.forEach { line ->
                val at = line.indexOf('\t')
                if (at > 0 && at < line.length - 1) read[line.substring(0, at)] = line.substring(at + 1)
            }
        read.also { names = it }
    }
}
