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
    // Apache-2.0, pure Java, same project as the parser: where a number is from (#93).
    implementation(libs.libphonenumber.geocoder)
    implementation(libs.libphonenumber.carrier)
    testImplementation(libs.junit)
}
