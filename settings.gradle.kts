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
        // libsignal, from wherever this build is told to take it: the address lives in
        // gradle.properties, and a build that compiles libsignal itself, as F-Droid does, passes
        // the folder it published into instead. Only the org.signal group is taken from there, so
        // no other dependency can be swapped in through it.
        val libsignalRepo = providers.gradleProperty("seca.libsignal.repo").get()
        exclusiveContent {
            forRepository { if (libsignalRepo == "mavenLocal") mavenLocal() else maven(libsignalRepo) }
            filter { includeGroup("org.signal") }
        }
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
