plugins {
    alias(libs.plugins.taskify.android.library)
}

android {
    namespace = "com.rosyidputrar.taskify.core.youtubei"
}

/**
 * Adaptasi modul morideobfuscator ArchiveTune (GPL-3.0, lihat licenses/ARCHIVETUNE.md):
 * QuickJS + youtubei.js 18.0.0 untuk resolve + decipher stream YouTube.
 * Aset JS disalin verbatim dari ArchiveTune; sumber JS ada di `javascript/`
 * dan dapat dibangun ulang dengan `node scripts/bundle.mjs` (lihat README.md).
 */
dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)
    implementation(libs.quickjs.kt)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.core)
}
