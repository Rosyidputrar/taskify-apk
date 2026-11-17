plugins {
    alias(libs.plugins.taskify.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.rosyidputrar.taskify.core.media"
}

/** Musik (Worker music-taskify + Media3) dan TikTok (Worker ttdl-taskify). Tanpa database. */
dependencies {
    api(projects.core.data)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    api(libs.androidx.media3.exoplayer)
    api(libs.androidx.media3.session)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.ktor.client.mock)
}
