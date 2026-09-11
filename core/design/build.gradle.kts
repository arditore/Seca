plugins {
    id("seca.android.library")
    id("seca.compose")
}
android {
    namespace = "com.seca.core.design"
    buildFeatures { compose = true }
}
dependencies {
    api(libs.compose.material3)
    api(project(":core:model"))
    implementation(libs.androidx.core.ktx)
}
