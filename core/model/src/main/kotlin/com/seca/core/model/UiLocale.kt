package com.seca.core.model

import android.os.LocaleList
import java.util.Locale

/**
 * The locale dates and times are written in, matching the language the screens resolve to.
 * The apps speak French and English: the owner's first French or English locale keeps its own
 * regional formats, and a phone in any other language gets English, like the screens.
 */
fun uiLocale(): Locale {
    // Unit tests run without Android's locale list: fall back to the JVM's default.
    val preferred: LocaleList? = LocaleList.getDefault()
    val candidates = if (preferred != null) (0 until preferred.size()).map(preferred::get) else listOf(Locale.getDefault())
    return candidates.firstOrNull { it.language == "fr" || it.language == "en" } ?: Locale.ENGLISH
}
