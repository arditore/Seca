package com.seca.core.model

import android.os.LocaleList
import java.util.Locale

/**
 * The languages Seca ships, as `localeFilters` in the build lists them.
 *
 * A test holds this to that list, because the two falling out of step is not hypothetical: this
 * named three of them while the apps shipped six, so a German phone was given its screens in
 * German and its dates in English (reported with a screenshot of a call list reading
 * "Sunday, November 15" on a German handset).
 */
val ShippedLanguages = listOf("en", "fr", "es", "pl", "de", "tr")

/**
 * The locale dates, times and numbers are written in, matching the language the screens
 * resolve to.
 *
 * The owner's first locale in a language Seca ships keeps its own regional formats, and a phone
 * in a language Seca does not ship gets English, like the screens: a Japanese phone reading
 * English words beside Japanese dates is worse than English throughout.
 */
fun uiLocale(): Locale {
    // Unit tests run without Android's locale list: fall back to the JVM's default.
    val preferred: LocaleList? = LocaleList.getDefault()
    val candidates = if (preferred != null) (0 until preferred.size()).map(preferred::get) else listOf(Locale.getDefault())
    return candidates.firstOrNull { it.language in ShippedLanguages } ?: Locale.ENGLISH
}
