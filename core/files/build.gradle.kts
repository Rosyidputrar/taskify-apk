plugins {
    alias(libs.plugins.taskify.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.rosyidputrar.taskify.core.files"
}

/** Jalur file lampiran: presign lewat Edge Function `files`, unggah/unduh R2, outbox WorkManager. */
dependencies {
    api(projects.core.data)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    api(libs.androidx.work.runtime)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
}
