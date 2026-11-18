package com.seca.core.suite

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

    private companion object {
        /** Kept in step with `localeFilters` in the application convention plugin. */
        val LANGUAGES = listOf("fr", "es", "pl", "de", "tr")
        val KEY = Regex("""<(?:string|plurals) name="([^"]+)"""")
    }
}
