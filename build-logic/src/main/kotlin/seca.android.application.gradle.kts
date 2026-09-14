import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.seca.buildlogic.VerifyNoProprietaryDependencies

plugins {
    id("com.android.application")
}

extensions.configure<ApplicationExtension> {
    compileSdk = 37
    buildToolsVersion = "36.0.0"
    defaultConfig {
        // Android 12: the first with Material You colours and call-style notifications, and
        // still what many phones run. GrapheneOS phones are far newer; newer APIs are checked
        // for and fall back gracefully.
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-beta1"
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
    // The key the published APKs are signed with. It never enters the repository: its file
    // and passwords come from the maintainer's own ~/.gradle/gradle.properties.
    val releaseStore = providers.gradleProperty("seca.release.storeFile")
    if (releaseStore.isPresent) {
        signingConfigs.create("release") {
            storeFile = file(releaseStore.get())
            storePassword = providers.gradleProperty("seca.release.storePassword").get()
            keyAlias = providers.gradleProperty("seca.release.keyAlias").get()
            keyPassword = providers.gradleProperty("seca.release.keyPassword").get()
        }
    }
    buildTypes {
        getByName("release") {
            // R8 shrinks and optimises the code, and the libraries' baseline profiles get
            // compiled ahead of time: Compose scrolls far more smoothly than in a debug build.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // F-Droid signs releases itself and needs them unsigned: without the release key,
            // nothing signs them. To try a release on one's own phone,
            // -Pseca.signReleaseWithDebugKey signs it with the debug key, the one the debug
            // builds use, so it installs over them and the Seca apps still recognise each other.
            when {
                providers.gradleProperty("seca.signReleaseWithDebugKey").isPresent ->
                    signingConfig = signingConfigs.getByName("debug")
                releaseStore.isPresent -> signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    lint {
        warningsAsErrors = true
        abortOnError = true
        // Pinned versions are the point of a reproducible build; this check
        // would otherwise fire on every dependency the moment a newer one ships.
        disable += setOf("GradleDependency")
    }
}

kotlin {
    jvmToolchain(17)
}

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
