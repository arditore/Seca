plugins {
    id("seca.android.application")
    id("seca.compose")
}

android {
    namespace = "com.seca.phone"
    defaultConfig { applicationId = "com.seca.phone" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core:design"))
    implementation(project(":core:contacts"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
}
