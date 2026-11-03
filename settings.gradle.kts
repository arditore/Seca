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
        // Where the two libraries with native code come from.
        //
        // libsignal is published on Signal's own Maven repository, whose address lives in
        // gradle.properties; secp256k1 comes from Maven Central like everything else. A build
        // that compiles both itself, as F-Droid does, points this at the folder it published
        // them into, and then takes both from there and nothing else from it. Such a build
        // replaces everything between these two markers, because its scanner reads an address
        // held in a variable as an unknown repository.
        val libsignalRepo = providers.gradleProperty("seca.libsignal.repo").get()
        if (libsignalRepo == "mavenLocal") {
            exclusiveContent {
                forRepository { mavenLocal() }
                filter {
                    includeGroup("org.signal")
                    includeGroup("fr.acinq.secp256k1")
                }
            }
        } else {
            exclusiveContent {
                forRepository { maven(libsignalRepo) }
                filter { includeGroup("org.signal") }
            }
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
