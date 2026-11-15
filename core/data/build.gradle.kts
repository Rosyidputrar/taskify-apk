plugins {
    alias(libs.plugins.taskify.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.rosyidputrar.taskify.core.data"
}

dependencies {
    api(projects.core.domain)
    api(libs.powersync.core)
    api(platform(libs.supabase.bom))
    api(libs.supabase.auth)
    api(libs.supabase.postgrest)
    api(libs.supabase.functions)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
}
