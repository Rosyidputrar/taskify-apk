plugins {
    alias(libs.plugins.taskify.android.library)
    alias(libs.plugins.taskify.android.compose)
}

android {
    namespace = "com.rosyidputrar.taskify.feature.media"
    // BuildConfig.DEBUG dipakai RawErrorDetail: tombol "Salin detail"
    // hanya tampil di build debug.
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    api(projects.core.media)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.androidx.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}
