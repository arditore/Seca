plugins {
    id("seca.android.application")
    id("seca.compose")
}

android {
    namespace = "com.seca.phone"
    defaultConfig {
        applicationId = "com.seca.phone"
        // Written out here, not only in the shared convention plugin: F-Droid reads the version
        // of an app from its own build file, and finds nothing in a plugin it does not run.
        versionCode = 4
        versionName = "0.1.0-beta4"
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core:design"))
    implementation(project(":core:contacts"))
    implementation(project(":core:suite"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
}
