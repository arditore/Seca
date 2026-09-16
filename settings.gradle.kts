pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // libsignal repository (start)
        // Where libsignal comes from: the address lives in gradle.properties, and only the
        // org.signal group is ever taken from there. A build that compiles libsignal itself, as
        // F-Droid does, replaces everything between these two markers with its own folder.
        val libsignalRepo = providers.gradleProperty("seca.libsignal.repo").get()
        exclusiveContent {
            forRepository { if (libsignalRepo == "mavenLocal") mavenLocal() else maven(libsignalRepo) }
            filter { includeGroup("org.signal") }
        }
        // libsignal repository (end)
    }
}

rootProject.name = "Seca"

include(":core:model")
include(":core:design")
include(":apps:catalog")

include(":core:contacts")
include(":apps:contacts")
include(":apps:phone")
include(":apps:messages")

include(":core:link")
include(":core:suite")
