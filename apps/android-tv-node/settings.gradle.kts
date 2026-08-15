// Elysium Nexus TV Node — Android TV side of the Software-Only TV Fabric.
//
// Standalone build (mirrors apps/android conventions): single :app module,
// wrapper Gradle 9.3.1, AGP 8.7.3, Kotlin 2.0.21 — the exact toolchain that
// passed the full controller batch (1275 JVM tests, debug+lint+release).
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

rootProject.name = "elysium-nexus-tv-node"
include(":app")