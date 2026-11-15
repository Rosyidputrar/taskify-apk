plugins {
    alias(libs.plugins.taskify.jvm.library)
}

dependencies {
    api(projects.core.common)
    api(libs.kotlinx.serialization.json)
    implementation(libs.zxcvbn)
}

dependencies {
    testImplementation(libs.kotlinx.coroutines.test)
}
