plugins {
    alias(libs.plugins.taskify.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.rosyidputrar.taskify.core.media"
}

/** Dependensi `:core:innertube` untuk resolve stream direct (Tahap 3). */
dependencies {
    api(projects.core.data)
    api(projects.core.innertube)
    api(projects.core.youtubei)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    api(libs.androidx.media3.exoplayer)
    api(libs.androidx.media3.session)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.database)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.ktor.client.mock)
}
