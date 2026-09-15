plugins {
    id("seca.android.application")
    id("seca.compose")
}

android {
    namespace = "com.seca.messages"
    defaultConfig {
        applicationId = "com.seca.messages"
        // Both ARM builds of libsignal: 64-bit for nearly every phone, 32-bit for the entry-level
        // phones whose Android runs in 32-bit. The x86 builds only serve emulators and Chromebooks.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }
    buildFeatures { compose = true }
    // libsignal uses Java APIs newer than Android offers everywhere; desugaring supplies them.
    compileOptions { isCoreLibraryDesugaringEnabled = true }
    // The NDK strips the debug symbols out of libsignal's native code as the APK is packaged,
    // which takes it from over a hundred megabytes to about ten.
    ndkVersion = "30.0.16248370"
    packaging {
        jniLibs {
            // libsignal ships a second build of its native code, for its own tests.
            excludes += "**/libsignal_jni_testing.so"
        }
        resources {
            // libsignal's desktop library carries its macOS, Windows and Linux builds as plain
            // files; Android only loads the one under lib/.
            excludes += listOf("/*.dylib", "/*.dll", "/libsignal_jni*.so")
        }
    }
    lint {
        // Seca is made for phones, not Chromebooks: the x86_64 build is left out on purpose.
        disable += "ChromeOsAbiSupport"
    }
}

dependencies {
    implementation(project(":core:design"))
    implementation(project(":core:contacts"))
    implementation(project(":core:link"))
    implementation(project(":core:suite"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // Apache-2.0, pure Java: reads the contact's safety number from the camera.
    implementation(libs.zxing.core)
    // Apache-2.0: the camera, open only while the owner scans a safety number.
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.compose)
    // Apache-2.0, open source: the Java APIs that libsignal needs.
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
