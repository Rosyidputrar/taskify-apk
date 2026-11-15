package com.rosyidputrar.taskify.core.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest

/** Konfigurasi server dari BuildConfig aplikasi. */
data class ServerConfig(
    val supabaseUrl: String,
    val supabaseKey: String,
    val powerSyncUrl: String,
) {
    val isComplete: Boolean get() = supabaseUrl.isNotBlank() && supabaseKey.isNotBlank() && powerSyncUrl.isNotBlank()
}

/**
 * Satu-satunya Supabase client: auth, unggah antrean PowerSync, dan Edge Functions.
 * Membaca data TIDAK lewat sini — semua baca dari SQLite lokal.
 */
fun createTaskifySupabase(config: ServerConfig): SupabaseClient =
    createSupabaseClient(supabaseUrl = config.supabaseUrl, supabaseKey = config.supabaseKey) {
        install(Auth) {
            // Sesi disimpan di perangkat & di-refresh otomatis: aplikasi tetap terbuka saat offline.
            alwaysAutoRefresh = true
            autoLoadFromStorage = true
        }
        install(Postgrest)
        install(Functions)
    }
