plugins {
    alias(libs.plugins.taskify.jvm.library)
}

dependencies {
    api(projects.core.common)
    api(libs.kotlinx.serialization.json)
}
