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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Talks"

include(":core")

// `-PcoreOnly` lets the pure-Kotlin engine be built and tested on machines that have no
// Android SDK (for example sandboxes). Normal builds include the app.
if (!providers.gradleProperty("coreOnly").isPresent) {
    include(":app")
}
