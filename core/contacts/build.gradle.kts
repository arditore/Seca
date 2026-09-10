plugins {
    id("seca.android.library")
}

android {
    namespace = "com.seca.core.contacts"
}

dependencies {
    api(project(":core:model"))
    // Exposed as api: the repository's public API returns a Flow.
    api(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
