package com.rosyidputrar.taskify

/**
 * Konfigurasi server dari BuildConfig (diisi saat build dari GitHub Secrets / local.properties).
 * Nilainya tidak pernah ditampilkan di UI — hanya status terisi/kosong.
 */
object AppConfig {
    val supabaseUrl: String = BuildConfig.SUPABASE_URL
    val supabasePublishableKey: String = BuildConfig.SUPABASE_PUBLISHABLE_KEY
    val powerSyncUrl: String = BuildConfig.POWERSYNC_URL
    val tiktokApiUrl: String = BuildConfig.TIKTOK_API_URL
    val publicWebOrigin: String = BuildConfig.PUBLIC_WEB_ORIGIN.trimEnd('/')

    data class Entry(val key: String, val description: String, val required: Boolean, val isSet: Boolean)

    val entries: List<Entry> = listOf(
        Entry("SUPABASE_URL", "Auth & database", required = true, isSet = supabaseUrl.isNotBlank()),
        Entry("SUPABASE_PUBLISHABLE_KEY", "Kunci publik Supabase", required = true, isSet = supabasePublishableKey.isNotBlank()),
        Entry("POWERSYNC_URL", "Sinkronisasi offline", required = true, isSet = powerSyncUrl.isNotBlank()),
        Entry("TIKTOK_API_URL", "Unduh TikTok", required = false, isSet = tiktokApiUrl.isNotBlank()),
        Entry("PUBLIC_WEB_ORIGIN", "Tautan publik & Origin Worker", required = false, isSet = publicWebOrigin.isNotBlank()),
    )

    /** Tiga nilai wajib terisi → login & sinkron bisa dipakai. */
    val isServerReady: Boolean = entries.filter { it.required }.all { it.isSet }
}
