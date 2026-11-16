import java.util.Properties
import java.net.URI

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
    "MUSIC_API_URL",
    "TIKTOK_API_URL",
    "PUBLIC_WEB_ORIGIN",
)

android {
    namespace = "com.rosyidputrar.taskify"

    defaultConfig {
        applicationId = "com.rosyidputrar.taskify"
        // CI: nomor run GitHub Actions → setiap APK bisa dipasang menimpa yang sebelumnya.
        versionCode = config("VERSION_CODE").toIntOrNull() ?: 1
        versionName = "1.0.0"
        serverConfigKeys.forEach { key -> buildConfigField("String", key, config(key).asBuildConfigString()) }
        // App Link halaman unduh publik: host dari PUBLIC_WEB_ORIGIN (tanpa skema/path).
        manifestPlaceholders["publicWebHost"] = publicWebHost
    }

    signingConfigs {
        // Kunci debug tetap (bukan rahasia) supaya APK dari CI bisa dipasang menimpa versi sebelumnya
        // tanpa harus uninstall (uninstall = data lokal yang belum tersinkron ikut hilang).
        getByName("debug") {
            storeFile = rootProject.file("app/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
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
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
