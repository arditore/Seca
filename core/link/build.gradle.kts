plugins {
    id("seca.android.library")
}

android {
    namespace = "com.seca.core.link"
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

// libsignal's classes need Java 21 or later to run: the tests run on a newer JDK, the build stays on 17.
tasks.withType<Test>().configureEach {
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // Exposed as api: publishing reports each relay's answer through a Flow.
    api(libs.kotlinx.coroutines.android)
    // AGPL-3.0: the Signal Protocol, PQXDH then Double Ratchet. Native code, which F-Droid
    // builds from its Rust sources.
    implementation(libs.libsignal.android)
    // Apache-2.0: the Schnorr signatures on secp256k1 that Nostr events carry.
    implementation(libs.secp256k1.jni.android)
    // Apache-2.0: the WebSocket to the relays.
    implementation(libs.okhttp)
    testImplementation(libs.junit)
}
