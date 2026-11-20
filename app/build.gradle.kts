import java.util.Properties
import java.net.URI
import java.security.KeyStore

plugins {
    alias(libs.plugins.taskify.android.application)
    alias(libs.plugins.taskify.android.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

/**
 * Konfigurasi server dibaca dari environment variable (GitHub Actions → Secrets) lalu
 * `local.properties` (build lokal). Tidak pernah di-commit. Kosong = aplikasi tetap jalan dan
 * menampilkan status "belum dikonfigurasi" di Pengaturan.
 */
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun config(name: String): String =
    (providers.environmentVariable(name).orNull ?: localProperties.getProperty(name) ?: "").trim()

fun String.asBuildConfigString(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

/** Host App Link halaman unduh publik; placeholder yang tidak bisa dicocokkan bila belum diisi. */
val publicWebHost: String = runCatching { URI(config("PUBLIC_WEB_ORIGIN")).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: "taskify.invalid"

val serverConfigKeys = listOf(
    "SUPABASE_URL",
    "SUPABASE_PUBLISHABLE_KEY",
    "POWERSYNC_URL",
    "TIKTOK_API_URL",
    "PUBLIC_WEB_ORIGIN",
)

android {
    namespace = "com.rosyidputrar.taskify"

    defaultConfig {
        applicationId = "com.rosyidputrar.taskify"
        // CI: nomor run GitHub Actions → setiap APK bisa dipasang menimpa yang sebelumnya.
        versionCode = config("VERSION_CODE").toIntOrNull() ?: 1
        // Nomor build masuk ke nama versi yang tampil, supaya dua APK rilis bisa dibedakan
        // dari Pengaturan (bukan hanya dari versionCode yang tak terlihat).
        versionName = config("VERSION_CODE").toIntOrNull()?.let { "1.0.0.$it" } ?: "1.0.0"
        serverConfigKeys.forEach { key -> buildConfigField("String", key, config(key).asBuildConfigString()) }
        // App Link halaman unduh publik: host dari PUBLIC_WEB_ORIGIN (tanpa skema/path).
        manifestPlaceholders["publicWebHost"] = publicWebHost
    }

    signingConfigs {
        // Kunci debug tetap (bukan rahasia) supaya APK debug dari CI bisa dipasang menimpa versi
        // debug sebelumnya tanpa uninstall. Karena kunci ini publik, build debug memakai paket
        // terpisah (`.debug`, lihat buildTypes): APK yang ditandatangani kunci ini tidak bisa
        // menimpa aplikasi utama dan tidak bisa membaca datanya.
        getByName("debug") {
            storeFile = rootProject.file("app/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            // Preship: aktifkan v1+v2+v3 supaya semua installer menerima APK debug.
            // AGP 9: API baru enableV1/V2/V3Signing (isV1/V2SigningEnabled deprecated
            // dan tidak lagi berpengaruh, itu sebabnya v1 tetap mati sebelumnya).
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
        val releaseStore = config("RELEASE_KEYSTORE_PATH")
        if (releaseStore.isNotEmpty() && file(releaseStore).exists()) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = config("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = config("RELEASE_KEY_ALIAS")
                keyPassword = config("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
            // Paket sendiri: bisa terpasang berdampingan dengan versi rilis, tidak bisa menimpanya.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // Nama & shortcut statis versi debug: app/src/debug/res.
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

// Fail-fast build rilis (E-M3): tanpa ini assembleRelease lokal tanpa keystore diam-diam
// menghasilkan APK unsigned, dan password yang salah baru ketahuan di akhir build yang panjang
// (packageRelease). Validasi jalan saat konfigurasi, sebelum kompilasi apa pun.
// Sengaja TIDAK menyentuh build debug dan tidak menyentuh keystore/password yang ada.
val releaseTaskRequested = gradle.startParameter.taskNames.any { requested ->
    val simple = requested.substringAfterLast(':')
    simple.contains("assembleRelease") || simple.contains("bundleRelease") || simple == "build" || simple == "assemble"
}
if (releaseTaskRequested) {
    val storePath = config("RELEASE_KEYSTORE_PATH")
    if (storePath.isEmpty()) throw GradleException("Build rilis butuh keystore: isi RELEASE_KEYSTORE_PATH (path ke berkas .jks/.keystore). Build debug tidak butuh ini.")
    val storeFile = file(storePath)
    if (!storeFile.exists()) throw GradleException("Build rilis gagal: RELEASE_KEYSTORE_PATH menunjuk berkas yang tidak ada: $storePath")
    val storePassword = config("RELEASE_KEYSTORE_PASSWORD")
    val keyAlias = config("RELEASE_KEY_ALIAS")
    val keyPassword = config("RELEASE_KEY_PASSWORD")
    if (storePassword.isEmpty()) throw GradleException("Build rilis gagal: RELEASE_KEYSTORE_PASSWORD kosong.")
    if (keyAlias.isEmpty()) throw GradleException("Build rilis gagal: RELEASE_KEY_ALIAS kosong.")
    if (keyPassword.isEmpty()) throw GradleException("Build rilis gagal: RELEASE_KEY_PASSWORD kosong.")
    val keystore = try {
        KeyStore.getInstance(storeFile, storePassword.toCharArray())
    } catch (e: Exception) {
        throw GradleException("Build rilis gagal: keystore tidak bisa dibuka dengan RELEASE_KEYSTORE_PASSWORD (${e.message}).", e)
    }
    try {
        if (keystore.getKey(keyAlias, keyPassword.toCharArray()) == null) {
            throw GradleException("Build rilis gagal: alias '$keyAlias' (RELEASE_KEY_ALIAS) tidak ada di keystore, atau RELEASE_KEY_PASSWORD salah.")
        }
    } catch (e: GradleException) {
        throw e
    } catch (e: Exception) {
        throw GradleException("Build rilis gagal: kunci '$keyAlias' tidak bisa dibuka dengan RELEASE_KEY_PASSWORD (${e.message}).", e)
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.auth)
    implementation(projects.feature.auth)
    implementation(projects.feature.tasks)
    implementation(projects.feature.calendar)
    implementation(projects.feature.journal)
    implementation(projects.feature.notes)
    implementation(projects.feature.finance)
    implementation(projects.feature.vault)
    implementation(projects.feature.attachments)
    implementation(projects.feature.sandi)
    implementation(projects.feature.files)
    implementation(projects.feature.review)
    implementation(projects.feature.links)
    implementation(projects.feature.organize)
    implementation(projects.feature.media)
    implementation(projects.feature.notifications)
    implementation(projects.feature.ai)
    implementation(projects.feature.wellbeing)
    implementation(projects.core.device)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(projects.core.files)
    implementation(projects.core.ui)
    implementation(projects.core.reminders)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.koin.test)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
