plugins {
    alias(libs.plugins.taskify.android.library)
    alias(libs.plugins.taskify.android.compose)
}

android {
    namespace = "com.rosyidputrar.taskify.feature.sandi"
}

dependencies {
    api(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    api(projects.core.sandi)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.androidx.compose)
    // Impor 2FA: pindai QR "Transfer akun" (Google) + baca QR dari foto (zxing, tanpa Play Services).
    implementation(libs.play.services.code.scanner)
    implementation(libs.zxing.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
