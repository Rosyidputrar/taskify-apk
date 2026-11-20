plugins {
    alias(libs.plugins.taskify.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Klien InnerTube YouTube Music (direct-to-YouTube ala ArchiveTune).
 * Modul JVM murni, tanpa Android — transport, parser, dan fasad bisa dites
 * dengan fixture JSON respons asli (lihat src/test/resources/fixtures).
 */
dependencies {
    api(projects.core.domain)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.encoding)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}
