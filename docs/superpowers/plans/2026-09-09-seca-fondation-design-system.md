# Seca — Foundation and Design System — Implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Set up the Gradle monorepo, the `:core:design` module in Material 3 Expressive, and an installable catalog app that shows the whole visual language before writing the first real app.

**Architecture:** Multi-module Gradle monorepo. `build-logic` carries convention plugins so the three future apps share exactly the same configuration. `:core:model` holds the pure domain types (no Android dependency). `:core:design` holds **all** the visuals — colors, typography, shapes, motion, components — and is the only module allowed to opt into the experimental M3 Expressive APIs. `apps/catalog` is a debug app that shows the design system; it is disposable and will never be published.

**Tech Stack:** Kotlin 2.4.20, Jetpack Compose 1.12.0, Material 3 `1.5.0-alpha27` (APIs Expressive), AGP 9.4.0, Gradle 9.7.1, JDK 17, Robolectric 4.16.1.

**Spec:** `docs/superpowers/specs/2026-09-09-seca-suite-design.md` — *translated from French on 2026-09-15; UI strings inside code samples are left as they were at the time.*

## Global Constraints

These constraints apply to **every** task, implicitly.

- **Exactly JDK 17.** AGP 9.4 lists it as the minimum *and* the default. The JDK 25 present on the machine does not fit.
- **Gradle 9.7.1** through the wrapper only. Never a system install.
- `compileSdk = 37`, `buildToolsVersion = "36.0.0"`, `minSdk = 34`, `targetSdk = 36`. **`compileSdk 37` is mandatory, not a preference**: `material3:1.5.0-alpha27` *and* Compose `ui`/`foundation` 1.12.0 all declare `minCompileSdk=37` in their AAR metadata. Nothing in this plan compiles at 36. `targetSdk` stays at 36: it is the level tested against, and it is independent of `compileSdk`.
- **No Compose BOM.** BOM `2026.08.00` pins `material3` to `1.4.0`, which lacks the Expressive APIs. Every version is pinned explicitly in `gradle/libs.versions.toml`.
- **Zero proprietary dependency.** No Play Services, no Firebase, no analytics, no crash reporting. An F-Droid constraint.
- **`@OptIn(ExperimentalMaterial3ExpressiveApi::class)` only in `:core:design`.** No app module opts in.
- **No app module defines a color, shape or typography.** Everything comes from `:core:design`.
- Reproducible builds: pinned versions, no version ranges, no `latest.release`.
- Code, comments and identifiers are in English. The user interface and the documentation were in French at the time; the project has since moved to English, with the interface translated into French.

---

### Task 1: Toolchain

No TDD here — this is environment setup. The task is done when the verification commands pass.

**Files:**
- No file of the repository is modified.

**Interfaces:**
- Consumes: nothing.
- Produces: `java` at 17, `sdkmanager` and `adb` available; `ANDROID_HOME` set.

- [ ] **Step 1: Install JDK 17**

Temurin 17 installs next to JDK 25 without replacing it.

```powershell
winget install --id EclipseAdoptium.Temurin.17.JDK -e
```

- [ ] **Step 2: Check that JDK 17 is present**

```powershell
Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory | Select-Object Name
```

Expected: a `jdk-17.*-hotspot` folder appears. Note its exact path — the next step uses it.

- [ ] **Step 3: Install the Android SDK command-line tools**

```powershell
winget install --id Google.AndroidStudio -e
```

Android Studio bundles the SDK and `sdkmanager`. If the IDE is not wanted, download only `commandlinetools-win` from https://developer.android.com/studio#command-line-tools-only and unzip it into `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest`.

- [ ] **Step 4: Set ANDROID_HOME**

```powershell
[Environment]::SetEnvironmentVariable("ANDROID_HOME", "$env:LOCALAPPDATA\Android\Sdk", "User")
```

- [ ] **Step 5: Install the required SDK packages**

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat" --install "platform-tools" "platforms;android-37.0" "build-tools;36.0.0"
```

- [ ] **Step 6: Check the whole toolchain**

The JDK patch number depends on what winget installed — resolve it rather than assume it.

```powershell
$jdk17 = (Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -Filter "jdk-17*").FullName
& "$jdk17\bin\java.exe" -version
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" version
```

Expected: `openjdk version "17.*"` and an adb version.

- [ ] **Step 7: Check the connection to the Pixel 9 (optional but recommended now)**

Turn on USB debugging on the Pixel 9 (Settings → About → tap the build number 7 times, then Developer options → USB debugging), plug in the cable, accept the prompt on the phone.

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices -l
```

Expected: the device appears in the `device` state. **If it does not appear**, the visual check of task 8 will be impossible — fix it now, not later.

- [ ] **Step 8: Read the device's real API level**

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell getprop ro.build.version.sdk
```

Note the value. **Read on 2026-09-09 on the Pixel 9: `37` (Android 17).** The platform to install is `platforms;android-37.0` — `platforms;android-37` does not exist under that name, the SDK having moved to minor versions. `android-37.0` is released, not a preview (`PreviewSdkInt=0`, empty `BetaVersion`).

---

### Task 2: Gradle monorepo skeleton

**Files:**
- Create: `settings.gradle.kts`
- Create: `gradle/libs.versions.toml`
- Create: `gradle.properties`
- Create: `.gitignore`
- Create: `.gitattributes`
- Create: `build-logic/settings.gradle.kts`
- Create: `build-logic/build.gradle.kts`
- Create: `build-logic/src/main/kotlin/seca.android.library.gradle.kts`
- Create: `build-logic/src/main/kotlin/seca.android.application.gradle.kts`
- Create: `build-logic/src/main/kotlin/seca.compose.gradle.kts`
- Delete: the empty folders `Seca Contacts/`, `Seca Messages/`, `Seca Phone/`

**Interfaces:**
- Consumes: the toolchain from task 1.
- Produces: the `seca.android.library`, `seca.android.application` and `seca.compose` plugins; the `libs` version catalog.

- [ ] **Step 1: Delete the empty folders with spaces in their names**

Spaces in paths make Gradle and F-Droid build recipes fragile. These folders are empty, nothing is lost.

```powershell
Remove-Item "Seca Contacts","Seca Messages","Seca Phone" -Recurse -Force
```

- [ ] **Step 2: Create the Gradle 9.7.1 wrapper**

With no Gradle installed, generate the wrapper from the distribution, downloaded once:

```powershell
Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-9.7.1-bin.zip" -OutFile "$env:TEMP\gradle-9.7.1-bin.zip"
Expand-Archive "$env:TEMP\gradle-9.7.1-bin.zip" -DestinationPath "$env:TEMP\gradle-dist" -Force
& "$env:TEMP\gradle-dist\gradle-9.7.1\bin\gradle.bat" wrapper --gradle-version 9.7.1 --distribution-type bin
```

- [ ] **Step 3: Write `.gitattributes`**

Git already reported an LF→CRLF conversion. Normalise before it pollutes the diffs.

```
* text=auto eol=lf
*.bat text eol=crlf
*.jar binary
gradlew text eol=lf
```

- [ ] **Step 4: Write `.gitignore`**

```
.gradle/
build/
local.properties
*.iml
.idea/
.kotlin/
captures/
*.apk
*.aab
.superpowers/
```

- [ ] **Step 5: Write `gradle/libs.versions.toml`**

Every version is real, read from Google Maven and Maven Central on 2026-09-09.

```toml
[versions]
agp = "9.4.0"
kotlin = "2.4.20"
compose = "1.12.0"
material3 = "1.5.0-alpha27"
activityCompose = "1.13.0"
navigationCompose = "2.10.0"
lifecycle = "2.11.0"
coroutines = "1.11.0"
robolectric = "4.16.1"
junit = "4.13.2"
androidxTestCore = "1.7.0"
androidxTestExtJunit = "1.3.0"

[libraries]
compose-ui = { module = "androidx.compose.ui:ui", version.ref = "compose" }
compose-ui-graphics = { module = "androidx.compose.ui:ui-graphics", version.ref = "compose" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling", version.ref = "compose" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview", version.ref = "compose" }
compose-foundation = { module = "androidx.compose.foundation:foundation", version.ref = "compose" }
compose-material3 = { module = "androidx.compose.material3:material3", version.ref = "material3" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigationCompose" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
junit = { module = "junit:junit", version.ref = "junit" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
androidx-test-core = { module = "androidx.test:core", version.ref = "androidxTestCore" }
androidx-test-ext-junit = { module = "androidx.test.ext:junit", version.ref = "androidxTestExtJunit" }
compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4", version.ref = "compose" }
compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest", version.ref = "compose" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

- [ ] **Step 6: Write `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx4g -XX:+UseParallelGC
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
android.useAndroidX=true
android.nonTransitiveRClass=true
```

- [ ] **Step 7: Write `build-logic/settings.gradle.kts`**

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
rootProject.name = "build-logic"
```

- [ ] **Step 8: Write `build-logic/build.gradle.kts`**

```kotlin
plugins {
    `kotlin-dsl`
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}

dependencies {
    compileOnly("com.android.tools.build:gradle:${libs.versions.agp.get()}")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    compileOnly("org.jetbrains.kotlin:compose-compiler-gradle-plugin:${libs.versions.kotlin.get()}")
}
```

The coordinates are written explicitly rather than derived from the plugin aliases: it reads better and avoids a fragile helper in a build script.

- [ ] **Step 9: Write the `seca.android.library` convention plugin**

`build-logic/src/main/kotlin/seca.android.library.gradle.kts`:

```kotlin
import com.android.build.api.dsl.LibraryExtension

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

extensions.configure<LibraryExtension> {
    compileSdk = 37
    buildToolsVersion = "36.0.0"
    defaultConfig {
        minSdk = 34
        testOptions.targetSdk = 36
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    jvmToolchain(17)
}
```

`unitTests.isIncludeAndroidResources = true` is essential: it is what lets Compose tests run under Robolectric, so **without a device**.

- [ ] **Step 10: Write the `seca.android.application` convention plugin**

`build-logic/src/main/kotlin/seca.android.application.gradle.kts`:

```kotlin
import com.android.build.api.dsl.ApplicationExtension

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

extensions.configure<ApplicationExtension> {
    compileSdk = 37
    buildToolsVersion = "36.0.0"
    defaultConfig {
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

kotlin {
    jvmToolchain(17)
}
```

`dependenciesInfo.includeInApk = false` removes the Google-signed metadata blob from the APK — required for reproducible builds that F-Droid can verify.

- [ ] **Step 11: Write the `seca.compose` convention plugin**

`build-logic/src/main/kotlin/seca.compose.gradle.kts`:

```kotlin
plugins {
    id("org.jetbrains.kotlin.plugin.compose")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    add("implementation", libs.findLibrary("compose-ui").get())
    add("implementation", libs.findLibrary("compose-ui-graphics").get())
    add("implementation", libs.findLibrary("compose-foundation").get())
    add("implementation", libs.findLibrary("compose-ui-tooling-preview").get())
    add("debugImplementation", libs.findLibrary("compose-ui-tooling").get())
    add("testImplementation", libs.findLibrary("robolectric").get())
    add("testImplementation", libs.findLibrary("junit").get())
    add("testImplementation", libs.findLibrary("androidx-test-core").get())
    add("testImplementation", libs.findLibrary("androidx-test-ext-junit").get())
    add("testImplementation", libs.findLibrary("compose-ui-test-junit4").get())
    add("debugImplementation", libs.findLibrary("compose-ui-test-manifest").get())
}
```

This plugin deliberately does **not** turn on `buildFeatures.compose`. Doing so from a precompiled script plugin would mean naming `CommonExtension` with its list of generic parameters, which changes from one AGP version to the next and breaks silently. Each Compose module turns it on itself in one line — explicit and stable. `:core:model` does not need it.

`libs` is obtained through `VersionCatalogsExtension`: the generated `libs` accessor does not exist in a precompiled script plugin.

- [ ] **Step 12: Write `settings.gradle.kts`**

```kotlin
pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Seca"

include(":core:model")
include(":core:design")
include(":apps:catalog")
```

- [ ] **Step 13: Create `local.properties` with the SDK path**

```powershell
"sdk.dir=$($env:LOCALAPPDATA -replace '\\','\\')\\Android\\Sdk" | Out-File -Encoding utf8 local.properties
```

This file is ignored by git — it belongs to the machine.

- [ ] **Step 14: Create the minimal build files of the three modules**

`core/model/build.gradle.kts`:

```kotlin
plugins { id("seca.android.library") }
android { namespace = "com.seca.core.model" }
dependencies {
    testImplementation(libs.junit)
}
```

`core/design/build.gradle.kts`:

```kotlin
plugins {
    id("seca.android.library")
    id("seca.compose")
}
android {
    namespace = "com.seca.core.design"
    buildFeatures { compose = true }
}
dependencies {
    api(libs.compose.material3)
}
```

`apps/catalog/build.gradle.kts`:

```kotlin
plugins {
    id("seca.android.application")
    id("seca.compose")
}
android {
    namespace = "com.seca.catalog"
    defaultConfig { applicationId = "com.seca.catalog" }
    buildFeatures { compose = true }
}
dependencies {
    implementation(project(":core:design"))
    implementation(libs.androidx.activity.compose)
}
```

`material3` is declared as `api` in `:core:design`: consuming modules get it without declaring it again, which guarantees that a single version circulates.

- [ ] **Step 15: Check that the Gradle configuration is valid**

```powershell
.\gradlew.bat projects --no-daemon
```

Expected: the tree lists `:core:model`, `:core:design`, `:apps:catalog` without error. It is the first real signal that the toolchain, the convention plugins and the catalog work together.

- [ ] **Step 16: Commit**

```bash
git add -A
git commit -m "build: Gradle monorepo, version catalog and convention plugins"
```

---

### Task 3: `:core:model` — domain types

A pure Kotlin module, with no Android dependency. It is the vocabulary shared by the three future apps.

**Files:**
- Create: `core/model/src/main/kotlin/com/seca/core/model/PhoneNumber.kt`
- Create: `core/model/src/main/kotlin/com/seca/core/model/SecaContact.kt`
- Test: `core/model/src/test/kotlin/com/seca/core/model/PhoneNumberTest.kt`
- Test: `core/model/src/test/kotlin/com/seca/core/model/SecaContactTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `PhoneNumber(raw: String)` with `val digits: String` and `fun matches(other: PhoneNumber): Boolean`
  - `SecaContact(id: Long, displayName: String, phoneNumbers: List<PhoneNumber>, isFavorite: Boolean, photoUri: String?)` with `val initials: String`

- [ ] **Step 1: Write the failing test for `PhoneNumber`**

`core/model/src/test/kotlin/com/seca/core/model/PhoneNumberTest.kt`:

```kotlin
package com.seca.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberTest {

    @Test
    fun `digits strips formatting characters`() {
        assertEquals("0612345678", PhoneNumber("06 12 34 56 78").digits)
        assertEquals("+33612345678", PhoneNumber("+33 6-12.34.56.78").digits)
    }

    @Test
    fun `digits keeps a parenthesised trunk prefix`() {
        // "(0)" is a written convention, not a dialable digit, but stripping it
        // would need country-specific rules. Keeping it is safe because matches()
        // compares only the trailing significant digits.
        assertEquals("+330612345678", PhoneNumber("+33 (0)6-12.34.56.78").digits)
    }

    @Test
    fun `matches sees through a parenthesised trunk prefix`() {
        assertTrue(
            PhoneNumber("+33 (0)6-12.34.56.78").matches(PhoneNumber("06 12 34 56 78"))
        )
    }

    @Test
    fun `matches compares the last nine significant digits`() {
        assertTrue(PhoneNumber("+33612345678").matches(PhoneNumber("06 12 34 56 78")))
    }

    @Test
    fun `matches rejects different numbers`() {
        assertFalse(PhoneNumber("+33612345678").matches(PhoneNumber("0687654321")))
    }

    @Test
    fun `matches rejects numbers too short to compare safely`() {
        assertFalse(PhoneNumber("1234").matches(PhoneNumber("5678")))
    }
}
```

Comparing the last nine digits is the usual way to match an international form with a national form without shipping a numbering library. The threshold guards against false positives on short numbers.

- [ ] **Step 2: Run the test and check that it fails**

```powershell
.\gradlew.bat :core:model:testDebugUnitTest --tests "*PhoneNumberTest*"
```

Expected: compilation failure, `PhoneNumber` does not exist.

- [ ] **Step 3: Implement `PhoneNumber`**

```kotlin
package com.seca.core.model

/** A phone number in whatever shape the user or the system provided it. */
@JvmInline
value class PhoneNumber(val raw: String) {

    /** The number stripped of formatting, keeping a leading `+` if present. */
    val digits: String
        get() = buildString {
            raw.forEachIndexed { index, c ->
                when {
                    c.isDigit() -> append(c)
                    c == '+' && index == 0 -> append(c)
                }
            }
        }

    /**
     * Whether two numbers plausibly designate the same line.
     *
     * Compares the last [SIGNIFICANT_DIGITS] digits so that an international
     * form matches its national form. Numbers shorter than that are only
     * considered equal when identical, to avoid matching short codes.
     */
    fun matches(other: PhoneNumber): Boolean {
        val a = digits.filter { it.isDigit() }
        val b = other.digits.filter { it.isDigit() }
        if (a.isEmpty() || b.isEmpty()) return false
        if (a.length < SIGNIFICANT_DIGITS || b.length < SIGNIFICANT_DIGITS) return a == b
        return a.takeLast(SIGNIFICANT_DIGITS) == b.takeLast(SIGNIFICANT_DIGITS)
    }

    private companion object {
        const val SIGNIFICANT_DIGITS = 9
    }
}
```

- [ ] **Step 4: Run the test and check that it passes**

```powershell
.\gradlew.bat :core:model:testDebugUnitTest --tests "*PhoneNumberTest*"
```

Expected: PASS, 6 tests.

- [ ] **Step 5: Write the failing test for `SecaContact`**

`core/model/src/test/kotlin/com/seca/core/model/SecaContactTest.kt`:

```kotlin
package com.seca.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SecaContactTest {

    private fun contact(name: String) = SecaContact(
        id = 1L,
        displayName = name,
        phoneNumbers = emptyList(),
        isFavorite = false,
        photoUri = null,
    )

    @Test
    fun `initials use the first letter of the first two words`() {
        assertEquals("CD", contact("Camille Durand").initials)
    }

    @Test
    fun `initials fall back to a single letter for one word`() {
        assertEquals("C", contact("Camille").initials)
    }

    @Test
    fun `initials are empty for a blank name`() {
        assertEquals("", contact("   ").initials)
    }

    @Test
    fun `initials skip non letter leading characters`() {
        assertEquals("A", contact("+ Alice").initials)
    }
}
```

- [ ] **Step 6: Run the test and check that it fails**

```powershell
.\gradlew.bat :core:model:testDebugUnitTest --tests "*SecaContactTest*"
```

Expected: compilation failure, `SecaContact` does not exist.

- [ ] **Step 7: Implement `SecaContact`**

```kotlin
package com.seca.core.model

/** A contact as Seca understands it, independent of any storage concern. */
data class SecaContact(
    val id: Long,
    val displayName: String,
    val phoneNumbers: List<PhoneNumber>,
    val isFavorite: Boolean,
    val photoUri: String?,
) {
    /** Up to two letters used when no photo is available. */
    val initials: String
        get() = displayName
            .split(' ', '\t', '\n')
            .mapNotNull { word -> word.firstOrNull { it.isLetter() } }
            .take(2)
            .joinToString("")
            .uppercase()
}
```

- [ ] **Step 8: Run all the module's tests**

```powershell
.\gradlew.bat :core:model:test
```

Expected: PASS, 10 tests.

- [ ] **Step 9: Commit**

```bash
git add core/model
git commit -m "feat(model): PhoneNumber and SecaContact domain types"
```

---

### Task 4: `:core:design` — the Seca theme

Typography, shapes, motion and colors form a single theme. They ship in the same task because `SecaTheme` only compiles once all four exist, and because a reviewer could not approve the typography while rejecting the palette: it is one design decision.

The palettes are written by hand rather than derived from a seed by a library: it is deterministic, avoids a dependency, and gives real control over the result.

**Files:**
- Create: `core/design/src/main/kotlin/com/seca/core/design/SecaTypography.kt`
- Create: `core/design/src/main/kotlin/com/seca/core/design/SecaShapes.kt`
- Create: `core/design/src/main/kotlin/com/seca/core/design/SecaMotion.kt`
- Create: `core/design/src/main/kotlin/com/seca/core/design/SecaAppIdentity.kt`
- Create: `core/design/src/main/kotlin/com/seca/core/design/color/SecaPalettes.kt`
- Create: `core/design/src/main/kotlin/com/seca/core/design/SecaTheme.kt`
- Test: `core/design/src/test/kotlin/com/seca/core/design/SecaThemeTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `enum class SecaAppIdentity { Contacts, Phone, Messages }`
  - `val SecaTypography: Typography` and `val SecaShapes: Shapes`
  - `object SecaMotion` with `emphasized()`, `standard()` and `expressiveSpring()`
  - `@Composable fun SecaTheme(identity: SecaAppIdentity, darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = false, content: @Composable () -> Unit)`

- [ ] **Step 1: Write the typography**

`core/design/src/main/kotlin/com/seca/core/design/SecaTypography.kt`:

```kotlin
package com.seca.core.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Seca's type scale.
 *
 * Uses the platform font so the apps sit naturally on GrapheneOS and no font
 * binary ships in the APK. Display and headline weights are heavier than the
 * Material default to give screens a clearer hierarchy.
 */
private val Platform = FontFamily.Default

val SecaTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = Platform,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Platform,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.25).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Platform,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Platform,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Platform,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Platform,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Platform,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
)
```

- [ ] **Step 2: Write the shapes**

`core/design/src/main/kotlin/com/seca/core/design/SecaShapes.kt`:

```kotlin
package com.seca.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Seca's shape scale.
 *
 * Corners are noticeably rounder than the Material default — the single
 * cheapest lever on how expressive the surface reads.
 */
val SecaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)
```

- [ ] **Step 3: Write the motion specs**

`core/design/src/main/kotlin/com/seca/core/design/SecaMotion.kt`:

```kotlin
package com.seca.core.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Shared motion specs.
 *
 * Every Seca animation comes from here so the three apps move alike.
 * [expressiveSpring] carries the slight overshoot that gives M3 Expressive
 * its character; use it for anything the user directly manipulates.
 */
object SecaMotion {

    // M3 uses distinct curves: standard is symmetric-ish, emphasized decelerates late.
    private val EmphasizedEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    private val StandardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun <T> emphasized() = tween<T>(durationMillis = 500, easing = EmphasizedEasing)

    fun <T> standard() = tween<T>(durationMillis = 300, easing = StandardEasing)

    fun <T> expressiveSpring() = spring<T>(
        dampingRatio = 0.75f,
        stiffness = Spring.StiffnessMediumLow,
    )
}
```

- [ ] **Step 4: Write `SecaAppIdentity`**

`core/design/src/main/kotlin/com/seca/core/design/SecaAppIdentity.kt`:

```kotlin
package com.seca.core.design

/**
 * Which Seca app is being themed.
 *
 * All three share tokens, shapes, typography and motion; only the accent
 * differs, so the family reads as one system while each app stays
 * recognisable at a glance.
 */
enum class SecaAppIdentity {
    Contacts,
    Phone,
    Messages,
}
```

- [ ] **Step 5: Write the palettes**

`core/design/src/main/kotlin/com/seca/core/design/color/SecaPalettes.kt`. Three distinct accents on a shared neutral base — teal for Contacts, indigo for Phone, violet for Messages.

```kotlin
package com.seca.core.design.color

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.seca.core.design.SecaAppIdentity

/**
 * One app's accent, in both themes.
 *
 * Container roles are set explicitly: `lightColorScheme`/`darkColorScheme` fill
 * anything left out from Material's baseline purple, which would render every
 * identity's containers identically and defeat the per-app accent.
 */
private data class Accent(
    val primary: Color,
    val onPrimary: Color,
    val container: Color,
    val onContainer: Color,
)

private val ContactsLight = Accent(Color(0xFF00696E), Color.White, Color(0xFF9CF1F6), Color(0xFF002022))
private val ContactsDark = Accent(Color(0xFF4FD8E0), Color(0xFF00363A), Color(0xFF004F53), Color(0xFF9CF1F6))

private val PhoneLight = Accent(Color(0xFF3A5BA9), Color.White, Color(0xFFDAE2FF), Color(0xFF001A43))
private val PhoneDark = Accent(Color(0xFFB1C5FF), Color(0xFF002B75), Color(0xFF1F438F), Color(0xFFDAE2FF))

private val MessagesLight = Accent(Color(0xFF6B4EA8), Color.White, Color(0xFFEADDFF), Color(0xFF250057))
private val MessagesDark = Accent(Color(0xFFD3BCFF), Color(0xFF3A1D6E), Color(0xFF53378E), Color(0xFFEADDFF))

// Shared neutral base — identical across the three apps, so the family reads as one system.
private val SurfaceLight = Color(0xFFF7FAFA)
private val OnSurfaceLight = Color(0xFF191C1D)
private val SurfaceVariantLight = Color(0xFFDBE4E5)
private val OnSurfaceVariantLight = Color(0xFF3F4849)
private val OutlineLight = Color(0xFF6F7979)

private val SurfaceDark = Color(0xFF0E1414)
private val OnSurfaceDark = Color(0xFFE1E3E3)
private val SurfaceVariantDark = Color(0xFF3F4849)
private val OnSurfaceVariantDark = Color(0xFFBFC8C9)
private val OutlineDark = Color(0xFF899393)

private fun lightAccent(identity: SecaAppIdentity) = when (identity) {
    SecaAppIdentity.Contacts -> ContactsLight
    SecaAppIdentity.Phone -> PhoneLight
    SecaAppIdentity.Messages -> MessagesLight
}

private fun darkAccent(identity: SecaAppIdentity) = when (identity) {
    SecaAppIdentity.Contacts -> ContactsDark
    SecaAppIdentity.Phone -> PhoneDark
    SecaAppIdentity.Messages -> MessagesDark
}

internal fun lightSchemeFor(identity: SecaAppIdentity): ColorScheme {
    val a = lightAccent(identity)
    return lightColorScheme(
        primary = a.primary,
        onPrimary = a.onPrimary,
        primaryContainer = a.container,
        onPrimaryContainer = a.onContainer,
        secondary = a.primary,
        onSecondary = a.onPrimary,
        secondaryContainer = a.container,
        onSecondaryContainer = a.onContainer,
        tertiary = a.primary,
        onTertiary = a.onPrimary,
        tertiaryContainer = a.container,
        onTertiaryContainer = a.onContainer,
        surface = SurfaceLight,
        onSurface = OnSurfaceLight,
        surfaceVariant = SurfaceVariantLight,
        onSurfaceVariant = OnSurfaceVariantLight,
        outline = OutlineLight,
        background = SurfaceLight,
        onBackground = OnSurfaceLight,
    )
}

internal fun darkSchemeFor(identity: SecaAppIdentity): ColorScheme {
    val a = darkAccent(identity)
    return darkColorScheme(
        primary = a.primary,
        onPrimary = a.onPrimary,
        primaryContainer = a.container,
        onPrimaryContainer = a.onContainer,
        secondary = a.primary,
        onSecondary = a.onPrimary,
        secondaryContainer = a.container,
        onSecondaryContainer = a.onContainer,
        tertiary = a.primary,
        onTertiary = a.onPrimary,
        tertiaryContainer = a.container,
        onTertiaryContainer = a.onContainer,
        surface = SurfaceDark,
        onSurface = OnSurfaceDark,
        surfaceVariant = SurfaceVariantDark,
        onSurfaceVariant = OnSurfaceVariantDark,
        outline = OutlineDark,
        background = SurfaceDark,
        onBackground = OnSurfaceDark,
    )
}
```

- [ ] **Step 6: Write the failing test for the theme**

`core/design/src/test/kotlin/com/seca/core/design/SecaThemeTest.kt`:

```kotlin
package com.seca.core.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SecaThemeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `every app identity gets a distinct accent`() {
        // Captured in a single setContent: ComposeContentTestRule allows
        // setContent only once per test.
        val primaries = mutableMapOf<SecaAppIdentity, Color>()
        composeRule.setContent {
            SecaAppIdentity.entries.forEach { identity ->
                SecaTheme(identity = identity, darkTheme = false, dynamicColor = false) {
                    primaries[identity] = MaterialTheme.colorScheme.primary
                    Text("probe-${identity.name}")
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(SecaAppIdentity.entries.size, primaries.values.toSet().size)
    }

    @Test
    fun `dark theme differs from light theme`() {
        var light = Color.Unspecified
        var dark = Color.Unspecified
        composeRule.setContent {
            SecaTheme(SecaAppIdentity.Contacts, darkTheme = false, dynamicColor = false) {
                light = MaterialTheme.colorScheme.surface
                Text("light")
            }
            SecaTheme(SecaAppIdentity.Contacts, darkTheme = true, dynamicColor = false) {
                dark = MaterialTheme.colorScheme.surface
                Text("dark")
            }
        }
        composeRule.waitForIdle()
        assertNotEquals(light, dark)
    }

    @Test
    fun `container roles follow the app accent rather than Material defaults`() {
        // Guards the gap that made every identity share Material's baseline
        // purple container: SecaAvatar paints itself with primaryContainer.
        val containers = mutableMapOf<SecaAppIdentity, Color>()
        composeRule.setContent {
            SecaAppIdentity.entries.forEach { identity ->
                SecaTheme(identity = identity, darkTheme = false, dynamicColor = false) {
                    containers[identity] = MaterialTheme.colorScheme.primaryContainer
                    Text("container-${identity.name}")
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(SecaAppIdentity.entries.size, containers.values.toSet().size)
    }

    @Test
    fun `theme wires in the Seca shape and type scales`() {
        // Guards the constraint that no app gets Material defaults by accident.
        var shapes: Shapes? = null
        var typography: Typography? = null
        composeRule.setContent {
            SecaTheme(SecaAppIdentity.Contacts, darkTheme = false, dynamicColor = false) {
                shapes = MaterialTheme.shapes
                typography = MaterialTheme.typography
                Text("probe")
            }
        }
        composeRule.waitForIdle()
        assertEquals(SecaShapes, shapes)
        assertEquals(SecaTypography, typography)
    }
}
```

- [ ] **Step 7: Run the test and check that it fails**

```powershell
.\gradlew.bat :core:design:testDebugUnitTest --tests "*SecaThemeTest*"
```

Expected: compilation failure, `SecaTheme` does not exist.

- [ ] **Step 8: Implement `SecaTheme`**

`core/design/src/main/kotlin/com/seca/core/design/SecaTheme.kt`:

```kotlin
package com.seca.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.seca.core.design.color.darkSchemeFor
import com.seca.core.design.color.lightSchemeFor

/**
 * The single entry point for Seca visuals.
 *
 * No app module defines its own colours, shapes or typography; they all
 * wrap their content in this.
 *
 * [dynamicColor] opts in to the user's Material You wallpaper palette. It
 * defaults to false: dynamic colour derives every role from the wallpaper,
 * which would make Contacts, Phone and Messages look identical and erase the
 * per-app accent that makes them recognisable as distinct members of one
 * family.
 */
@Composable
fun SecaTheme(
    identity: SecaAppIdentity,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        darkTheme -> darkSchemeFor(identity)
        else -> lightSchemeFor(identity)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SecaTypography,
        shapes = SecaShapes,
        content = content,
    )
}
```

- [ ] **Step 9: Run the test and check that it passes**

```powershell
.\gradlew.bat :core:design:testDebugUnitTest --tests "*SecaThemeTest*"
```

Expected: PASS, 4 tests. Also confirms that Compose tests run under Robolectric, with no device connected.

- [ ] **Step 10: Commit**

```bash
git add core/design
git commit -m "feat(design): Seca theme — typography, shapes, motion and colors"
```

---

### Task 5: `:core:design` — shared components

The building blocks reused by the three apps. Each one is tested under Robolectric.

**Files:**
- Create: `core/design/src/main/kotlin/com/seca/core/design/component/SecaAvatar.kt`
- Create: `core/design/src/main/kotlin/com/seca/core/design/component/SecaContactRow.kt`
- Create: `core/design/src/main/kotlin/com/seca/core/design/component/SecaEmptyState.kt`
- Test: `core/design/src/test/kotlin/com/seca/core/design/component/SecaAvatarTest.kt`
- Test: `core/design/src/test/kotlin/com/seca/core/design/component/SecaContactRowTest.kt`
- Modify: `core/design/build.gradle.kts` — add the `:core:model` dependency

**Interfaces:**
- Consumes: `SecaContact`, `SecaTheme`, `SecaMotion` from tasks 3 and 4.
- Produces:
  - `@Composable fun SecaAvatar(initials: String, photoUri: String?, modifier: Modifier = Modifier, size: Dp = 48.dp)`
  - `@Composable fun SecaContactRow(contact: SecaContact, onClick: () -> Unit, modifier: Modifier = Modifier)`
  - `@Composable fun SecaEmptyState(title: String, description: String, modifier: Modifier = Modifier)`

- [ ] **Step 1: Add the `:core:model` dependency**

In `core/design/build.gradle.kts`, in the `dependencies` block:

```kotlin
    api(project(":core:model"))
```

- [ ] **Step 2: Write the failing test for `SecaAvatar`**

`core/design/src/test/kotlin/com/seca/core/design/component/SecaAvatarTest.kt`:

```kotlin
package com.seca.core.design.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.seca.core.design.SecaAppIdentity
import com.seca.core.design.SecaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SecaAvatarTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `shows initials when no photo is available`() {
        composeRule.setContent {
            SecaTheme(SecaAppIdentity.Contacts, dynamicColor = false) {
                SecaAvatar(initials = "CD", photoUri = null)
            }
        }
        composeRule.onNodeWithText("CD").assertIsDisplayed()
    }
}
```

- [ ] **Step 3: Run the test and check that it fails**

```powershell
.\gradlew.bat :core:design:testDebugUnitTest --tests "*SecaAvatarTest*"
```

Expected: compilation failure, `SecaAvatar` does not exist.

- [ ] **Step 4: Implement `SecaAvatar`**

```kotlin
package com.seca.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A contact's avatar, rendered as initials.
 *
 * Photo rendering is NOT implemented. [photoUri] is accepted so call sites do
 * not have to change when it lands, but passing one currently has no effect —
 * no image library enters the APK until an app actually needs one. When it is
 * added, the image should carry `contentDescription = null`: the adjacent name
 * already identifies the contact, so announcing it twice hurts screen readers.
 */
@Composable
fun SecaAvatar(
    initials: String,
    photoUri: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
```

- [ ] **Step 5: Run the test and check that it passes**

```powershell
.\gradlew.bat :core:design:testDebugUnitTest --tests "*SecaAvatarTest*"
```

Expected: PASS.

- [ ] **Step 6: Write the failing test for `SecaContactRow`**

`core/design/src/test/kotlin/com/seca/core/design/component/SecaContactRowTest.kt`:

```kotlin
package com.seca.core.design.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.seca.core.design.SecaAppIdentity
import com.seca.core.design.SecaTheme
import com.seca.core.model.PhoneNumber
import com.seca.core.model.SecaContact
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SecaContactRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val camille = SecaContact(
        id = 1L,
        displayName = "Camille Durand",
        phoneNumbers = listOf(PhoneNumber("06 12 34 56 78")),
        isFavorite = false,
        photoUri = null,
    )

    @Test
    fun `shows the display name and first number`() {
        composeRule.setContent {
            SecaTheme(SecaAppIdentity.Contacts, dynamicColor = false) {
                SecaContactRow(contact = camille, onClick = {})
            }
        }
        composeRule.onNodeWithText("Camille Durand").assertIsDisplayed()
        composeRule.onNodeWithText("06 12 34 56 78").assertIsDisplayed()
    }

    @Test
    fun `shows only the name when the contact has no phone number`() {
        // Contacts with no number are routine — email-only entries, import
        // artefacts. This covers the row's only real branch.
        val numberless = camille.copy(phoneNumbers = emptyList())
        composeRule.setContent {
            SecaTheme(SecaAppIdentity.Contacts, dynamicColor = false) {
                SecaContactRow(contact = numberless, onClick = {})
            }
        }
        composeRule.onNodeWithText("Camille Durand").assertIsDisplayed()
        composeRule.onNodeWithText("06 12 34 56 78").assertDoesNotExist()
    }

    @Test
    fun `invokes onClick when tapped`() {
        var clicked = false
        composeRule.setContent {
            SecaTheme(SecaAppIdentity.Contacts, dynamicColor = false) {
                SecaContactRow(contact = camille, onClick = { clicked = true })
            }
        }
        composeRule.onNodeWithText("Camille Durand").performClick()
        assertTrue(clicked)
    }
}
```

- [ ] **Step 7: Run the test and check that it fails**

```powershell
.\gradlew.bat :core:design:testDebugUnitTest --tests "*SecaContactRowTest*"
```

Expected: compilation failure, `SecaContactRow` does not exist.

- [ ] **Step 8: Implement `SecaContactRow`**

```kotlin
package com.seca.core.design.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seca.core.model.SecaContact

/** One contact in a list: avatar, name, and first number if there is one. */
@Composable
fun SecaContactRow(
    contact: SecaContact,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SecaAvatar(initials = contact.initials, photoUri = contact.photoUri)
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = contact.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            contact.phoneNumbers.firstOrNull()?.let { number ->
                Text(
                    text = number.raw,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
```

- [ ] **Step 9: Implement `SecaEmptyState`**

```kotlin
package com.seca.core.design.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Shown when a screen has nothing to display.
 *
 * Seca Contacts relies on this for a case that is normal rather than broken:
 * under GrapheneOS Contact Scopes the system may hand the app an empty
 * directory on purpose.
 */
@Composable
fun SecaEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
```

- [ ] **Step 10: Run all the module's tests**

```powershell
.\gradlew.bat :core:design:test
```

Expected: PASS, 8 tests in all (SecaThemeTest 4, SecaAvatarTest 1, SecaContactRowTest 3).

- [ ] **Step 11: Commit**

```bash
git add core/design
git commit -m "feat(design): avatar, contact row and empty state components"
```

---

### Task 6: `apps/catalog` — design system gallery

The app that makes the design possible to judge. It installs on the Pixel 9 and shows everything: the three identities, light/dark, dynamic color.

**Files:**
- Create: `apps/catalog/src/main/AndroidManifest.xml`
- Create: `apps/catalog/src/main/kotlin/com/seca/catalog/MainActivity.kt`
- Create: `apps/catalog/src/main/kotlin/com/seca/catalog/CatalogScreen.kt`
- Test: `apps/catalog/src/test/kotlin/com/seca/catalog/CatalogScreenTest.kt`

**Interfaces:**
- Consumes: all of `:core:design`.
- Produces: an installable `com.seca.catalog` APK. No other module depends on this one.

- [ ] **Step 1: Write the manifest**

`apps/catalog/src/main/AndroidManifest.xml`. No permission — the gallery needs none.

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:label="Seca Catalogue"
        android:supportsRtl="true"
        android:theme="@style/Theme.SecaCatalog">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.SecaCatalog">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

- [ ] **Step 2: Write the launch theme**

`apps/catalog/src/main/res/values/themes.xml`. A minimal system theme, until Compose takes over.

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.SecaCatalog" parent="android:Theme.Material.NoActionBar">
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
    </style>
</resources>
```

The parent is a **platform** theme, not `com.google.android.material`: no extra dependency enters the APK.

- [ ] **Step 3: Write the failing test for the catalog screen**

`apps/catalog/src/test/kotlin/com/seca/catalog/CatalogScreenTest.kt`:

```kotlin
package com.seca.catalog

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CatalogScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders a sample contact row for the selected identity`() {
        composeRule.setContent { CatalogScreen() }
        composeRule.onNodeWithText("Camille Durand").assertIsDisplayed()
    }

    @Test
    fun `renders the empty state sample`() {
        composeRule.setContent { CatalogScreen() }
        composeRule.onNodeWithText("Aucun contact").assertIsDisplayed()
    }
}
```

- [ ] **Step 4: Run the test and check that it fails**

```powershell
.\gradlew.bat :apps:catalog:test
```

Expected: compilation failure, `CatalogScreen` does not exist.

- [ ] **Step 5: Implement `CatalogScreen`**

```kotlin
package com.seca.catalog

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.seca.core.design.SecaAppIdentity
import com.seca.core.design.SecaMotion
import com.seca.core.design.SecaTheme
import com.seca.core.design.component.SecaAvatar
import com.seca.core.design.component.SecaContactRow
import com.seca.core.design.component.SecaEmptyState
import com.seca.core.model.PhoneNumber
import com.seca.core.model.SecaContact
import androidx.compose.foundation.layout.Row

/**
 * French display names for the identity chips.
 *
 * Identifiers stay English per the project constraint, but UI strings are
 * French — `SecaAppIdentity.name` would print "Phone" on an otherwise French
 * screen. This lives in the catalog because it is the only screen that lists
 * all three apps; a real Seca app never shows a picker.
 */
private val SecaAppIdentity.frenchLabel: String
    get() = when (this) {
        SecaAppIdentity.Contacts -> "Contacts"
        SecaAppIdentity.Phone -> "Téléphone"
        SecaAppIdentity.Messages -> "Messages"
    }

private val sampleContact = SecaContact(
    id = 1L,
    displayName = "Camille Durand",
    phoneNumbers = listOf(PhoneNumber("06 12 34 56 78")),
    isFavorite = true,
    photoUri = null,
)

/** A gallery of every shared component, so the design can be judged as a whole. */
@Composable
fun CatalogScreen() {
    var identity by remember { mutableStateOf(SecaAppIdentity.Contacts) }
    // Dark and dynamic colour are the catalog's starting state by request:
    // it should open looking the way the user expects to use their phone.
    var dark by remember { mutableStateOf(true) }
    var dynamic by remember { mutableStateOf(true) }

    SecaTheme(identity = identity, darkTheme = dark, dynamicColor = dynamic) {
        // Animating the background makes the shared motion spec visible: switching
        // identity or theme should feel like one system reacting, not a hard cut.
        val background by animateColorAsState(
            targetValue = MaterialTheme.colorScheme.background,
            animationSpec = SecaMotion.emphasized(),
            label = "background",
        )
        // The Surface paints edge to edge so no band of stale colour shows behind
        // the system bars; the content itself is inset so nothing collides with
        // the clock or the navigation bar.
        Surface(color = background, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .safeDrawingPadding()
                    .padding(vertical = 24.dp),
            ) {
                Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    SecaAppIdentity.entries.forEach { entry ->
                        FilterChip(
                            selected = identity == entry,
                            onClick = { identity = entry },
                            label = { Text(entry.frenchLabel) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }

                Row(modifier = Modifier.padding(horizontal = 20.dp)) {
                    FilterChip(
                        selected = dark,
                        onClick = { dark = !dark },
                        label = { Text("Sombre") },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    FilterChip(
                        selected = dynamic,
                        onClick = { dynamic = !dynamic },
                        label = { Text("Couleur dynamique") },
                    )
                }

                Text(
                    text = "Avatars",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 20.dp, top = 32.dp, bottom = 12.dp),
                )
                Row(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SecaAvatar("CD", null, size = 40.dp, modifier = Modifier.padding(end = 12.dp))
                    SecaAvatar("AB", null, size = 56.dp, modifier = Modifier.padding(end = 12.dp))
                    SecaAvatar("Z", null, size = 72.dp)
                }

                Text(
                    text = "Ligne de contact",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 20.dp, top = 32.dp, bottom = 12.dp),
                )
                SecaContactRow(contact = sampleContact, onClick = {})

                Text(
                    text = "État vide",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 20.dp, top = 32.dp, bottom = 12.dp),
                )
                SecaEmptyState(
                    title = "Aucun contact",
                    description = "Les contacts que vous ajoutez apparaîtront ici.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                )
            }
        }
    }
}
```

- [ ] **Step 6: Implement `MainActivity`**

```kotlin
package com.seca.catalog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CatalogScreen() }
    }
}
```

- [ ] **Step 7: Run the tests and check that they pass**

```powershell
.\gradlew.bat :apps:catalog:test
```

Expected: PASS, 2 tests.

- [ ] **Step 8: Build the APK**

```powershell
.\gradlew.bat :apps:catalog:assembleDebug
```

Expected: BUILD SUCCESSFUL, APK in `apps/catalog/build/outputs/apk/debug/`.

- [ ] **Step 9: Install on the Pixel 9 and look**

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r apps\catalog\build\outputs\apk\debug\catalog-debug.apk
```

Open the app and **judge the result**: switch between the three identities, turn on dark, turn on dynamic color. This is the checkpoint for "very, very pretty". If the result does not fit, this is where palettes, shapes and typography get adjusted — before writing anything functional.

- [ ] **Step 10: Commit**

```bash
git add apps/catalog
git commit -m "feat(catalog): design system gallery"
```

---

### Task 7: Guards and documentation

Locks in the properties the spec claims so they cannot regress silently: no proprietary dependency in what ships in the APK, and a lint that fails instead of warning.

> **Rewritten on 2026-09-10.** The first version prescribed a unit test that looked for `com.google.android.gms` in `System.getProperty("java.class.path")`. Run with `play-services-base:18.3.0` really present on the test classpath, it **passed**. AARs are transformed before they reach a classpath (`caches/<gradle>/transforms/<hash>/transformed/play-services-base-18.3.0/jars/classes.jar`) and the Maven group appears nowhere in that path: this test could not fail. It also only inspected the test classpath of `:core:design`, not what ships in an APK. It is replaced by a check of the resolved Maven coordinates of each app's *release* classpath.

**Files:**
- Create: `build-logic/src/main/kotlin/com/seca/buildlogic/VerifyNoProprietaryDependencies.kt`
- Modify: `build-logic/src/main/kotlin/seca.android.application.gradle.kts` — register the check, make lint fail
- Create: `README.md`

**Interfaces:**
- Consumes: the `seca.android.application` convention plugin from task 2.
- Produces: in each app module, a `verifyReleaseNoProprietaryDependencies` task wired into `check`. No Kotlin API.

- [ ] **Step 1: Write the verification task**

`build-logic/src/main/kotlin/com/seca/buildlogic/VerifyNoProprietaryDependencies.kt`:

```kotlin
package com.seca.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * Fails the build if a proprietary Google artifact reaches an app's runtime
 * classpath.
 *
 * Inspects resolved Maven coordinates, not file paths: AARs are transformed
 * before they reach a classpath and the transformed path no longer contains
 * the group, so a path-based check cannot see them. This is a tripwire for the
 * well-known offenders, not an exhaustive scanner — F-Droid's own scanner
 * remains the authority.
 */
abstract class VerifyNoProprietaryDependencies : DefaultTask() {

    /** Wired from `incoming.resolutionResult.rootComponent`; configuration-cache safe. */
    @get:Input
    abstract val rootComponent: Property<ResolvedComponentResult>

    @TaskAction
    fun verify() {
        val offenders = resolvedModules(rootComponent.get())
            .filter { id -> ForbiddenGroups.any { id.group == it || id.group.startsWith("$it.") } }
            .map { "${it.group}:${it.module}:${it.version}" }
            .sorted()
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "Proprietary dependencies on the runtime classpath:\n" +
                    offenders.joinToString("\n") { "  - $it" },
            )
        }
    }

    private fun resolvedModules(root: ResolvedComponentResult): Set<ModuleComponentIdentifier> {
        val seen = mutableSetOf<ResolvedComponentResult>()
        val modules = mutableSetOf<ModuleComponentIdentifier>()
        val queue = ArrayDeque(listOf(root))
        while (queue.isNotEmpty()) {
            val component = queue.removeFirst()
            if (!seen.add(component)) continue
            (component.id as? ModuleComponentIdentifier)?.let(modules::add)
            component.dependencies
                .filterIsInstance<ResolvedDependencyResult>()
                .forEach { queue.addLast(it.selected) }
        }
        return modules
    }

    private companion object {
        val ForbiddenGroups = listOf(
            "com.google.android.gms",
            "com.google.firebase",
            "com.google.android.play",
        )
    }
}
```

The filter compares the whole group or a subgroup (`"$it."`), never a plain string prefix: `com.google.android.material`, which is free, must not be mistaken for `com.google.android.play`.

- [ ] **Step 2: Register the check in the application convention plugin**

In `build-logic/src/main/kotlin/seca.android.application.gradle.kts` — **read the file as it is first**; remove nothing that is already there.

Add at the top, next to the existing import:

```kotlin
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.seca.buildlogic.VerifyNoProprietaryDependencies
```

Add at the end of the file:

```kotlin
// One check per release variant: it inspects exactly what ships in the APK.
extensions.configure<ApplicationAndroidComponentsExtension> {
    onVariants(selector().withBuildType("release")) { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val verify = tasks.register<VerifyNoProprietaryDependencies>(
            "verify${name}NoProprietaryDependencies",
        ) {
            group = "verification"
            description = "Fails if a proprietary Google artifact reaches the $name runtime classpath."
            rootComponent.set(variant.runtimeConfiguration.incoming.resolutionResult.rootComponent)
        }
        tasks.named("check") { dependsOn(verify) }
    }
}
```

`rootComponent` is a lazy `Provider`, resolved when the task runs. The action never touches `project` or `configurations`: that is what makes it compatible with the configuration cache, which this repository turns on.

- [ ] **Step 3: Make lint fail**

In the same file, inside the existing `extensions.configure<ApplicationExtension>` block:

```kotlin
    lint {
        warningsAsErrors = true
        abortOnError = true
        // Pinned versions are the point of a reproducible build; this check
        // would otherwise fire on every dependency the moment a newer one ships.
        disable += setOf("GradleDependency")
    }
```

- [ ] **Step 4: Check that the task passes on the real state**

```powershell
.\gradlew.bat :apps:catalog:verifyReleaseNoProprietaryDependencies
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Prove that it fails when it should — without touching a single tracked file**

A guard that cannot fail guarantees nothing: that is exactly the flaw of the first version. The proof uses a temporary init script, **outside the repository**, that injects the intruder for a single invocation. No file tracked by git is ever modified: if the run is interrupted halfway, there is nothing to revert.

```powershell
$init = Join-Path $env:TEMP "seca-teeth.init.gradle.kts"
@'
allprojects {
    if (path == ":apps:catalog") {
        afterEvaluate {
            dependencies.add("implementation", "com.google.android.gms:play-services-base:18.3.0")
        }
    }
}
'@ | Set-Content -Encoding utf8 $init

# 1. The intruder is really there.
.\gradlew.bat :apps:catalog:dependencies --configuration releaseRuntimeClasspath --init-script $init | Select-String "com.google.android.gms"

# 2. And the check fails.
.\gradlew.bat :apps:catalog:verifyReleaseNoProprietaryDependencies --init-script $init

Remove-Item $init
git status --porcelain
```

Expected:
1. the first command lists `com.google.android.gms:play-services-base:18.3.0`;
2. the second ends in **BUILD FAILED**, with a message naming at least `com.google.android.gms:play-services-base:18.3.0`;
3. `git status --porcelain` shows no change related to the proof.

**If the second command passes, stop**: either the injection did not take, or the check is wrong. Either way, the guard is not demonstrated and must not be committed as such.

- [ ] **Step 6: Run lint**

```powershell
.\gradlew.bat :apps:catalog:lint
```

Expected: BUILD SUCCESSFUL. Fix what is reported in `apps/catalog`. If lint reports something in `core/` or in `build-logic/`, stop and report it: that would be a finding about the foundation, not a fix to slip in at the end of a task.

- [ ] **Step 7: Write the README**

`README.md`:

````markdown
# Seca

Three communication apps for Android, designed for GrapheneOS:
**Seca Contacts**, **Seca Phone** and **Seca Messages**. One shared Material 3
Expressive design, privacy first, with no Google dependency at all.

## Status

Foundation and design system in place, with a catalog app that presents
them. Seca Contacts is the next step. See `docs/superpowers/specs/`
for the design and `docs/superpowers/plans/` for the implementation plans.

## Three apps, one family

The three apps stay separate APKs. Each shows a bar at the bottom that
leads to the other two; they launch each other by intent. Keeping three separate
APKs keeps their permissions apart: Seca Contacts never needs
`INTERNET`, Seca Phone holds `ROLE_DIALER`, Seca Messages `ROLE_SMS`.

The light or dark theme follows the system's, with no setting in the app. The
only visual preference is the palette — Ocean, Forest, Dusk or Slate.
Each app shifts the hue of the chosen palette, so that the three
stay recognisable whatever the palette.

Material You is not offered: dynamic color takes every hue from the
wallpaper and made the three apps identical.

## What Seca does not do

- **No RCS.** Google keeps its RCS API to a closed allowlist;
  no third-party app can implement it. Seca Messages will offer
  SMS/MMS and an end-to-end encrypted layer between Seca users.
- **No Wi-Fi calling implementation.** VoWiFi belongs to the system's IMS stack
  and already works whatever dialer is installed.
  Seca Phone will show its state.

## Build

Requirements: JDK 17 and the Android SDK (platform `android-37.0`, build-tools 36.0.0).

```bash
./gradlew :apps:catalog:assembleDebug
./gradlew test
./gradlew :apps:catalog:check
```

## Privacy

No analytics, no crash reporting, no Google dependency. Each
app checks at build time that no `com.google.android.gms`,
`com.google.firebase` or `com.google.android.play` artifact reaches its runtime
classpath: the `verifyReleaseNoProprietaryDependencies` task, wired into
`check`, fails the build otherwise.

Seca Contacts will **not** ask for the `INTERNET` permission: the app
will be structurally unable to exfiltrate an address book.

The bar's three icons are drawn in the repository, as `ImageVector`s.
Google's `material-icons-core` and `material-icons-extended` libraries
are frozen at 1.7.8 while the project uses Compose 1.12.0: three icons
did not justify shipping an abandoned dependency.

## License

GPL-3.0-or-later.
````

- [ ] **Step 8: Full suite**

```powershell
.\gradlew.bat test
.\gradlew.bat :apps:catalog:check
```

Expected: `test` green with **24 tests** — 10 `:core:model`, 11 `:core:design`, 3 `:apps:catalog` (the first version's unit test no longer exists); `check` green, which covers lint and the dependency check.

- [ ] **Step 9: Commit**

Add each file by its path. **Never `git add -A` or `git add .`**: an orphaned file left by an interrupted run — including a temporary proprietary dependency — would enter the history.

```bash
git add build-logic/src/main/kotlin/com/seca/buildlogic/VerifyNoProprietaryDependencies.kt \
        build-logic/src/main/kotlin/seca.android.application.gradle.kts \
        README.md
git commit -m "chore: Gradle-level dependency guard, strict lint and README"
```

If the lint fixes touched files in `apps/catalog`, add them too, by name.

---

### Task 8: `:core:design` — palettes to choose from, system theme

Replaces Material You with a small palette the user picks, from which each app derives a distinct variant. The light/dark theme follows the system and can no longer be set in the app.

The accents are no longer written by hand one by one: each palette carries a base hue, each identity applies a fixed hue shift, and the roles derive through constant HSL recipes. Compose already provides `Color.hsl()` — no color conversion to write. It gives consistent families, 4 palettes × 3 identities × 2 themes without hand-authoring 24 quadruplets, and it is testable.

**Files:**
- Create: `core/design/src/main/kotlin/com/seca/core/design/SecaPalette.kt`
- Modify: `core/design/src/main/kotlin/com/seca/core/design/color/SecaPalettes.kt` (derivations rewritten)
- Modify: `core/design/src/main/kotlin/com/seca/core/design/SecaTheme.kt` (signature)
- Modify: `core/design/src/test/kotlin/com/seca/core/design/SecaThemeTest.kt`

**Interfaces:**
- Consumes: `SecaAppIdentity`, `SecaTypography`, `SecaShapes`.
- Produces:
  - `enum class SecaPalette(val label: String)` — `Ocean`, `Foret`, `Crepuscule`, `Ardoise`
  - `@Composable fun SecaTheme(identity: SecaAppIdentity, palette: SecaPalette = SecaPalette.Ocean, darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)`
  - **`dynamicColor` goes away.** Every caller that passed it must be updated.

- [ ] **Step 1: Write `SecaPalette`**

`core/design/src/main/kotlin/com/seca/core/design/SecaPalette.kt`:

```kotlin
package com.seca.core.design

/**
 * The accent families a user can choose between.
 *
 * Material You is deliberately not offered: deriving every role from the
 * wallpaper made all three Seca apps look identical, which defeats the
 * per-app identity. A palette instead sets a base hue; each app shifts it by
 * a fixed step so the three stay distinguishable inside every palette.
 *
 * [label] is user-visible and therefore French.
 */
enum class SecaPalette(
    val label: String,
    internal val baseHue: Float,
    internal val chroma: Float,
) {
    Ocean("Océan", baseHue = 185f, chroma = 1f),
    Foret("Forêt", baseHue = 128f, chroma = 0.9f),
    Crepuscule("Crépuscule", baseHue = 322f, chroma = 0.95f),
    Ardoise("Ardoise", baseHue = 220f, chroma = 0.4f),
}
```

- [ ] **Step 2: Rewrite the color derivations**

`core/design/src/main/kotlin/com/seca/core/design/color/SecaPalettes.kt` — replaces the whole content:

```kotlin
package com.seca.core.design.color

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.seca.core.design.SecaAppIdentity
import com.seca.core.design.SecaPalette

/** One app's accent within one palette, for one theme. */
private data class Accent(
    val primary: Color,
    val onPrimary: Color,
    val container: Color,
    val onContainer: Color,
)

/**
 * How far each app's hue sits from its palette's base.
 *
 * Large enough to read as a different colour at a glance, small enough that
 * the three still look like one family.
 */
private const val IdentityHueStep = 34f

private fun hueFor(palette: SecaPalette, identity: SecaAppIdentity): Float {
    val step = when (identity) {
        SecaAppIdentity.Contacts -> 0f
        SecaAppIdentity.Phone -> IdentityHueStep
        SecaAppIdentity.Messages -> IdentityHueStep * 2f
    }
    return (palette.baseHue + step) % 360f
}

private fun lightAccent(palette: SecaPalette, identity: SecaAppIdentity): Accent {
    val h = hueFor(palette, identity)
    val s = 0.62f * palette.chroma
    return Accent(
        primary = Color.hsl(h, s, 0.32f),
        onPrimary = Color.White,
        container = Color.hsl(h, s * 0.7f, 0.88f),
        onContainer = Color.hsl(h, s, 0.12f),
    )
}

private fun darkAccent(palette: SecaPalette, identity: SecaAppIdentity): Accent {
    val h = hueFor(palette, identity)
    val s = 0.55f * palette.chroma
    return Accent(
        primary = Color.hsl(h, s, 0.72f),
        onPrimary = Color.hsl(h, s, 0.14f),
        container = Color.hsl(h, s, 0.28f),
        onContainer = Color.hsl(h, s * 0.8f, 0.90f),
    )
}

// Shared neutral base — identical across palettes and apps, so the family
// reads as one system whatever accent the user picked.
private val SurfaceLight = Color(0xFFF7FAFA)
private val OnSurfaceLight = Color(0xFF191C1D)
private val SurfaceVariantLight = Color(0xFFDBE4E5)
private val OnSurfaceVariantLight = Color(0xFF3F4849)
private val OutlineLight = Color(0xFF6F7979)

private val SurfaceDark = Color(0xFF0E1414)
private val OnSurfaceDark = Color(0xFFE1E3E3)
private val SurfaceVariantDark = Color(0xFF3F4849)
private val OnSurfaceVariantDark = Color(0xFFBFC8C9)
private val OutlineDark = Color(0xFF899393)

internal fun lightSchemeFor(palette: SecaPalette, identity: SecaAppIdentity): ColorScheme {
    val a = lightAccent(palette, identity)
    return lightColorScheme(
        primary = a.primary,
        onPrimary = a.onPrimary,
        primaryContainer = a.container,
        onPrimaryContainer = a.onContainer,
        secondary = a.primary,
        onSecondary = a.onPrimary,
        secondaryContainer = a.container,
        onSecondaryContainer = a.onContainer,
        tertiary = a.primary,
        onTertiary = a.onPrimary,
        tertiaryContainer = a.container,
        onTertiaryContainer = a.onContainer,
        surface = SurfaceLight,
        onSurface = OnSurfaceLight,
        surfaceVariant = SurfaceVariantLight,
        onSurfaceVariant = OnSurfaceVariantLight,
        outline = OutlineLight,
        background = SurfaceLight,
        onBackground = OnSurfaceLight,
    )
}

internal fun darkSchemeFor(palette: SecaPalette, identity: SecaAppIdentity): ColorScheme {
    val a = darkAccent(palette, identity)
    return darkColorScheme(
        primary = a.primary,
        onPrimary = a.onPrimary,
        primaryContainer = a.container,
        onPrimaryContainer = a.onContainer,
        secondary = a.primary,
        onSecondary = a.onPrimary,
        secondaryContainer = a.container,
        onSecondaryContainer = a.onContainer,
        tertiary = a.primary,
        onTertiary = a.onPrimary,
        tertiaryContainer = a.container,
        onTertiaryContainer = a.onContainer,
        surface = SurfaceDark,
        onSurface = OnSurfaceDark,
        surfaceVariant = SurfaceVariantDark,
        onSurfaceVariant = OnSurfaceVariantDark,
        outline = OutlineDark,
        background = SurfaceDark,
        onBackground = OnSurfaceDark,
    )
}
```

- [ ] **Step 3: Write the failing tests**

Replace `core/design/src/test/kotlin/com/seca/core/design/SecaThemeTest.kt`:

```kotlin
package com.seca.core.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SecaThemeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `every app identity gets a distinct accent within a palette`() {
        val primaries = mutableMapOf<SecaAppIdentity, Color>()
        composeRule.setContent {
            SecaAppIdentity.entries.forEach { identity ->
                SecaTheme(identity = identity, palette = SecaPalette.Ocean, darkTheme = false) {
                    primaries[identity] = MaterialTheme.colorScheme.primary
                    Text("probe-${identity.name}")
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(SecaAppIdentity.entries.size, primaries.values.toSet().size)
    }

    @Test
    fun `every palette gives the same app a distinct accent`() {
        val primaries = mutableMapOf<SecaPalette, Color>()
        composeRule.setContent {
            SecaPalette.entries.forEach { palette ->
                SecaTheme(identity = SecaAppIdentity.Contacts, palette = palette, darkTheme = false) {
                    primaries[palette] = MaterialTheme.colorScheme.primary
                    Text("palette-${palette.name}")
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(SecaPalette.entries.size, primaries.values.toSet().size)
    }

    @Test
    fun `container roles follow the app accent rather than Material defaults`() {
        val containers = mutableMapOf<SecaAppIdentity, Color>()
        composeRule.setContent {
            SecaAppIdentity.entries.forEach { identity ->
                SecaTheme(identity = identity, palette = SecaPalette.Ocean, darkTheme = false) {
                    containers[identity] = MaterialTheme.colorScheme.primaryContainer
                    Text("container-${identity.name}")
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(SecaAppIdentity.entries.size, containers.values.toSet().size)
    }

    @Test
    fun `dark theme differs from light theme`() {
        var light = Color.Unspecified
        var dark = Color.Unspecified
        composeRule.setContent {
            SecaTheme(SecaAppIdentity.Contacts, SecaPalette.Ocean, darkTheme = false) {
                light = MaterialTheme.colorScheme.surface
                Text("light")
            }
            SecaTheme(SecaAppIdentity.Contacts, SecaPalette.Ocean, darkTheme = true) {
                dark = MaterialTheme.colorScheme.surface
                Text("dark")
            }
        }
        composeRule.waitForIdle()
        assertNotEquals(light, dark)
    }

    @Test
    fun `theme wires in the Seca shape and type scales`() {
        var shapes: Shapes? = null
        var typography: Typography? = null
        composeRule.setContent {
            SecaTheme(SecaAppIdentity.Contacts, SecaPalette.Ocean, darkTheme = false) {
                shapes = MaterialTheme.shapes
                typography = MaterialTheme.typography
                Text("probe")
            }
        }
        composeRule.waitForIdle()
        assertEquals(SecaShapes, shapes)
        assertEquals(SecaTypography, typography)
    }
}
```

- [ ] **Step 4: Run and check that it fails**

```powershell
.\gradlew.bat :core:design:testDebugUnitTest --tests "*SecaThemeTest*"
```

Expected: compilation failure — `SecaPalette` does not exist and `SecaTheme` does not take this parameter yet.

- [ ] **Step 5: Rewrite `SecaTheme`**

```kotlin
package com.seca.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.seca.core.design.color.darkSchemeFor
import com.seca.core.design.color.lightSchemeFor

/**
 * The single entry point for Seca visuals.
 *
 * No app module defines its own colours, shapes or typography; they all wrap
 * their content in this.
 *
 * [darkTheme] follows the system and is never toggled inside an app — the
 * parameter exists so tests can assert both themes. [palette] is the one
 * visual choice a user gets; each [identity] shifts its hue so the three apps
 * stay distinguishable whichever palette is picked.
 */
@Composable
fun SecaTheme(
    identity: SecaAppIdentity,
    palette: SecaPalette = SecaPalette.Ocean,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme =
        if (darkTheme) darkSchemeFor(palette, identity) else lightSchemeFor(palette, identity)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SecaTypography,
        shapes = SecaShapes,
        content = content,
    )
}
```

- [ ] **Step 6: Update the existing callers**

`SecaAvatarTest` and `SecaContactRowTest` pass `dynamicColor = false`, which no longer exists. Remove that argument from the three calls — change nothing else in these tests.

- [ ] **Step 7: Run the module's suite**

```powershell
.\gradlew.bat :core:design:test
```

Expected: PASS, 9 tests (SecaThemeTest 5, SecaAvatarTest 1, SecaContactRowTest 3).

- [ ] **Step 8: Commit**

```bash
git add core/design
git commit -m "feat(design): palettes to choose from, theme following the system"
```

---

### Task 9: `:core:design` — icons and cross-app bar

Three hand-drawn icons and a bottom navigation bar shared by the three apps. `material-icons-core` and `-extended` are frozen at 1.7.8 against Compose 1.12.0: dead libraries, set aside. Three icons do not justify an abandoned dependency.

**Files:**
- Create: `core/design/src/main/kotlin/com/seca/core/design/SecaIcons.kt`
- Create: `core/design/src/main/kotlin/com/seca/core/design/component/SecaSuiteBar.kt`
- Test: `core/design/src/test/kotlin/com/seca/core/design/component/SecaSuiteBarTest.kt`

**Interfaces:**
- Consumes: `SecaAppIdentity`, `SecaPalette`, `SecaTheme`.
- Produces:
  - `object SecaIcons` with `Contacts`, `Phone`, `Messages` (`ImageVector`)
  - `@Composable fun SecaSuiteBar(current: SecaAppIdentity, onSelect: (SecaAppIdentity) -> Unit, modifier: Modifier = Modifier)`

`SecaSuiteBar` knows no `Intent`. Each app decides in `onSelect` what it does — launch the sibling app, or switch the preview in the catalog. `:core:design` stays free of Android navigation logic.

- [ ] **Step 1: Write the icons**

`core/design/src/main/kotlin/com/seca/core/design/SecaIcons.kt`:

```kotlin
package com.seca.core.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The three suite icons, drawn here rather than pulled from a library.
 *
 * `material-icons-core` and `-extended` stopped at 1.7.8 while this project
 * runs Compose 1.12.0 — they are abandoned, and three icons do not justify a
 * dead dependency inside an F-Droid build.
 */
object SecaIcons {

    val Contacts: ImageVector = icon("Contacts") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 12f)
            curveToRelative(2.21f, 0f, 4f, -1.79f, 4f, -4f)
            reflectiveCurveToRelative(-1.79f, -4f, -4f, -4f)
            reflectiveCurveToRelative(-4f, 1.79f, -4f, 4f)
            reflectiveCurveToRelative(1.79f, 4f, 4f, 4f)
            close()
            moveTo(12f, 14f)
            curveToRelative(-2.67f, 0f, -8f, 1.34f, -8f, 4f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(16f)
            verticalLineToRelative(-2f)
            curveToRelative(0f, -2.66f, -5.33f, -4f, -8f, -4f)
            close()
        }
    }

    val Phone: ImageVector = icon("Phone") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(6.62f, 10.79f)
            curveToRelative(1.44f, 2.83f, 3.76f, 5.14f, 6.59f, 6.59f)
            lineToRelative(2.2f, -2.2f)
            curveToRelative(0.27f, -0.27f, 0.67f, -0.36f, 1.02f, -0.24f)
            curveToRelative(1.12f, 0.37f, 2.33f, 0.57f, 3.57f, 0.57f)
            curveToRelative(0.55f, 0f, 1f, 0.45f, 1f, 1f)
            verticalLineTo(20f)
            curveToRelative(0f, 0.55f, -0.45f, 1f, -1f, 1f)
            curveToRelative(-9.39f, 0f, -17f, -7.61f, -17f, -17f)
            curveToRelative(0f, -0.55f, 0.45f, -1f, 1f, -1f)
            horizontalLineToRelative(3.5f)
            curveToRelative(0.55f, 0f, 1f, 0.45f, 1f, 1f)
            curveToRelative(0f, 1.25f, 0.2f, 2.45f, 0.57f, 3.57f)
            curveToRelative(0.11f, 0.35f, 0.03f, 0.74f, -0.25f, 1.02f)
            lineToRelative(-2.2f, 2.2f)
            close()
        }
    }

    val Messages: ImageVector = icon("Messages") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(20f, 2f)
            horizontalLineTo(4f)
            curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f)
            lineTo(2f, 22f)
            lineToRelative(4f, -4f)
            horizontalLineToRelative(14f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(4f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            close()
        }
    }

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(block).build()
}
```

- [ ] **Step 2: Write the failing test**

`core/design/src/test/kotlin/com/seca/core/design/component/SecaSuiteBarTest.kt`:

```kotlin
package com.seca.core.design.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.seca.core.design.SecaAppIdentity
import com.seca.core.design.SecaPalette
import com.seca.core.design.SecaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SecaSuiteBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `shows all three apps in French`() {
        composeRule.setContent {
            SecaTheme(SecaAppIdentity.Contacts, SecaPalette.Ocean, darkTheme = false) {
                SecaSuiteBar(current = SecaAppIdentity.Contacts, onSelect = {})
            }
        }
        composeRule.onNodeWithText("Contacts").assertIsDisplayed()
        composeRule.onNodeWithText("Téléphone").assertIsDisplayed()
        composeRule.onNodeWithText("Messages").assertIsDisplayed()
    }

    @Test
    fun `reports the app the user tapped`() {
        var chosen: SecaAppIdentity? = null
        composeRule.setContent {
            SecaTheme(SecaAppIdentity.Contacts, SecaPalette.Ocean, darkTheme = false) {
                SecaSuiteBar(current = SecaAppIdentity.Contacts, onSelect = { chosen = it })
            }
        }
        composeRule.onNodeWithText("Téléphone").performClick()
        assertEquals(SecaAppIdentity.Phone, chosen)
    }
}
```

- [ ] **Step 3: Run and check that it fails**

```powershell
.\gradlew.bat :core:design:testDebugUnitTest --tests "*SecaSuiteBarTest*"
```

Expected: compilation failure, `SecaSuiteBar` does not exist.

- [ ] **Step 4: Implement `SecaSuiteBar`**

```kotlin
package com.seca.core.design.component

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.seca.core.design.SecaAppIdentity
import com.seca.core.design.SecaIcons

/**
 * The bar that ties the three Seca apps together.
 *
 * Each app shows it with its own [current] identity selected; tapping another
 * entry calls [onSelect]. This component deliberately knows nothing about
 * `Intent`s — the app decides what selecting a sibling does, which keeps
 * `:core:design` free of Android navigation logic and lets the catalog reuse
 * it as a preview switcher.
 */
@Composable
fun SecaSuiteBar(
    current: SecaAppIdentity,
    onSelect: (SecaAppIdentity) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        SecaAppIdentity.entries.forEach { identity ->
            NavigationBarItem(
                selected = identity == current,
                onClick = { onSelect(identity) },
                icon = { Icon(identity.icon, contentDescription = null) },
                label = { Text(identity.label) },
                alwaysShowLabel = true,
            )
        }
    }
}

private val SecaAppIdentity.icon: ImageVector
    get() = when (this) {
        SecaAppIdentity.Contacts -> SecaIcons.Contacts
        SecaAppIdentity.Phone -> SecaIcons.Phone
        SecaAppIdentity.Messages -> SecaIcons.Messages
    }

/**
 * French display name. `SecaAppIdentity.name` would print "Phone" on an
 * otherwise French screen; identifiers stay English, UI strings do not.
 */
internal val SecaAppIdentity.label: String
    get() = when (this) {
        SecaAppIdentity.Contacts -> "Contacts"
        SecaAppIdentity.Phone -> "Téléphone"
        SecaAppIdentity.Messages -> "Messages"
    }
```

`contentDescription = null` on the icon is deliberate: the text label just below already carries the name, and `NavigationBarItem` merges both into a single semantic node. Describing it twice would hurt screen readers.

- [ ] **Step 5: Run and check that it passes**

```powershell
.\gradlew.bat :core:design:test
```

Expected: PASS, 11 tests (SecaThemeTest 5, SecaAvatarTest 1, SecaContactRowTest 3, SecaSuiteBarTest 2).

- [ ] **Step 6: Commit**

```bash
git add core/design
git commit -m "feat(design): hand-drawn icons and cross-app bar"
```

---

### Task 10: `apps/catalog` — bottom bar, palette picker, system theme

The catalog takes its final shape: no more theme switch, a palette choice, and the three identities moved into the bottom bar with their icons.

**Files:**
- Modify: `apps/catalog/src/main/kotlin/com/seca/catalog/CatalogScreen.kt`
- Modify: `apps/catalog/src/test/kotlin/com/seca/catalog/CatalogScreenTest.kt`

**Interfaces:**
- Consumes: `SecaTheme`, `SecaPalette`, `SecaSuiteBar`, `SecaAvatar`, `SecaContactRow`, `SecaEmptyState`, `SecaMotion`.
- Produces: nothing. No module depends on the catalog.

- [ ] **Step 1: Rewrite `CatalogScreen`**

```kotlin
package com.seca.catalog

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.seca.core.design.SecaAppIdentity
import com.seca.core.design.SecaMotion
import com.seca.core.design.SecaPalette
import com.seca.core.design.SecaTheme
import com.seca.core.design.component.SecaAvatar
import com.seca.core.design.component.SecaContactRow
import com.seca.core.design.component.SecaEmptyState
import com.seca.core.design.component.SecaSuiteBar
import com.seca.core.model.PhoneNumber
import com.seca.core.model.SecaContact

private val sampleContact = SecaContact(
    id = 1L,
    displayName = "Camille Durand",
    phoneNumbers = listOf(PhoneNumber("06 12 34 56 78")),
    isFavorite = true,
    photoUri = null,
)

/**
 * A gallery of every shared component, so the design can be judged as a whole.
 *
 * There is no light/dark switch: the theme follows the system, exactly as the
 * real apps will. The only choice offered is the palette, which is also the
 * only choice a Seca user gets.
 */
@Composable
fun CatalogScreen() {
    var identity by remember { mutableStateOf(SecaAppIdentity.Contacts) }
    var palette by remember { mutableStateOf(SecaPalette.Ocean) }

    SecaTheme(identity = identity, palette = palette) {
        Scaffold(
            bottomBar = {
                SecaSuiteBar(current = identity, onSelect = { identity = it })
            },
        ) { innerPadding ->
            // Animating the background makes the shared motion spec visible:
            // switching app or palette should feel like one system reacting.
            val background by animateColorAsState(
                targetValue = MaterialTheme.colorScheme.background,
                animationSpec = SecaMotion.emphasized(),
                label = "background",
            )
            Surface(color = background, modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(innerPadding)
                        .padding(vertical = 24.dp),
                ) {
                    Text(
                        text = "Palette",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 20.dp, bottom = 12.dp),
                    )
                    Row(modifier = Modifier.padding(horizontal = 20.dp)) {
                        SecaPalette.entries.forEach { entry ->
                            FilterChip(
                                selected = palette == entry,
                                onClick = { palette = entry },
                                label = { Text(entry.label) },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }

                    Text(
                        text = "Avatars",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 20.dp, top = 32.dp, bottom = 12.dp),
                    )
                    // Centred, not the Row default of Alignment.Top: three
                    // different sizes sharing a top edge cascade downward.
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SecaAvatar("CD", null, size = 40.dp, modifier = Modifier.padding(end = 12.dp))
                        SecaAvatar("AB", null, size = 56.dp, modifier = Modifier.padding(end = 12.dp))
                        SecaAvatar("Z", null, size = 72.dp)
                    }

                    Text(
                        text = "Ligne de contact",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 20.dp, top = 32.dp, bottom = 12.dp),
                    )
                    SecaContactRow(contact = sampleContact, onClick = {})

                    Text(
                        text = "État vide",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 20.dp, top = 32.dp, bottom = 12.dp),
                    )
                    // Framed, not just shortened. SecaEmptyState carries 32dp of
                    // its own padding and centres its content — correct when it
                    // fills a real screen, but in a gallery strip that reads as
                    // an accidental gap under the heading. Shrinking the box only
                    // reduced it (261px to 156px, against 46-67px elsewhere on
                    // this page). A surface frame makes the component's own
                    // breathing room look deliberate instead, and the frame
                    // itself starts on the page's normal rhythm.
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .height(200.dp),
                    ) {
                        SecaEmptyState(
                            title = "Aucun contact",
                            description = "Les contacts que vous ajoutez apparaîtront ici.",
                        )
                    }
                }
            }
        }
    }
}
```

`Scaffold` provides `innerPadding`, which already carries the system insets and the height of the bottom bar. It replaces the earlier `safeDrawingPadding()` and keeps the content from running under the navigation bar.

The private `frenchLabel` that lived in this file becomes dead code: the identity chips are gone, and `SecaSuiteBar` renders its own labels. **Delete it.** It cannot be replaced by `SecaAppIdentity.label` from `:core:design`, which is `internal` and so invisible from an app module — but the catalog no longer needs it, so there is nothing to share.

- [ ] **Step 2: Update the test**

In `apps/catalog/src/test/kotlin/com/seca/catalog/CatalogScreenTest.kt`, add the `androidx.compose.ui.test.performClick` import and this third case:

```kotlin
    @Test
    fun `switching app from the suite bar re-themes the screen`() {
        composeRule.setContent { CatalogScreen() }
        composeRule.onNodeWithText("Téléphone").performClick()
        composeRule.onNodeWithText("Camille Durand").assertIsDisplayed()
    }
```

It checks that the bottom bar is really wired and that switching app does not break the screen — which no test covered.

- [ ] **Step 3: Run the full suite**

```powershell
.\gradlew.bat test
```

Expected: PASS, 24 tests — 10 `:core:model`, 11 `:core:design`, 3 `:apps:catalog`.

- [ ] **Step 4: Install and check by eye**

```powershell
.\gradlew.bat :apps:catalog:assembleDebug
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r apps\catalog\build\outputs\apk\debug\catalog-debug.apk
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am start -n com.seca.catalog/.MainActivity
```

Wake the device before any screenshot (`input keyevent KEYCODE_WAKEUP`), or the image is black. Check: bottom bar with the three icons, theme following the system, four palettes that really change the accent.

**Capture at least one image with `Téléphone` OR `Messages` selected in the bottom bar**, on top of the per-palette screenshots. Without it, the visual distinction between the three apps is never proven on screen — every screenshot would show `Contacts`, and that is precisely the central promise of the design system.

- [ ] **Step 5: Commit**

```bash
git add apps/catalog
git commit -m "feat(catalog): cross-app bar, palette picker, system theme"
```

## Final verification of the plan

1. `.\gradlew.bat test` — the 24 tests green (10 `:core:model`, 11 `:core:design`, 3 `:apps:catalog`), with no device connected
2. `.\gradlew.bat :apps:catalog:check` — strict lint clean and the proprietary dependency check green
3. `.\gradlew.bat :apps:catalog:assembleDebug` — APK produced
4. APK installed on the Pixel 9: bottom bar with the three icons, theme following the system with no switch, the four palettes really changing the accent, and the three apps visually distinct with the same palette — proven by at least one screenshot with `Téléphone` or `Messages` selected
5. The dependency check really fails when a `com.google.android.gms` artifact is injected by init script (task 7, step 5). A guard that cannot fail guarantees nothing

## Deliberate departure from the spec

The spec lists five shared components in `:core:design`; this plan delivers three. **The search bar and the action sheets are deferred to the Seca Contacts plan**, where they will have a real use. Building a search bar with nothing to search would produce a guessed API rather than one derived from a need. They will stay in `:core:design` — only the moment they are created changes.

## Next

Once this plan is executed and the visual result approved, the next plan covers **Seca Contacts**: `:core:contacts` on top of `ContactsContract`, a list with fast scrolling and search, contact card, creation/editing, favorites, groups, vCard import/export, quick actions, handling of GrapheneOS Contact Scopes, and the guard test forbidding the `INTERNET` permission.

### To handle when the Seca Contacts plan opens

From the final review of this branch, and carried here on purpose: the execution workshop that recorded them is deleted at the end of the plan, and only this file keeps them.

1. **Robolectric on API 36.** Unit tests are pinned to `sdk=34` in the `robolectric.properties` files, because Robolectric's Android 36 sandbox requires JDK 21 while AGP 9.4 pins the project to JDK 17. No effect on theme tests; a false sense of safety for `ContactsContract` and runtime permission tests, where behavior differs between API 34 and 36. Identified fix: run the Gradle daemon on JDK 21 while keeping `jvmToolchain(17)` — AGP's JDK requirement is a minimum, not an exact version. **To do before writing those tests.**
2. **Material 3 Expressive APIs.** None is used yet: everything the foundation consumes exists in a stable `material3`. Pinning `1.5.0-alpha27` stays justified by the Expressive components expected in Seca Contacts — the search bar and the action sheets, deferred here. If that plan ends up using none, go back to a stable version.
3. **Purity of `:core:model`.** A pure Kotlin module by convention only: it is built by AGP, so `android.jar` is on its classpath and nothing prevents importing an Android API into it. Move to `org.jetbrains.kotlin.jvm` before `:core:contacts` appears next to it.
4. **Tonal variety.** `secondary` and `tertiary` equal `primary` in every identity. To revisit as soon as a component asks for variety — a floating action button, a badge.
5. **Launcher icons of the three apps.** They will follow the reasoning kept for the catalog — a system resource, outside the Compose theme — but their color will have to derive from each identity's accent.
6. **Inherited test gaps**: `.uppercase()` on the initials never exercised; the avatar not asserted in `SecaContactRow`; no palette chip click tested in the catalog; the "re-themes the screen" test does not check a color change; the palette distinction tests only compare set sizes and would pass for hues 1° apart.
7. **Accessibility.** `Modifier.clickable` merges the semantics of `SecaContactRow`: TalkBack will announce initials, name and number as a single node. To handle at the level of the Seca Contacts screens.
8. **License.** The README declares GPL-3.0-or-later without the project owner having chosen it, and no `LICENSE` file exists. F-Droid requires one.
