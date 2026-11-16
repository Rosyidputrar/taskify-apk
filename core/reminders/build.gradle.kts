plugins {
    alias(libs.plugins.taskify.android.library)
}

android {
    namespace = "com.rosyidputrar.taskify.core.reminders"
}

dependencies {
    api(projects.core.data)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.androidx.work.runtime)
}
