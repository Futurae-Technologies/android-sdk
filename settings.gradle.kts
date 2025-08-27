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
        maven("https://maven.pkg.github.com/Futurae-Technologies/android-sdk") {
            credentials {
                username = extra.properties["GITHUB_ACTOR"] as String
                password = extra.properties["GITHUB_TOKEN"] as String
            }
        }
        maven("https://maven.pkg.github.com/Futurae-Technologies/android-adaptive-sdk") {
            credentials {
                username = extra.properties["GITHUB_ACTOR"] as String
                password = extra.properties["GITHUB_TOKEN"] as String
            }
        }
    }
}

rootProject.name = "android-sdk"
include(":android-sdk-sample:futuraeSampleApp")
