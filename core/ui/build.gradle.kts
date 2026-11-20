plugins {
    alias(libs.plugins.taskify.android.library)
    alias(libs.plugins.taskify.android.compose)
}

android {
    namespace = "com.rosyidputrar.taskify.core.ui"
}

/** Komponen UI bersama yang butuh model domain/store: editor Markdown, pemilih tag & tanggal. */
dependencies {
    api(projects.core.designsystem)
    api(projects.core.store)
    // Menu "Sematkan ke Hari ini" mengambil repositori pintasan dari Koin bila ada (tes tanpa Koin → disembunyikan).
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.androidx.compose)
}
