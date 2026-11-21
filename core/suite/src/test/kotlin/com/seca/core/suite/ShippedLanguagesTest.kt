package com.seca.core.suite

import com.seca.core.model.ShippedLanguages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every language the apps ship has every string, in every module.
 *
 * The README has promised this from the start — "nothing ships half translated: a language is
 * listed in the build only once its strings are complete, in the apps and in every module they
 * share" — and nothing checked it. A promise with nothing behind it is only true until somebody
 * adds a string, which happens every week, and a missing one does not fail anything: Android
 * falls back to English and the owner gets one sentence in the wrong language with no warning.
 *
 * It went wrong the first time it was tested. A Turkish translation arrived complete against
 * the `main` it was written on, and by the time it merged, `main` had ten new strings it had
 * never seen.
 *
 * This reads the resource files rather than the compiled resources, because that is where the
 * gap is and where whoever has to fill it will be looking.
 */
class ShippedLanguagesTest {

    @Test
    fun `no shipped language is missing a string`() {
        val missing = mutableListOf<String>()
        for (module in modules()) {
            val english = keysOf(File(module, "src/main/res/values/strings.xml")) ?: continue
            for (language in LANGUAGES) {
                val file = File(module, "src/main/res/values-$language/strings.xml")
                val keys = keysOf(file)
                if (keys == null) {
                    missing += "${module.name}: no values-$language at all"
                    continue
                }
                (english - keys).sorted().forEach { missing += "${module.name} [$language] $it" }
            }
        }
        assertTrue(
            "A language listed in the build is missing strings. Either translate these or take " +
                "the language out of localeFilters; shipping it half done is the one thing the " +
                "README says does not happen.\n" + missing.joinToString("\n"),
            missing.isEmpty(),
        )
    }

    /** No language carries a string the English file has dropped, which is dead weight. */
    @Test
    fun `no language keeps a string English no longer has`() {
        val extra = mutableListOf<String>()
        for (module in modules()) {
            val english = keysOf(File(module, "src/main/res/values/strings.xml")) ?: continue
            for (language in LANGUAGES) {
                val keys = keysOf(File(module, "src/main/res/values-$language/strings.xml")) ?: continue
                (keys - english).sorted().forEach { extra += "${module.name} [$language] $it" }
            }
        }
        assertTrue("Strings left behind after the English one was removed:\n" + extra.joinToString("\n"), extra.isEmpty())
    }

    /**
     * An apostrophe inside a string has to be escaped, and the error when it is not is useless.
     *
     * Android's resource compiler answers "Invalid unicode escape sequence in string" for a
     * bare `'`, which sends whoever hit it looking for a backslash-u that is not there. It has
     * now cost two people an evening: a French string of mine and a Turkish one in a pull
     * request, both of them "Seca'nın"-shaped.
     *
     * Said here, by name, before the resource compiler gets to be mysterious about it.
     */
    @Test
    fun `no string has a bare apostrophe in it`() {
        val bare = mutableListOf<String>()
        for (module in modules()) {
            for (dir in File(module, "src/main/res").listFiles().orEmpty()) {
                if (!dir.name.startsWith("values")) continue
                val file = File(dir, "strings.xml")
                if (!file.isFile) continue
                VALUE.findAll(file.readText()).forEach { match ->
                    val (name, body) = match.destructured
                    if (UNESCAPED.containsMatchIn(body)) bare += "${module.name}/${dir.name} $name"
                }
            }
        }
        assertTrue(
            "An apostrophe in an Android string resource has to be escaped or the whole file " +
                "fails to compile, and the error names unicode rather than the quote:\n" +
                bare.joinToString("\n"),
            bare.isEmpty(),
        )
    }

    private fun keysOf(file: File): Set<String>? {
        if (!file.isFile) return null
        return KEY.findAll(file.readText()).map { it.groupValues[1] }.toSet()
    }

    /**
     * Every module with strings, found from this one rather than listed, so a new module is
     * covered without anybody remembering to add it here.
     */
    private fun modules(): List<File> {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        return listOf("apps", "core").flatMap { group ->
            File(root, group).listFiles().orEmpty().filter { File(it, "src/main/res/values/strings.xml").isFile }
        }.sortedBy { it.name }
    }

    /**
     * The build decides what ships, and two lists in the code claim to know it: this test's own,
     * and [ShippedLanguages], which picks the locale dates and numbers are written in. The second
     * one was three languages behind, so phones in the other three were given English dates under
     * their own screens, for months, in silence. Neither list is trusted here: both are held
     * against `localeFilters`.
     */
    @Test
    fun `the lists of languages agree with the build`() {
        val filters = localeFilters()
        assertEquals(
            "This test's own list of languages is not what the build ships",
            filters, (LANGUAGES + "en").sorted(),
        )
        assertEquals(
            "ShippedLanguages decides which phones get their dates in their own language, and it " +
                "is not what the build ships",
            filters, ShippedLanguages.sorted(),
        )
    }

    /** What `localeFilters` names in the application convention plugin, sorted. */
    private fun localeFilters(): List<String> {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        val plugin = File(root, "build-logic/src/main/kotlin/seca.android.application.gradle.kts")
        val line = plugin.readLines().first { it.contains("localeFilters") }
        return FILTER.findAll(line).map { it.groupValues[1] }.toList().sorted()
    }

    private companion object {
        /** Held to the build's own list by `the lists of languages agree with the build`. */
        val LANGUAGES = listOf("fr", "es", "pl", "de", "tr", "zh", "fa")
        val FILTER = Regex(""""([a-z]{2})"""")
        val KEY = Regex("""<(?:string|plurals) name="([^"]+)"""")
        val VALUE = Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)

        /** An apostrophe with no backslash in front of it. */
        val UNESCAPED = Regex("(?<!\\\\)'")
    }
}
