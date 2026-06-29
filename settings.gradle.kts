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

rootProject.name = "CarTripAnalyzer"
include(":app")
// Phase 0 (UX premium modular): empty engine module holding only the Entitlements seam for now.
// Real engine extraction (analysis/data/record/cloud) happens in Phase 1.
include(":core-engine")
