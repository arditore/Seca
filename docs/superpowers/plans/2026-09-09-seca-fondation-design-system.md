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
- `compileSdk = 37`, `buildToolsVersion = "36.0.0"`, `minSdk = 34`, `targetSdk = 36`.
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
& "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat" --install "platform-tools" "platforms;android-37" "build-tools;36.0.0"
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

Noter la valeur. Si elle est inférieure à 36, ajuster `targetSdk` en conséquence à la tâche 2 plutôt que de supposer.

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
.\gradlew.bat :core:model:test --tests "*PhoneNumberTest*"
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
.\gradlew.bat :core:model:test --tests "*PhoneNumberTest*"
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
.\gradlew.bat :core:model:test --tests "*SecaContactTest*"
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
  - `@Composable fun SecaTheme(identity: SecaAppIdentity, darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = true, content: @Composable () -> Unit)`

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

    val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val StandardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

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

private val ContactsAccent = Color(0xFF00696E)
private val ContactsAccentDark = Color(0xFF4FD8E0)
private val PhoneAccent = Color(0xFF3A5BA9)
private val PhoneAccentDark = Color(0xFFB1C5FF)
private val MessagesAccent = Color(0xFF6B4EA8)
private val MessagesAccentDark = Color(0xFFD3BCFF)

private val NeutralSurfaceLight = Color(0xFFF7FAFA)
private val NeutralSurfaceDark = Color(0xFF0E1414)
private val NeutralOnSurfaceLight = Color(0xFF191C1D)
private val NeutralOnSurfaceDark = Color(0xFFE1E3E3)

internal fun lightSchemeFor(identity: SecaAppIdentity): ColorScheme {
    val accent = when (identity) {
        SecaAppIdentity.Contacts -> ContactsAccent
        SecaAppIdentity.Phone -> PhoneAccent
        SecaAppIdentity.Messages -> MessagesAccent
    }
    return lightColorScheme(
        primary = accent,
        onPrimary = Color.White,
        surface = NeutralSurfaceLight,
        onSurface = NeutralOnSurfaceLight,
        background = NeutralSurfaceLight,
        onBackground = NeutralOnSurfaceLight,
    )
}

internal fun darkSchemeFor(identity: SecaAppIdentity): ColorScheme {
    val accent = when (identity) {
        SecaAppIdentity.Contacts -> ContactsAccentDark
        SecaAppIdentity.Phone -> PhoneAccentDark
        SecaAppIdentity.Messages -> MessagesAccentDark
    }
    return darkColorScheme(
        primary = accent,
        onPrimary = Color(0xFF00363A),
        surface = NeutralSurfaceDark,
        onSurface = NeutralOnSurfaceDark,
        background = NeutralSurfaceDark,
        onBackground = NeutralOnSurfaceDark,
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
.\gradlew.bat :core:design:test --tests "*SecaThemeTest*"
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
 * wrap their content in this. [dynamicColor] honours the user's Material You
 * wallpaper palette, which is available on every device Seca targets.
 */
@Composable
fun SecaTheme(
    identity: SecaAppIdentity,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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
.\gradlew.bat :core:design:test --tests "*SecaThemeTest*"
```

Expected: PASS, 3 tests. Confirme aussi que les tests Compose tournent sous Robolectric, sans appareil connecté.

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
.\gradlew.bat :core:design:test --tests "*SecaAvatarTest*"
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
 * A contact's avatar: their photo when there is one, their initials otherwise.
 *
 * Photo loading is deliberately absent for now — no image library is pulled in
 * until an app actually needs one, and the initials path is what the catalog
 * and the contact list exercise first.
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
.\gradlew.bat :core:design:test --tests "*SecaAvatarTest*"
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
.\gradlew.bat :core:design:test --tests "*SecaContactRowTest*"
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

Expected: PASS, 6 tests au total.

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
    var dark by remember { mutableStateOf(false) }
    var dynamic by remember { mutableStateOf(false) }

    SecaTheme(identity = identity, darkTheme = dark, dynamicColor = dynamic) {
        // Animating the background makes the shared motion spec visible: switching
        // identity or theme should feel like one system reacting, not a hard cut.
        val background by animateColorAsState(
            targetValue = MaterialTheme.colorScheme.background,
            animationSpec = SecaMotion.emphasized(),
            label = "background",
        )
        Surface(color = background) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 24.dp),
            ) {
                Text(
                    text = "Seca",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )

                Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    SecaAppIdentity.entries.forEach { entry ->
                        FilterChip(
                            selected = identity == entry,
                            onClick = { identity = entry },
                            label = { Text(entry.name) },
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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
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

Verrouille les propriétés que la spec revendique, pour qu'elles ne puissent pas régresser silencieusement.

**Files:**
- Create: `core/design/src/test/kotlin/com/seca/core/design/NoProprietaryDependenciesTest.kt`
- Create: `README.md`
- Modify: `build-logic/src/main/kotlin/seca.android.application.gradle.kts` — activer lint en échec

**Interfaces:**
- Consumes: tout ce qui précède.
- Produces: aucune API. Des garanties.

- [ ] **Step 1: Écrire le test de garde sur les dépendances**

`core/design/src/test/kotlin/com/seca/core/design/NoProprietaryDependenciesTest.kt` :

```kotlin
package com.seca.core.design

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the F-Droid constraint: no Google Play, Firebase or analytics code
 * may reach the classpath. Checks the loaded classpath rather than the build
 * files, so a transitive dependency cannot slip one in unnoticed.
 */
class NoProprietaryDependenciesTest {

    private val forbidden = listOf(
        "com.google.android.gms",
        "com.google.firebase",
        "com.google.android.play",
    )

    @Test
    fun `no proprietary Google packages on the classpath`() {
        val classpath = System.getProperty("java.class.path").orEmpty()
        val offenders = forbidden.filter { pkg ->
            classpath.contains(pkg.replace('.', '/')) || classpath.contains(pkg)
        }
        assertTrue(
            "Dépendances propriétaires détectées : $offenders",
            offenders.isEmpty(),
        )
    }
}
```

- [ ] **Step 2: Lancer le test et vérifier qu'il passe**

```powershell
.\gradlew.bat :core:design:test --tests "*NoProprietaryDependenciesTest*"
```

Expected: PASS. Si le test échoue, une dépendance propriétaire est déjà entrée — la traquer avec `.\gradlew.bat :core:design:dependencies` avant d'aller plus loin.

- [ ] **Step 3: Activer lint en échec dans le convention plugin application**

Dans `seca.android.application.gradle.kts`, à l'intérieur du bloc `extensions.configure<ApplicationExtension>` :

```kotlin
    lint {
        warningsAsErrors = true
        abortOnError = true
        disable += setOf("GradleDependency")
    }
```

`GradleDependency` est désactivé volontairement : lint signale les versions non les plus récentes, ce qui est exactement ce qu'on veut pour des builds reproductibles.

- [ ] **Step 4: Lancer lint**

```powershell
.\gradlew.bat :apps:catalog:lint
```

Expected: BUILD SUCCESSFUL. Corriger ce qui est signalé avant de committer.

- [ ] **Step 5: Écrire le README**

`README.md` :

````markdown
# Seca

Trois applications de communication pour Android, conçues pour GrapheneOS :
**Seca Contacts**, **Seca Phone** et **Seca Messages**. Design Material 3
Expressive unifié, orientées vie privée, sans aucune dépendance Google.

## État

Fondation et design system en place. Seca Contacts est la prochaine étape.
Voir `docs/superpowers/specs/` pour la conception et `docs/superpowers/plans/`
pour les plans d'implémentation.

## Ce que Seca ne fait pas

- **Pas de RCS.** Google réserve son API RCS à une allowlist fermée ;
  aucune application tierce ne peut l'implémenter. Seca Messages proposera
  du SMS/MMS et une couche chiffrée de bout en bout entre utilisateurs Seca.
- **Pas d'implémentation des appels Wi-Fi.** Le VoWiFi relève de la pile IMS
  du système et fonctionne déjà indépendamment du composeur installé.
  Seca Phone en affichera l'état.

## Construire

Prérequis : JDK 17 et le SDK Android (plateforme 37, build-tools 36.0.0).

```bash
./gradlew :apps:catalog:assembleDebug
./gradlew test
```

## Vie privée

Aucune analytique, aucun rapport de plantage, aucune dépendance Google.
Seca Contacts ne demandera **pas** la permission `INTERNET` : l'application
sera structurellement incapable d'exfiltrer un répertoire.

## Licence

GPL-3.0-or-later.
````

- [ ] **Step 6: Lancer la suite complète**

```powershell
.\gradlew.bat test
```

Expected: PASS, 19 tests au total — 10 dans `:core:model`, 7 dans `:core:design`, 2 dans `:apps:catalog`.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "chore: garde-fous dépendances, lint strict et README"
```

---

## Vérification finale du plan

1. `.\gradlew.bat test` — les 19 tests au vert, sans appareil connecté
2. `.\gradlew.bat :apps:catalog:lint` — propre
3. `.\gradlew.bat :apps:catalog:assembleDebug` — APK produit
4. APK installé sur le Pixel 9 : basculer les trois identités, clair/sombre, couleur dynamique — le rendu doit convenir avant de passer à la suite
5. `.\gradlew.bat :core:design:dependencies` — aucun artefact `com.google.android.gms`, `com.google.firebase` ou `com.google.android.play`

## Écart assumé par rapport à la spec

La spec liste cinq composants partagés dans `:core:design` ; ce plan en livre trois. **La barre de recherche et les feuilles d'action sont reportées au plan Seca Contacts**, où elles auront un usage réel. Construire une barre de recherche sans rien à chercher produirait une API devinée plutôt que dérivée d'un besoin. Elles resteront dans `:core:design` — seul leur moment de création change.

## Suite

Une fois ce plan exécuté et le rendu visuel validé, le plan suivant couvre **Seca Contacts** : `:core:contacts` au-dessus de `ContactsContract`, liste avec défilement rapide et recherche, fiche, création/édition, favoris, groupes, import/export vCard, actions rapides, gestion des Contact Scopes de GrapheneOS, et le test de garde interdisant la permission `INTERNET`.
