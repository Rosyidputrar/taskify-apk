plugins {
    alias(libs.plugins.taskify.android.library)
    alias(libs.plugins.taskify.android.compose)
}

android {
    namespace = "com.rosyidputrar.taskify.feature.finance"
}

dependencies {
    implementation(projects.feature.links)
    api(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.feature.attachments)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.androidx.compose)
}
