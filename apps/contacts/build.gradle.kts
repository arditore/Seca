plugins {
    id("seca.android.application")
    id("seca.compose")
}

android {
    namespace = "com.seca.contacts"
    defaultConfig { applicationId = "com.seca.contacts" }
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
