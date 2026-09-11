plugins {
    id("seca.android.library")
}

android {
    namespace = "com.seca.core.contacts"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    // Exposed as api: the repository's public API returns a Flow.
    api(libs.kotlinx.coroutines.android)
    // Apache-2.0, pure Java, no network: the reference rules for numbers of every country.
    implementation(libs.libphonenumber)
    testImplementation(libs.junit)
}
