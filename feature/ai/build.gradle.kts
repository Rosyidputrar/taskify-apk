plugins {
    alias(libs.plugins.taskify.android.library)
    alias(libs.plugins.taskify.android.compose)
}

android {
    namespace = "com.rosyidputrar.taskify.feature.ai"
}

/** Asisten AI: sesi chat, streaming, alat baca/tulis data Taskify (dengan konfirmasi). */
dependencies {
    api(projects.core.data)
    api(projects.core.ai)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.androidx.compose)
    testImplementation(libs.kotlinx.coroutines.test)
}
