pluginManagement {
    includeBuild("build-logic")
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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "budget-widget"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":core:model")
include(":core:database")
include(":core:data")
include(":core:datastore")
include(":core:designsystem")
include(":core:testing")
include(":ingest")
include(":notifications")
include(":widget")
include(":feature:analysis")
include(":feature:transactions")
include(":feature:categories")
include(":feature:onboarding")
include(":feature:settings")
include(":parser-core")

// Fail fast with a clear message instead of a "no sources" error deep inside :parser-core.
check(file("third_party/pennywise/parser-core/src/main/kotlin").isDirectory) {
    "Submodule third_party/pennywise is missing. Run: git submodule update --init"
}
