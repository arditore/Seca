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
