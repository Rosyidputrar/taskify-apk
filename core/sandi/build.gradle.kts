plugins {
    alias(libs.plugins.taskify.android.library)
}

android {
    namespace = "com.rosyidputrar.taskify.core.sandi"
}

/** Sesi brankas Sandi (DEK di memori), ingat perangkat via Keystore, papan klip aman, rotasi kunci. */
dependencies {
    api(projects.core.data)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.process)
}
