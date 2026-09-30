pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "GiffyViewer"

include(":app-mobile")
include(":app-tv")
include(":core:model")
include(":core:network")
include(":core:auth")
include(":core:database")
include(":core:datastore")
include(":core:player")
include(":core:ui")
include(":feature:feed")
include(":feature:search")
include(":feature:favorites")
include(":feature:auth")
include(":feature:settings")
