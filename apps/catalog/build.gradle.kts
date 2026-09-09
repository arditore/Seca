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
