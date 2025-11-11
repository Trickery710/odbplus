pluginManagement {
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
rootProject.name = "odbplus"
include(
    ":app",
    ":core-transport",
    ":core-protocol",
    ":data-schema",
    ":feature-live",
    ":feature-diagnostics",
    ":feature-logger",
    ":feature-ecu-profile"
)
