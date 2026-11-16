plugins {
    alias(libs.plugins.taskify.android.library)
}

android {
    namespace = "com.rosyidputrar.taskify.core.sandi"
    // Uji instrumentasi Keystore + BiometricPrompt sungguhan (butuh emulator/HP dengan kunci layar).
    defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
}

/** Sesi brankas Sandi (DEK di memori), ingat perangkat via Keystore, papan klip aman, rotasi kunci. */
dependencies {
    api(projects.core.data)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.process)
    api(libs.androidx.biometric)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlin.test.junit)
    androidTestImplementation(libs.kotlinx.coroutines.android)
}
