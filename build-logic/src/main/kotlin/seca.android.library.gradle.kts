import com.android.build.api.dsl.LibraryExtension

plugins {
    id("com.android.library")
}

extensions.configure<LibraryExtension> {
    compileSdk = 37
    buildToolsVersion = "36.0.0"
    defaultConfig {
        minSdk = 31
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

// The bytecode targets Java 17, but any JDK from 17 up may compile it: a build server that
// brings its own JDK, F-Droid for one, has no reason to install another.
//
// Set on the tasks rather than through the `kotlin { }` block. That block is an accessor Gradle
// generates by applying the plugin to a throwaway project first, and on Windows that probe comes
// back with almost nothing, so the block does not resolve and build-logic will not compile there.
// The tasks are typed, need no accessor, and take the same value.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
