plugins {
    alias(libs.plugins.taskify.jvm.library)
}

dependencies {
    api(projects.core.common)
    api(libs.kotlinx.serialization.json)
    implementation(libs.zxcvbn)
    // Public Suffix List (AutofillMatch.registrable): OkHttp membundel Mozilla PSL.
    implementation(libs.okhttp)
}

dependencies {
    testImplementation(libs.kotlinx.coroutines.test)
}
