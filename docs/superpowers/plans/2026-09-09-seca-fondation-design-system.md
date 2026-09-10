# Seca — Fondation et Design System — Plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Monter le monorepo Gradle, le module `:core:design` en Material 3 Expressive, et une app catalogue installable qui donne à voir l'intégralité du langage visuel avant d'écrire la première app réelle.

**Architecture:** Monorepo Gradle multi-modules. `build-logic` porte des convention plugins pour que les trois futures apps partagent exactement la même configuration. `:core:model` contient les types domaine purs (aucune dépendance Android). `:core:design` contient **tout** le visuel — couleurs, typographie, formes, motion, composants — et est le seul module autorisé à activer les APIs expérimentales M3 Expressive. `apps/catalog` est une app de debug qui affiche le design system ; elle est jetable et ne sera jamais publiée.

**Tech Stack:** Kotlin 2.4.20, Jetpack Compose 1.12.0, Material 3 `1.5.0-alpha27` (APIs Expressive), AGP 9.4.0, Gradle 9.7.1, JDK 17, Robolectric 4.16.1.

**Spec:** `docs/superpowers/specs/2026-09-09-seca-suite-design.md`

## Global Constraints

Ces contraintes s'appliquent à **toutes** les tâches, implicitement.

- **JDK 17 exactement.** AGP 9.4 l'indique en min *et* en défaut. Le JDK 25 présent sur la machine ne convient pas.
- **Gradle 9.7.1** via wrapper uniquement. Jamais d'installation système.
- `compileSdk = 37`, `buildToolsVersion = "36.0.0"`, `minSdk = 34`, `targetSdk = 36`. **`compileSdk 37` est obligatoire, pas préférentiel** : `material3:1.5.0-alpha27` *et* Compose `ui`/`foundation` 1.12.0 déclarent tous `minCompileSdk=37` dans leurs métadonnées AAR. Rien de ce plan ne compile en 36. `targetSdk` reste à 36 : c'est le niveau contre lequel on teste, et il est indépendant de `compileSdk`.
- **Aucun Compose BOM.** Le BOM `2026.08.00` épingle `material3` sur `1.4.0`, qui ne contient pas les APIs Expressive. Toutes les versions sont épinglées explicitement dans `gradle/libs.versions.toml`.
- **Zéro dépendance propriétaire.** Pas de Play Services, pas de Firebase, pas d'analytique, pas de crash reporting. Contrainte F-Droid.
- **`@OptIn(ExperimentalMaterial3ExpressiveApi::class)` uniquement dans `:core:design`.** Aucun module d'app ne l'active.
- **Aucun module d'app ne définit de couleur, forme ou typographie.** Tout vient de `:core:design`.
- Builds reproductibles : versions épinglées, pas de plage de versions, pas de `latest.release`.
- Le code, les commentaires et les identifiants sont en anglais. L'interface utilisateur et la documentation sont en français.

---

### Task 1: Toolchain

Pas de TDD ici — c'est de la mise en place d'environnement. La tâche est terminée quand les commandes de vérification passent.

**Files:**
- Aucun fichier du dépôt modifié.

**Interfaces:**
- Consumes: rien.
- Produces: `java` en 17, `sdkmanager`, `adb` disponibles ; variable `ANDROID_HOME` définie.

- [ ] **Step 1: Installer le JDK 17**

Temurin 17 s'installe à côté du JDK 25 sans le remplacer.

```powershell
winget install --id EclipseAdoptium.Temurin.17.JDK -e
```

- [ ] **Step 2: Vérifier que le JDK 17 est présent**

```powershell
Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory | Select-Object Name
```

Expected: un dossier `jdk-17.*-hotspot` apparaît. Noter son chemin exact — il sert à l'étape suivante.

- [ ] **Step 3: Installer les outils en ligne de commande du SDK Android**

```powershell
winget install --id Google.AndroidStudio -e
```

Android Studio embarque le SDK et `sdkmanager`. Si l'IDE n'est pas souhaité, télécharger uniquement `commandlinetools-win` depuis https://developer.android.com/studio#command-line-tools-only et le décompresser dans `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest`.

- [ ] **Step 4: Définir ANDROID_HOME**

```powershell
[Environment]::SetEnvironmentVariable("ANDROID_HOME", "$env:LOCALAPPDATA\Android\Sdk", "User")
```

- [ ] **Step 5: Installer les paquets SDK requis**

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat" --install "platform-tools" "platforms;android-37.0" "build-tools;36.0.0"
```

- [ ] **Step 6: Vérifier la toolchain complète**

Le numéro de patch du JDK dépend de ce que winget a installé — le résoudre plutôt que de le supposer.

```powershell
$jdk17 = (Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -Filter "jdk-17*").FullName
& "$jdk17\bin\java.exe" -version
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" version
```

Expected: `openjdk version "17.*"` et une version d'adb.

- [ ] **Step 7: Vérifier la connexion au Pixel 9 (optionnel mais recommandé maintenant)**

Activer le débogage USB sur le Pixel 9 (Paramètres → À propos → appuyer 7 fois sur le numéro de build, puis Options pour développeurs → Débogage USB), brancher le câble, accepter l'invite sur le téléphone.

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices -l
```

Expected: l'appareil apparaît avec l'état `device`. **S'il n'apparaît pas**, la vérification visuelle de la tâche 8 sera impossible — le régler maintenant, pas plus tard.

- [ ] **Step 8: Relever le niveau d'API réel de l'appareil**

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell getprop ro.build.version.sdk
```

Noter la valeur. **Relevé le 2026-09-09 sur le Pixel 9 : `37` (Android 17).** La plateforme à installer est `platforms;android-37.0` — `platforms;android-37` n'existe pas sous ce nom, le SDK étant passé aux versions mineures. `android-37.0` est publiée et non préversion (`PreviewSdkInt=0`, `BetaVersion` vide).

---

### Task 2: Squelette du monorepo Gradle

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
- Delete: les dossiers vides `Seca Contacts/`, `Seca Messages/`, `Seca Phone/`

**Interfaces:**
- Consumes: la toolchain de la tâche 1.
- Produces: les plugins `seca.android.library`, `seca.android.application`, `seca.compose` ; le version catalog `libs`.

- [ ] **Step 1: Supprimer les dossiers vides à espaces**

Les espaces dans les chemins fragilisent Gradle et les recettes F-Droid. Ces dossiers sont vides, rien n'est perdu.

```powershell
Remove-Item "Seca Contacts","Seca Messages","Seca Phone" -Recurse -Force
```

- [ ] **Step 2: Créer le wrapper Gradle 9.7.1**

Sans Gradle installé, générer le wrapper depuis la distribution téléchargée une seule fois :

```powershell
Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-9.7.1-bin.zip" -OutFile "$env:TEMP\gradle-9.7.1-bin.zip"
Expand-Archive "$env:TEMP\gradle-9.7.1-bin.zip" -DestinationPath "$env:TEMP\gradle-dist" -Force
& "$env:TEMP\gradle-dist\gradle-9.7.1\bin\gradle.bat" wrapper --gradle-version 9.7.1 --distribution-type bin
```

- [ ] **Step 3: Écrire `.gitattributes`**

Git a déjà signalé une conversion LF→CRLF. Normaliser avant que ça pollue les diffs.

```
* text=auto eol=lf
*.bat text eol=crlf
*.jar binary
gradlew text eol=lf
```

- [ ] **Step 4: Écrire `.gitignore`**

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

- [ ] **Step 5: Écrire `gradle/libs.versions.toml`**

Toutes les versions sont réelles, relevées sur Google Maven et Maven Central le 2026-09-09.

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

- [ ] **Step 6: Écrire `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx4g -XX:+UseParallelGC
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
android.useAndroidX=true
android.nonTransitiveRClass=true
```

- [ ] **Step 7: Écrire `build-logic/settings.gradle.kts`**

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

- [ ] **Step 8: Écrire `build-logic/build.gradle.kts`**

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

Les coordonnées sont écrites explicitement plutôt que dérivées des alias de plugins : c'est plus lisible et ça évite un helper fragile dans un script de build.

- [ ] **Step 9: Écrire le convention plugin `seca.android.library`**

`build-logic/src/main/kotlin/seca.android.library.gradle.kts` :

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

`unitTests.isIncludeAndroidResources = true` est indispensable : c'est ce qui permet aux tests Compose de tourner sous Robolectric, donc **sans appareil**.

- [ ] **Step 10: Écrire le convention plugin `seca.android.application`**

`build-logic/src/main/kotlin/seca.android.application.gradle.kts` :

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

`dependenciesInfo.includeInApk = false` retire le blob de métadonnées signé par Google de l'APK — requis pour des builds reproductibles et vérifiables par F-Droid.

- [ ] **Step 11: Écrire le convention plugin `seca.compose`**

`build-logic/src/main/kotlin/seca.compose.gradle.kts` :

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

Ce plugin n'active délibérément **pas** `buildFeatures.compose`. Le faire depuis un script plugin précompilé imposerait de nommer `CommonExtension` avec sa liste de paramètres génériques, qui change d'une version d'AGP à l'autre et casse silencieusement. Chaque module Compose l'active lui-même sur une ligne — c'est explicite et stable. `:core:model` n'en a pas besoin.

`libs` est récupéré via `VersionCatalogsExtension` : l'accesseur `libs` généré n'existe pas dans un script plugin précompilé.

- [ ] **Step 12: Écrire `settings.gradle.kts`**

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

- [ ] **Step 13: Créer `local.properties` avec le chemin du SDK**

```powershell
"sdk.dir=$($env:LOCALAPPDATA -replace '\\','\\')\\Android\\Sdk" | Out-File -Encoding utf8 local.properties
```

Ce fichier est ignoré par git — il est propre à la machine.

- [ ] **Step 14: Créer les build files minimaux des trois modules**

`core/model/build.gradle.kts` :

```kotlin
plugins { id("seca.android.library") }
android { namespace = "com.seca.core.model" }
dependencies {
    testImplementation(libs.junit)
}
```

`core/design/build.gradle.kts` :

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

`apps/catalog/build.gradle.kts` :

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

`material3` est déclaré en `api` dans `:core:design` : les modules consommateurs y ont accès sans le redéclarer, ce qui garantit qu'une seule version circule.

- [ ] **Step 15: Vérifier que la configuration Gradle est valide**

```powershell
.\gradlew.bat projects --no-daemon
```

Expected: l'arborescence liste `:core:model`, `:core:design`, `:apps:catalog` sans erreur. C'est le premier vrai signal que la toolchain, les convention plugins et le catalog fonctionnent ensemble.

- [ ] **Step 16: Commit**

```bash
git add -A
git commit -m "build: monorepo Gradle, version catalog et convention plugins"
```

---

### Task 3: `:core:model` — types domaine

Module Kotlin pur, sans dépendance Android. C'est le vocabulaire partagé par les trois futures apps.

**Files:**
- Create: `core/model/src/main/kotlin/com/seca/core/model/PhoneNumber.kt`
- Create: `core/model/src/main/kotlin/com/seca/core/model/SecaContact.kt`
- Test: `core/model/src/test/kotlin/com/seca/core/model/PhoneNumberTest.kt`
- Test: `core/model/src/test/kotlin/com/seca/core/model/SecaContactTest.kt`

**Interfaces:**
- Consumes: rien.
- Produces:
  - `PhoneNumber(raw: String)` avec `val digits: String` et `fun matches(other: PhoneNumber): Boolean`
  - `SecaContact(id: Long, displayName: String, phoneNumbers: List<PhoneNumber>, isFavorite: Boolean, photoUri: String?)` avec `val initials: String`

- [ ] **Step 1: Écrire le test qui échoue pour `PhoneNumber`**

`core/model/src/test/kotlin/com/seca/core/model/PhoneNumberTest.kt` :

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

La comparaison sur les neuf derniers chiffres est la manière habituelle de rapprocher un format international d'un format national sans embarquer de bibliothèque de numérotation. Le seuil protège contre les faux positifs sur les numéros courts.

- [ ] **Step 2: Lancer le test et vérifier qu'il échoue**

```powershell
.\gradlew.bat :core:model:testDebugUnitTest --tests "*PhoneNumberTest*"
```

Expected: échec de compilation, `PhoneNumber` n'existe pas.

- [ ] **Step 3: Implémenter `PhoneNumber`**

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

- [ ] **Step 4: Lancer le test et vérifier qu'il passe**

```powershell
.\gradlew.bat :core:model:testDebugUnitTest --tests "*PhoneNumberTest*"
```

Expected: PASS, 6 tests.

- [ ] **Step 5: Écrire le test qui échoue pour `SecaContact`**

`core/model/src/test/kotlin/com/seca/core/model/SecaContactTest.kt` :

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

- [ ] **Step 6: Lancer le test et vérifier qu'il échoue**

```powershell
.\gradlew.bat :core:model:testDebugUnitTest --tests "*SecaContactTest*"
```

Expected: échec de compilation, `SecaContact` n'existe pas.

- [ ] **Step 7: Implémenter `SecaContact`**

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

- [ ] **Step 8: Lancer tous les tests du module**

```powershell
.\gradlew.bat :core:model:test
```

Expected: PASS, 10 tests.

- [ ] **Step 9: Commit**

```bash
git add core/model
git commit -m "feat(model): types domaine PhoneNumber et SecaContact"
```

---

### Task 4: `:core:design` — le thème Seca

Typographie, formes, motion et couleurs forment un seul thème. Ils sont livrés dans la même tâche parce que `SecaTheme` ne compile qu'une fois les quatre présents, et parce qu'un relecteur ne pourrait pas approuver la typographie en rejetant la palette : c'est une seule décision de design.

Les palettes sont écrites à la main plutôt que dérivées d'une graine par une bibliothèque : c'est déterministe, ça évite une dépendance, et ça donne un vrai contrôle sur le rendu.

**Files:**
- Create: `core/design/src/main/kotlin/com/seca/core/design/SecaTypography.kt`
- Create: `core/design/src/main/kotlin/com/seca/core/design/SecaShapes.kt`
- Create: `core/design/src/main/kotlin/com/seca/core/design/SecaMotion.kt`
- Create: `core/design/src/main/kotlin/com/seca/core/design/SecaAppIdentity.kt`
- Create: `core/design/src/main/kotlin/com/seca/core/design/color/SecaPalettes.kt`
- Create: `core/design/src/main/kotlin/com/seca/core/design/SecaTheme.kt`
- Test: `core/design/src/test/kotlin/com/seca/core/design/SecaThemeTest.kt`

**Interfaces:**
- Consumes: rien.
- Produces:
  - `enum class SecaAppIdentity { Contacts, Phone, Messages }`
  - `val SecaTypography: Typography` et `val SecaShapes: Shapes`
  - `object SecaMotion` avec `emphasized()`, `standard()` et `expressiveSpring()`
  - `@Composable fun SecaTheme(identity: SecaAppIdentity, darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = false, content: @Composable () -> Unit)`

- [ ] **Step 1: Écrire la typographie**

`core/design/src/main/kotlin/com/seca/core/design/SecaTypography.kt` :

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

- [ ] **Step 2: Écrire les formes**

`core/design/src/main/kotlin/com/seca/core/design/SecaShapes.kt` :

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

- [ ] **Step 3: Écrire les spécifications de motion**

`core/design/src/main/kotlin/com/seca/core/design/SecaMotion.kt` :

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

- [ ] **Step 4: Écrire `SecaAppIdentity`**

`core/design/src/main/kotlin/com/seca/core/design/SecaAppIdentity.kt` :

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

- [ ] **Step 5: Écrire les palettes**

`core/design/src/main/kotlin/com/seca/core/design/color/SecaPalettes.kt`. Trois accents distincts sur une base neutre commune — teal pour Contacts, indigo pour Phone, violet pour Messages.

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

- [ ] **Step 6: Écrire le test qui échoue pour le thème**

`core/design/src/test/kotlin/com/seca/core/design/SecaThemeTest.kt` :

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

- [ ] **Step 7: Lancer le test et vérifier qu'il échoue**

```powershell
.\gradlew.bat :core:design:testDebugUnitTest --tests "*SecaThemeTest*"
```

Expected: échec de compilation, `SecaTheme` n'existe pas.

- [ ] **Step 8: Implémenter `SecaTheme`**

`core/design/src/main/kotlin/com/seca/core/design/SecaTheme.kt` :

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

- [ ] **Step 9: Lancer le test et vérifier qu'il passe**

```powershell
.\gradlew.bat :core:design:testDebugUnitTest --tests "*SecaThemeTest*"
```

Expected: PASS, 4 tests. Confirme aussi que les tests Compose tournent sous Robolectric, sans appareil connecté.

- [ ] **Step 10: Commit**

```bash
git add core/design
git commit -m "feat(design): thème Seca — typographie, formes, motion et couleurs"
```

---

### Task 5: `:core:design` — composants partagés

Les briques réutilisées par les trois apps. Chacune est testée sous Robolectric.

**Files:**
- Create: `core/design/src/main/kotlin/com/seca/core/design/component/SecaAvatar.kt`
- Create: `core/design/src/main/kotlin/com/seca/core/design/component/SecaContactRow.kt`
- Create: `core/design/src/main/kotlin/com/seca/core/design/component/SecaEmptyState.kt`
- Test: `core/design/src/test/kotlin/com/seca/core/design/component/SecaAvatarTest.kt`
- Test: `core/design/src/test/kotlin/com/seca/core/design/component/SecaContactRowTest.kt`
- Modify: `core/design/build.gradle.kts` — ajouter la dépendance `:core:model`

**Interfaces:**
- Consumes: `SecaContact`, `SecaTheme`, `SecaMotion` des tâches 3 et 4.
- Produces:
  - `@Composable fun SecaAvatar(initials: String, photoUri: String?, modifier: Modifier = Modifier, size: Dp = 48.dp)`
  - `@Composable fun SecaContactRow(contact: SecaContact, onClick: () -> Unit, modifier: Modifier = Modifier)`
  - `@Composable fun SecaEmptyState(title: String, description: String, modifier: Modifier = Modifier)`

- [ ] **Step 1: Ajouter la dépendance `:core:model`**

Dans `core/design/build.gradle.kts`, section `dependencies` :

```kotlin
    api(project(":core:model"))
```

- [ ] **Step 2: Écrire le test qui échoue pour `SecaAvatar`**

`core/design/src/test/kotlin/com/seca/core/design/component/SecaAvatarTest.kt` :

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

- [ ] **Step 3: Lancer le test et vérifier qu'il échoue**

```powershell
.\gradlew.bat :core:design:testDebugUnitTest --tests "*SecaAvatarTest*"
```

Expected: échec de compilation, `SecaAvatar` n'existe pas.

- [ ] **Step 4: Implémenter `SecaAvatar`**

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

- [ ] **Step 5: Lancer le test et vérifier qu'il passe**

```powershell
.\gradlew.bat :core:design:testDebugUnitTest --tests "*SecaAvatarTest*"
```

Expected: PASS.

- [ ] **Step 6: Écrire le test qui échoue pour `SecaContactRow`**

`core/design/src/test/kotlin/com/seca/core/design/component/SecaContactRowTest.kt` :

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

- [ ] **Step 7: Lancer le test et vérifier qu'il échoue**

```powershell
.\gradlew.bat :core:design:testDebugUnitTest --tests "*SecaContactRowTest*"
```

Expected: échec de compilation, `SecaContactRow` n'existe pas.

- [ ] **Step 8: Implémenter `SecaContactRow`**

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

- [ ] **Step 9: Implémenter `SecaEmptyState`**

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

- [ ] **Step 10: Lancer tous les tests du module**

```powershell
.\gradlew.bat :core:design:test
```

Expected: PASS, 8 tests au total (SecaThemeTest 4, SecaAvatarTest 1, SecaContactRowTest 3).

- [ ] **Step 11: Commit**

```bash
git add core/design
git commit -m "feat(design): composants avatar, ligne de contact et état vide"
```

---

### Task 6: `apps/catalog` — galerie du design system

L'app qui rend le design jugeable. Elle s'installe sur le Pixel 9 et montre tout : les trois identités, clair/sombre, couleur dynamique.

**Files:**
- Create: `apps/catalog/src/main/AndroidManifest.xml`
- Create: `apps/catalog/src/main/kotlin/com/seca/catalog/MainActivity.kt`
- Create: `apps/catalog/src/main/kotlin/com/seca/catalog/CatalogScreen.kt`
- Test: `apps/catalog/src/test/kotlin/com/seca/catalog/CatalogScreenTest.kt`

**Interfaces:**
- Consumes: tout `:core:design`.
- Produces: un APK installable `com.seca.catalog`. Aucun autre module ne dépend de celui-ci.

- [ ] **Step 1: Écrire le manifeste**

`apps/catalog/src/main/AndroidManifest.xml`. Aucune permission — la galerie n'en a besoin d'aucune.

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

- [ ] **Step 2: Écrire le thème de démarrage**

`apps/catalog/src/main/res/values/themes.xml`. Un thème système minimal, le temps que Compose prenne la main.

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.SecaCatalog" parent="android:Theme.Material.NoActionBar">
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
    </style>
</resources>
```

Le parent est un thème **de la plateforme**, pas `com.google.android.material` : aucune dépendance supplémentaire n'entre dans l'APK.

- [ ] **Step 3: Écrire le test qui échoue pour l'écran catalogue**

`apps/catalog/src/test/kotlin/com/seca/catalog/CatalogScreenTest.kt` :

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

- [ ] **Step 4: Lancer le test et vérifier qu'il échoue**

```powershell
.\gradlew.bat :apps:catalog:test
```

Expected: échec de compilation, `CatalogScreen` n'existe pas.

- [ ] **Step 5: Implémenter `CatalogScreen`**

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

- [ ] **Step 6: Implémenter `MainActivity`**

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

- [ ] **Step 7: Lancer les tests et vérifier qu'ils passent**

```powershell
.\gradlew.bat :apps:catalog:test
```

Expected: PASS, 2 tests.

- [ ] **Step 8: Construire l'APK**

```powershell
.\gradlew.bat :apps:catalog:assembleDebug
```

Expected: BUILD SUCCESSFUL, APK dans `apps/catalog/build/outputs/apk/debug/`.

- [ ] **Step 9: Installer sur le Pixel 9 et regarder**

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r apps\catalog\build\outputs\apk\debug\catalog-debug.apk
```

Ouvrir l'app et **juger le résultat** : basculer entre les trois identités, activer sombre, activer la couleur dynamique. C'est le point de contrôle du « très très joli ». Si le rendu ne convient pas, c'est ici qu'on ajuste palettes, formes et typographie — avant d'écrire quoi que ce soit de fonctionnel.

- [ ] **Step 10: Commit**

```bash
git add apps/catalog
git commit -m "feat(catalog): galerie du design system"
```

---

### Task 7: Garde-fous et documentation

Verrouille les propriétés que la spec revendique pour qu'elles ne régressent pas en silence : aucune dépendance propriétaire dans ce qui part dans l'APK, et un lint qui échoue au lieu d'avertir.

> **Réécrite le 2026-09-10.** La première version prescrivait un test unitaire qui cherchait `com.google.android.gms` dans `System.getProperty("java.class.path")`. Lancé avec `play-services-base:18.3.0` réellement présent sur le classpath de test, il est **passé**. Les AAR sont transformés avant d'atteindre un classpath (`caches/<gradle>/transforms/<hash>/transformed/play-services-base-18.3.0/jars/classes.jar`) et le groupe Maven n'apparaît nulle part dans ce chemin : ce test ne pouvait pas échouer. Il n'inspectait en outre que le classpath de test de `:core:design`, pas ce qui part dans un APK. Il est remplacé par une vérification des coordonnées Maven résolues du classpath *release* de chaque app.

**Files:**
- Create: `build-logic/src/main/kotlin/com/seca/buildlogic/VerifyNoProprietaryDependencies.kt`
- Modify: `build-logic/src/main/kotlin/seca.android.application.gradle.kts` — enregistrer la vérification, activer lint en échec
- Create: `README.md`

**Interfaces:**
- Consumes: le convention plugin `seca.android.application` de la tâche 2.
- Produces: dans chaque module d'app, une tâche `verifyReleaseNoProprietaryDependencies` branchée sur `check`. Aucune API Kotlin.

- [ ] **Step 1: Écrire la tâche de vérification**

`build-logic/src/main/kotlin/com/seca/buildlogic/VerifyNoProprietaryDependencies.kt` :

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
                "Dépendances propriétaires sur le classpath d'exécution :\n" +
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

Le filtre compare le groupe entier ou un sous-groupe (`"$it."`), jamais un simple préfixe de chaîne : `com.google.android.material`, qui est libre, ne doit pas être pris pour `com.google.android.play`.

- [ ] **Step 2: Enregistrer la vérification dans le convention plugin d'application**

Dans `build-logic/src/main/kotlin/seca.android.application.gradle.kts` — **lire d'abord le fichier tel qu'il est** ; ne rien retirer de l'existant.

Ajouter en tête, à côté de l'import existant :

```kotlin
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.seca.buildlogic.VerifyNoProprietaryDependencies
```

Ajouter à la fin du fichier :

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

`rootComponent` est un `Provider` paresseux, résolu à l'exécution de la tâche. L'action ne touche jamais `project` ni `configurations` : c'est ce qui la rend compatible avec le cache de configuration, activé dans ce dépôt.

- [ ] **Step 3: Activer lint en échec**

Dans le même fichier, à l'intérieur du bloc `extensions.configure<ApplicationExtension>` existant :

```kotlin
    lint {
        warningsAsErrors = true
        abortOnError = true
        // Pinned versions are the point of a reproducible build; this check
        // would otherwise fire on every dependency the moment a newer one ships.
        disable += setOf("GradleDependency")
    }
```

- [ ] **Step 4: Vérifier que la tâche passe sur l'état réel**

```powershell
.\gradlew.bat :apps:catalog:verifyReleaseNoProprietaryDependencies
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Prouver qu'elle échoue quand elle doit — sans toucher un seul fichier suivi**

Un garde-fou qui ne peut pas échouer ne garantit rien : c'est exactement le défaut de la première version. La preuve se fait par un init script temporaire, **hors du dépôt**, qui injecte l'intrus le temps d'une seule invocation. Aucun fichier suivi par git n'est jamais modifié : si l'exécution est interrompue en route, il n'y a rien à reverter.

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

# 1. L'intrus est bien là.
.\gradlew.bat :apps:catalog:dependencies --configuration releaseRuntimeClasspath --init-script $init | Select-String "com.google.android.gms"

# 2. Et la vérification échoue.
.\gradlew.bat :apps:catalog:verifyReleaseNoProprietaryDependencies --init-script $init

Remove-Item $init
git status --porcelain
```

Expected :
1. la première commande liste `com.google.android.gms:play-services-base:18.3.0` ;
2. la seconde se termine en **BUILD FAILED**, avec un message qui cite au moins `com.google.android.gms:play-services-base:18.3.0` ;
3. `git status --porcelain` ne montre aucune modification liée à la preuve.

**Si la seconde commande passe, s'arrêter** : soit l'injection n'a pas pris, soit la vérification est fausse. Dans les deux cas, le garde-fou n'est pas démontré et ne doit pas être committé comme tel.

- [ ] **Step 6: Lancer lint**

```powershell
.\gradlew.bat :apps:catalog:lint
```

Expected: BUILD SUCCESSFUL. Corriger ce qui est signalé dans `apps/catalog`. Si lint signale quelque chose dans `core/` ou dans `build-logic/`, s'arrêter et le rapporter : ce serait un constat sur la fondation, pas une correction à glisser en fin de tâche.

- [ ] **Step 7: Écrire le README**

`README.md` :

````markdown
# Seca

Trois applications de communication pour Android, conçues pour GrapheneOS :
**Seca Contacts**, **Seca Phone** et **Seca Messages**. Un design Material 3
Expressive commun, orienté vie privée, sans aucune dépendance Google.

## État

Fondation et design system en place, avec une application catalogue qui les
présente. Seca Contacts est la prochaine étape. Voir `docs/superpowers/specs/`
pour la conception et `docs/superpowers/plans/` pour les plans d'implémentation.

## Trois applications, une seule famille

Les trois apps restent des APK distincts. Chacune affiche en bas une barre qui
mène aux deux autres ; elles se lancent entre elles par intent. Garder trois APK
séparés préserve la séparation des permissions : Seca Contacts n'a jamais besoin
d'`INTERNET`, Seca Phone détient `ROLE_DIALER`, Seca Messages `ROLE_SMS`.

Le thème clair ou sombre suit celui du système, sans réglage dans l'app. La
seule préférence visuelle est la palette — Océan, Forêt, Crépuscule ou Ardoise.
Chaque application décale la teinte de la palette choisie, si bien que les trois
restent reconnaissables quelle que soit la palette.

Material You n'est pas proposé : la couleur dynamique tire toutes les teintes du
fond d'écran et rendait les trois applications identiques.

## Ce que Seca ne fait pas

- **Pas de RCS.** Google réserve son API RCS à une allowlist fermée ;
  aucune application tierce ne peut l'implémenter. Seca Messages proposera
  du SMS/MMS et une couche chiffrée de bout en bout entre utilisateurs Seca.
- **Pas d'implémentation des appels Wi-Fi.** Le VoWiFi relève de la pile IMS
  du système et fonctionne déjà indépendamment du composeur installé.
  Seca Phone en affichera l'état.

## Construire

Prérequis : JDK 17 et le SDK Android (plateforme `android-37.0`, build-tools 36.0.0).

```bash
./gradlew :apps:catalog:assembleDebug
./gradlew test
./gradlew :apps:catalog:check
```

## Vie privée

Aucune analytique, aucun rapport de plantage, aucune dépendance Google. Chaque
application vérifie à la construction qu'aucun artefact `com.google.android.gms`,
`com.google.firebase` ou `com.google.android.play` n'atteint son classpath
d'exécution : la tâche `verifyReleaseNoProprietaryDependencies`, branchée sur
`check`, fait échouer le build dans le cas contraire.

Seca Contacts ne demandera **pas** la permission `INTERNET` : l'application
sera structurellement incapable d'exfiltrer un répertoire.

Les trois icônes de la barre sont dessinées dans le dépôt, en `ImageVector`.
Les bibliothèques `material-icons-core` et `material-icons-extended` de Google
sont figées en 1.7.8 alors que le projet utilise Compose 1.12.0 : trois icônes
ne justifiaient pas d'embarquer une dépendance abandonnée.

## Licence

GPL-3.0-or-later.
````

- [ ] **Step 8: Suite complète**

```powershell
.\gradlew.bat test
.\gradlew.bat :apps:catalog:check
```

Expected : `test` au vert avec **24 tests** — 10 `:core:model`, 11 `:core:design`, 3 `:apps:catalog` (le test unitaire de la première version n'existe plus) ; `check` au vert, ce qui couvre lint et la vérification des dépendances.

- [ ] **Step 9: Commit**

Ajouter chaque fichier par son chemin. **Jamais `git add -A` ni `git add .`** : un fichier orphelin laissé par une exécution interrompue — y compris une dépendance propriétaire temporaire — entrerait dans l'historique.

```bash
git add build-logic/src/main/kotlin/com/seca/buildlogic/VerifyNoProprietaryDependencies.kt \
        build-logic/src/main/kotlin/seca.android.application.gradle.kts \
        README.md
git commit -m "chore: garde-fou de dépendances au niveau Gradle, lint strict et README"
```

Si les corrections de lint ont touché des fichiers de `apps/catalog`, les ajouter eux aussi, nommément.

---

### Task 8: `:core:design` — palettes au choix, thème système

Remplace Material You par une petite palette que l'utilisateur choisit, dont chaque app dérive une variante distincte. Le thème clair/sombre suit le système et n'est plus réglable dans l'app.

Les accents ne sont plus écrits à la main un par un : chaque palette porte une teinte de base, chaque identité applique un décalage de teinte fixe, et les rôles se dérivent par des recettes HSL constantes. Compose fournit déjà `Color.hsl()` — aucune conversion colorimétrique à écrire. Ça donne des familles cohérentes, 4 palettes × 3 identités × 2 thèmes sans authoring manuel de 24 quadruplets, et c'est testable.

**Files:**
- Create: `core/design/src/main/kotlin/com/seca/core/design/SecaPalette.kt`
- Modify: `core/design/src/main/kotlin/com/seca/core/design/color/SecaPalettes.kt` (réécriture des dérivations)
- Modify: `core/design/src/main/kotlin/com/seca/core/design/SecaTheme.kt` (signature)
- Modify: `core/design/src/test/kotlin/com/seca/core/design/SecaThemeTest.kt`

**Interfaces:**
- Consumes: `SecaAppIdentity`, `SecaTypography`, `SecaShapes`.
- Produces:
  - `enum class SecaPalette(val label: String)` — `Ocean`, `Foret`, `Crepuscule`, `Ardoise`
  - `@Composable fun SecaTheme(identity: SecaAppIdentity, palette: SecaPalette = SecaPalette.Ocean, darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)`
  - **`dynamicColor` disparaît.** Tout appelant qui le passait doit être mis à jour.

- [ ] **Step 1: Écrire `SecaPalette`**

`core/design/src/main/kotlin/com/seca/core/design/SecaPalette.kt` :

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

- [ ] **Step 2: Réécrire les dérivations de couleur**

`core/design/src/main/kotlin/com/seca/core/design/color/SecaPalettes.kt` — remplace intégralement le contenu :

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

- [ ] **Step 3: Écrire les tests qui échouent**

Remplace `core/design/src/test/kotlin/com/seca/core/design/SecaThemeTest.kt` :

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

- [ ] **Step 4: Lancer et vérifier l'échec**

```powershell
.\gradlew.bat :core:design:testDebugUnitTest --tests "*SecaThemeTest*"
```

Expected: échec de compilation — `SecaPalette` n'existe pas et `SecaTheme` ne prend pas encore ce paramètre.

- [ ] **Step 5: Réécrire `SecaTheme`**

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

- [ ] **Step 6: Mettre à jour les appelants existants**

`SecaAvatarTest` et `SecaContactRowTest` passent `dynamicColor = false`, qui n'existe plus. Retirer cet argument des trois appels — ne rien changer d'autre à ces tests.

- [ ] **Step 7: Lancer la suite du module**

```powershell
.\gradlew.bat :core:design:test
```

Expected: PASS, 9 tests (SecaThemeTest 5, SecaAvatarTest 1, SecaContactRowTest 3).

- [ ] **Step 8: Commit**

```bash
git add core/design
git commit -m "feat(design): palettes au choix, thème suivant le système"
```

---

### Task 9: `:core:design` — icônes et barre inter-apps

Trois icônes dessinées à la main et une barre de navigation basse partagée par les trois apps. `material-icons-core` et `-extended` sont figés en 1.7.8 face à Compose 1.12.0 : bibliothèques mortes, écartées. Trois icônes ne justifient pas une dépendance abandonnée.

**Files:**
- Create: `core/design/src/main/kotlin/com/seca/core/design/SecaIcons.kt`
- Create: `core/design/src/main/kotlin/com/seca/core/design/component/SecaSuiteBar.kt`
- Test: `core/design/src/test/kotlin/com/seca/core/design/component/SecaSuiteBarTest.kt`

**Interfaces:**
- Consumes: `SecaAppIdentity`, `SecaPalette`, `SecaTheme`.
- Produces:
  - `object SecaIcons` avec `Contacts`, `Phone`, `Messages` (`ImageVector`)
  - `@Composable fun SecaSuiteBar(current: SecaAppIdentity, onSelect: (SecaAppIdentity) -> Unit, modifier: Modifier = Modifier)`

`SecaSuiteBar` ne connaît aucun `Intent`. Chaque app décide dans `onSelect` ce qu'elle fait — lancer l'app voisine, ou changer d'aperçu dans le catalogue. `:core:design` reste sans logique de navigation Android.

- [ ] **Step 1: Écrire les icônes**

`core/design/src/main/kotlin/com/seca/core/design/SecaIcons.kt` :

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

- [ ] **Step 2: Écrire le test qui échoue**

`core/design/src/test/kotlin/com/seca/core/design/component/SecaSuiteBarTest.kt` :

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

- [ ] **Step 3: Lancer et vérifier l'échec**

```powershell
.\gradlew.bat :core:design:testDebugUnitTest --tests "*SecaSuiteBarTest*"
```

Expected: échec de compilation, `SecaSuiteBar` n'existe pas.

- [ ] **Step 4: Implémenter `SecaSuiteBar`**

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

`contentDescription = null` sur l'icône est délibéré : le libellé texte juste en dessous porte déjà le nom, et `NavigationBarItem` compose les deux en un seul nœud sémantique. Le décrire deux fois nuirait aux lecteurs d'écran.

- [ ] **Step 5: Lancer et vérifier le succès**

```powershell
.\gradlew.bat :core:design:test
```

Expected: PASS, 11 tests (SecaThemeTest 5, SecaAvatarTest 1, SecaContactRowTest 3, SecaSuiteBarTest 2).

- [ ] **Step 6: Commit**

```bash
git add core/design
git commit -m "feat(design): icônes dessinées à la main et barre inter-apps"
```

---

### Task 10: `apps/catalog` — barre en bas, sélecteur de palette, thème système

Le catalogue adopte la forme définitive : plus aucune bascule de thème, un choix de palette, et les trois identités déplacées dans la barre du bas avec leurs icônes.

**Files:**
- Modify: `apps/catalog/src/main/kotlin/com/seca/catalog/CatalogScreen.kt`
- Modify: `apps/catalog/src/test/kotlin/com/seca/catalog/CatalogScreenTest.kt`

**Interfaces:**
- Consumes: `SecaTheme`, `SecaPalette`, `SecaSuiteBar`, `SecaAvatar`, `SecaContactRow`, `SecaEmptyState`, `SecaMotion`.
- Produces: rien. Aucun module ne dépend du catalogue.

- [ ] **Step 1: Réécrire `CatalogScreen`**

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

`Scaffold` fournit `innerPadding`, qui porte déjà les insets système et la hauteur de la barre du bas. C'est ce qui remplace le `safeDrawingPadding()` précédent et ce qui empêche le contenu de passer sous la barre de navigation.

Le `frenchLabel` privé qui vivait dans ce fichier devient du code mort : les chips d'identité ont disparu, et `SecaSuiteBar` rend ses propres libellés. **Le supprimer.** Il ne peut pas être remplacé par le `SecaAppIdentity.label` de `:core:design`, qui est `internal` et donc invisible depuis un module d'app — mais le catalogue n'en a plus besoin, donc il n'y a rien à partager.

- [ ] **Step 2: Mettre à jour le test**

Dans `apps/catalog/src/test/kotlin/com/seca/catalog/CatalogScreenTest.kt`, ajouter l'import `androidx.compose.ui.test.performClick` et ce troisième cas :

```kotlin
    @Test
    fun `switching app from the suite bar re-themes the screen`() {
        composeRule.setContent { CatalogScreen() }
        composeRule.onNodeWithText("Téléphone").performClick()
        composeRule.onNodeWithText("Camille Durand").assertIsDisplayed()
    }
```

Il vérifie que la barre du bas est réellement câblée et que changer d'app ne casse pas l'écran — ce qu'aucun test ne couvrait.

- [ ] **Step 3: Lancer la suite complète**

```powershell
.\gradlew.bat test
```

Expected: PASS, 24 tests — 10 `:core:model`, 11 `:core:design`, 3 `:apps:catalog`.

- [ ] **Step 4: Installer et vérifier de visu**

```powershell
.\gradlew.bat :apps:catalog:assembleDebug
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r apps\catalog\build\outputs\apk\debug\catalog-debug.apk
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am start -n com.seca.catalog/.MainActivity
```

Réveiller l'appareil avant toute capture (`input keyevent KEYCODE_WAKEUP`), sinon l'image est noire. Contrôler : barre du bas avec les trois icônes, thème suivant le système, quatre palettes qui changent réellement l'accent.

**Capturer au moins une image avec `Téléphone` OU `Messages` sélectionné dans la barre du bas**, en plus des captures par palette. Sans elle, la distinction visuelle entre les trois apps n'est jamais prouvée à l'écran — toutes les captures montreraient `Contacts`, et c'est précisément la promesse centrale du design system.

- [ ] **Step 5: Commit**

```bash
git add apps/catalog
git commit -m "feat(catalog): barre inter-apps, sélecteur de palette, thème système"
```

## Vérification finale du plan

1. `.\gradlew.bat test` — les 24 tests au vert (10 `:core:model`, 11 `:core:design`, 3 `:apps:catalog`), sans appareil connecté
2. `.\gradlew.bat :apps:catalog:check` — lint strict propre et vérification des dépendances propriétaires au vert
3. `.\gradlew.bat :apps:catalog:assembleDebug` — APK produit
4. APK installé sur le Pixel 9 : barre du bas avec les trois icônes, thème suivant le système sans bascule, les quatre palettes changeant réellement l'accent, et les trois apps visuellement distinctes à palette identique — prouvé par au moins une capture avec `Téléphone` ou `Messages` sélectionné
5. La vérification des dépendances échoue réellement quand un artefact `com.google.android.gms` est injecté par init script (tâche 7, step 5). Un garde-fou qui ne peut pas échouer ne garantit rien

## Écart assumé par rapport à la spec

La spec liste cinq composants partagés dans `:core:design` ; ce plan en livre trois. **La barre de recherche et les feuilles d'action sont reportées au plan Seca Contacts**, où elles auront un usage réel. Construire une barre de recherche sans rien à chercher produirait une API devinée plutôt que dérivée d'un besoin. Elles resteront dans `:core:design` — seul leur moment de création change.

## Suite

Une fois ce plan exécuté et le rendu visuel validé, le plan suivant couvre **Seca Contacts** : `:core:contacts` au-dessus de `ContactsContract`, liste avec défilement rapide et recherche, fiche, création/édition, favoris, groupes, import/export vCard, actions rapides, gestion des Contact Scopes de GrapheneOS, et le test de garde interdisant la permission `INTERNET`.

### À traiter en ouverture du plan Seca Contacts

Issus de la relecture finale de cette branche, et reportés ici délibérément : l'atelier d'exécution qui les consignait est supprimé en fin de plan, seul ce fichier les conserve.

1. **Robolectric sur l'API 36.** Les tests unitaires sont épinglés sur `sdk=34` dans les `robolectric.properties`, parce que le bac à sable Android 36 de Robolectric exige JDK 21 alors qu'AGP 9.4 épingle le projet sur JDK 17. Sans effet sur des tests de thème ; faux sentiment de sécurité pour des tests de `ContactsContract` et de permissions runtime, où les comportements diffèrent entre API 34 et 36. Correctif identifié : faire tourner le démon Gradle sur JDK 21 en gardant `jvmToolchain(17)` — l'exigence JDK d'AGP est un minimum, pas une version exacte. **À faire avant d'écrire ces tests.**
2. **APIs Material 3 Expressive.** Aucune n'est encore utilisée : tout ce que la fondation consomme existe dans un `material3` stable. L'épinglage sur `1.5.0-alpha27` reste justifié par les composants Expressive attendus dans Seca Contacts — la barre de recherche et les feuilles d'action, reportées ici. Si ce plan n'en utilise finalement aucun, revenir à une version stable.
3. **Pureté de `:core:model`.** Module Kotlin pur par convention seulement : il est construit par AGP, `android.jar` est donc sur son classpath et rien n'empêche d'y importer une API Android. Passer à `org.jetbrains.kotlin.jvm` avant que `:core:contacts` n'apparaisse à côté.
4. **Variété tonale.** `secondary` et `tertiary` valent `primary` dans chaque identité. À revoir dès qu'un composant demande de la variété — un bouton d'action flottant, un badge.
5. **Icônes de lanceur des trois apps.** Elles suivront le raisonnement retenu pour le catalogue — ressource système, hors thème Compose — mais leur couleur devra dériver de l'accent de chaque identité.
6. **Trous de test hérités** : `.uppercase()` des initiales jamais exercé ; l'avatar non asserté dans `SecaContactRow` ; aucun clic de puce de palette testé dans le catalogue ; le test « re-themes the screen » ne vérifie pas un changement de couleur ; les tests de distinction de palette ne comparent que des tailles d'ensemble et passeraient pour des teintes à 1° d'écart.
7. **Accessibilité.** `Modifier.clickable` fusionne la sémantique de `SecaContactRow` : TalkBack annoncera initiales, nom et numéro en un seul nœud. À traiter au niveau des écrans de Seca Contacts.
8. **Licence.** Le README déclare GPL-3.0-or-later sans que le propriétaire du projet l'ait choisie, et aucun fichier `LICENSE` n'existe. F-Droid en exige un.
