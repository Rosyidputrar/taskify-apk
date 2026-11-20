plugins {
    alias(libs.plugins.taskify.android.library)
}

android {
    namespace = "com.rosyidputrar.taskify.core.ai"
}

/** Klien AI (OpenAI-compatible & Anthropic) + pengaturan & kunci API terenkripsi Keystore. */
dependencies {
    api(projects.core.domain)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}
