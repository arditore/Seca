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
        // libsignal, from wherever this build is told to take it. F-Droid builds every
        // dependency from source, so its recipe compiles libsignal and points this at the
        // folder it published into: -Pseca.libsignal.repo=/path/to/maven. Without it, the
        // build takes libsignal from Signal's own repository, and only its group from
        // there, so no other dependency can be swapped in through it.
        val libsignalRepo = providers.gradleProperty("seca.libsignal.repo").orNull
        exclusiveContent {
            forRepository {
                when (libsignalRepo) {
                    null -> maven("https://build-artifacts.signal.org/libraries/maven/")
                    "mavenLocal" -> mavenLocal()
                    else -> maven(libsignalRepo)
                }
            }
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
