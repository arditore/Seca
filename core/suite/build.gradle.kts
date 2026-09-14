plugins {
    id("seca.android.library")
    id("seca.compose")
}

android {
    namespace = "com.seca.core.suite"
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core:design"))
    api(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
}
