plugins { id("seca.android.library") }
android { namespace = "com.seca.core.model" }
dependencies {
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    // The calendars come from Android ICU, so their test needs an Android to ask.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
