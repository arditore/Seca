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
        // Signal publishes libsignal on its own repository. Only its group is taken from
        // there, so no other dependency can be swapped in through it.
        exclusiveContent {
            forRepository { maven("https://build-artifacts.signal.org/libraries/maven/") }
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
