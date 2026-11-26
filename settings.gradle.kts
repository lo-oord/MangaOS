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
        // Republished Mihon source-api + core-common (Apache-2.0), matching the API that
        // current Keiyoushi extensions are compiled against (tachiyomix 1.6).
        maven(url = "https://raw.githubusercontent.com/verizonkert/mihon-kit/mvn-repo")
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "MangaOS"
include(":app")
