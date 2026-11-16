plugins {
    alias(libs.plugins.taskify.android.library)
    alias(libs.plugins.taskify.android.compose)
}

android {
    namespace = "com.rosyidputrar.taskify.core.device"
}

dependencies {
    api(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.androidx.work.runtime)
}
