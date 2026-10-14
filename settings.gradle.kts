pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "atlas"

include(":app")
include(":core:model")
include(":core:common")
include(":core:datastore")
include(":core:data")
include(":core:designsystem")
include(":core:map")
include(":feature:onboarding")
include(":feature:day")
include(":feature:history")
include(":feature:journey")
include(":feature:insights")
include(":feature:places")
include(":feature:settings")
include(":feature:diagnostics")
