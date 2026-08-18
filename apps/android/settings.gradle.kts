// Elysium Nexus Universal Controller — Android module settings
//
// Single Android module for now: :app. We split into :app + :core
// when the core math stops fitting in one module (see ADR-0001).
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

rootProject.name = "elysium-nexus-controller"
include(":app")
include(":tvlink")
