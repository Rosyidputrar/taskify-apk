plugins {
    alias(libs.plugins.taskify.jvm.library)
}

/**
 * Skema + repository di atas PowerSync, tanpa Android. Di aplikasi `com.powersync:core` terselesaikan
 * ke varian Android; di tes JVM ke varian JVM (SQLite + ekstensi PowerSync asli), sehingga SQL
 * repository diuji terhadap database sungguhan.
 */
dependencies {
    api(projects.core.domain)
    api(libs.powersync.core)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
