package com.seca.core.suite

import android.app.LocaleConfig
import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.seca.core.design.SecaIcons
import com.seca.core.design.component.SecaGroupItem
import com.seca.core.design.component.SecaSectionLabel
import com.seca.core.design.component.SecaSettingRow
import java.util.Locale

/**
 * In each Seca app's settings: which language the app itself is in.
 *
 * Android has had this since 13, in Settings, Apps, the app, Language, and that is where it
 * belongs. The trouble is that several phone makers leave that line out of their own app
 * info page, so a German speaker with an English phone had no way of reaching the German
 * Seca ships: a translator wrote in with screenshots of a Xiaomi where the line is simply
 * not there.
 *
 * So the same choice is offered here. It is not a second setting with its own memory: it
 * writes the system's own per-app locale, the one that other page writes, so the two always
 * agree and nothing is stored by Seca at all.
 *
 * The list comes from [LocaleConfig], which reads the languages the build actually shipped.
 * There is no hand-written list here to fall out of step with the one in the build file.
 *
 * Shown only on Android 13 and later, because before that the system has nowhere to keep a
 * per-app language and the phone's own language is the only one there is.
 */
@Composable
fun LanguageSection() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val offered = remember(context) { languagesOffered(context) }
    if (offered.size < 2) return

    val chosen = remember(context) { chosenLanguage(context) }
    var open by remember { mutableStateOf(false) }
    val systemDefault = stringResource(R.string.language_system)

    SecaSectionLabel(stringResource(R.string.language))
    SecaGroupItem(index = 0, count = 1, onClick = { open = true }) {
        Box {
            SecaSettingRow(
                icon = SecaIcons.Language,
                title = chosen?.let(::nameOf) ?: systemDefault,
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(
                    text = { Text(systemDefault) },
                    trailingIcon = if (chosen == null) {
                        { Icon(SecaIcons.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        open = false
                        setLanguage(context, null)
                    },
                )
                offered.forEach { locale ->
                    DropdownMenuItem(
                        text = { Text(nameOf(locale)) },
                        trailingIcon = if (locale.language == chosen?.language) {
                            { Icon(SecaIcons.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        onClick = {
                            open = false
                            setLanguage(context, locale)
                        },
                    )
                }
            }
        }
    }
}

/** Each language in its own words, as a list of languages is read: Deutsch, not German. */
private fun nameOf(locale: Locale): String =
    locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }

/**
 * The languages this build shipped, one per language and in their own alphabetical order.
 *
 * The generated list names the default twice, once as the fallback and once in the list
 * itself, and carries regions this app does not translate separately.
 */
private fun languagesOffered(context: Context): List<Locale> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return emptyList()
    val supported = runCatching { LocaleConfig(context).supportedLocales }.getOrNull() ?: return emptyList()
    val byLanguage = LinkedHashMap<String, Locale>()
    for (index in 0 until supported.size()) {
        val locale = supported[index] ?: continue
        if (locale.language.isNotEmpty()) byLanguage.putIfAbsent(locale.language, Locale.forLanguageTag(locale.language))
    }
    return byLanguage.values.sortedBy { nameOf(it).lowercase(it) }
}

/** The language the owner chose for this app, or null while it follows the phone. */
private fun chosenLanguage(context: Context): Locale? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    val set = context.getSystemService(LocaleManager::class.java)?.applicationLocales ?: return null
    return if (set.isEmpty) null else set[0]
}

/** Hands the choice to the system, which restarts the app in it. Null means follow the phone. */
private fun setLanguage(context: Context, locale: Locale?) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val manager = context.getSystemService(LocaleManager::class.java) ?: return
    manager.applicationLocales = if (locale == null) {
        LocaleList.getEmptyLocaleList()
    } else {
        LocaleList.forLanguageTags(locale.toLanguageTag())
    }
}
