plugins {
    id("org.jetbrains.kotlin.plugin.compose")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    add("implementation", libs.findLibrary("compose-ui").get())
    add("implementation", libs.findLibrary("compose-ui-graphics").get())
    add("implementation", libs.findLibrary("compose-foundation").get())
    add("implementation", libs.findLibrary("compose-ui-tooling-preview").get())
    add("debugImplementation", libs.findLibrary("compose-ui-tooling").get())
    add("testImplementation", libs.findLibrary("robolectric").get())
    add("testImplementation", libs.findLibrary("junit").get())
    add("testImplementation", libs.findLibrary("androidx-test-core").get())
    add("testImplementation", libs.findLibrary("androidx-test-ext-junit").get())
    add("testImplementation", libs.findLibrary("compose-ui-test-junit4").get())
    add("debugImplementation", libs.findLibrary("compose-ui-test-manifest").get())
}
