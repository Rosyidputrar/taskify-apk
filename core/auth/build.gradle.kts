plugins {
    alias(libs.plugins.taskify.android.library)
}

android {
    namespace = "com.rosyidputrar.taskify.core.auth"
}

dependencies {
    api(projects.core.data)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.ktor.client.okhttp)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}
