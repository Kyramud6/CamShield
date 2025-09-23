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
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/jan-tennert/supabase-kt")
            credentials {
                username = settings.extra["GITHUB_USERNAME"] as String? ?: ""
                password = settings.extra["GITHUB_TOKEN"] as String? ?: ""
            }
        }

    }
}

rootProject.name = "CamSHIELD App"
include(":app")
