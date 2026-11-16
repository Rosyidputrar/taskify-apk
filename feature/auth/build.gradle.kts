plugins {
    alias(libs.plugins.taskify.android.library)
    alias(libs.plugins.taskify.android.compose)
}

android {
    namespace = "com.rosyidputrar.taskify.feature.auth"
}

dependencies {
    api(projects.core.auth)
    implementation(projects.core.designsystem)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.zxing.core)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.androidx.compose)
}
