enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

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

rootProject.name = "taskify-apk"

include(":app")
include(":core:common")
include(":core:designsystem")
include(":core:domain")
include(":core:store")
include(":core:data")
include(":core:auth")
include(":core:reminders")
include(":feature:auth")
include(":feature:tasks")
include(":feature:calendar")
include(":core:ui")
include(":feature:journal")
include(":feature:notes")
include(":feature:finance")
include(":core:files")
include(":feature:attachments")
include(":feature:vault")
include(":core:sandi")
include(":feature:sandi")
include(":feature:files")
include(":feature:review")
