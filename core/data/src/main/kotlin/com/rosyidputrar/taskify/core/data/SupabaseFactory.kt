package com.rosyidputrar.taskify.core.data

import android.content.Context
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
 * D-05: sesi disimpan lewat [createEncryptedSessionManager] (AES-256-GCM + Keystore),
 * bukan SessionManager bawaan supabase-kt yang plaintext di SharedPreferences.
 */
fun createTaskifySupabase(config: ServerConfig, context: Context): SupabaseClient =
    createSupabaseClient(supabaseUrl = config.supabaseUrl, supabaseKey = config.supabaseKey) {
        install(Auth) {
            // Sesi disimpan di perangkat & di-refresh otomatis: aplikasi tetap terbuka saat offline.
            alwaysAutoRefresh = true
            autoLoadFromStorage = true
            // Keystore rusak/tidak tersedia di sebagian perangkat: jangan sampai aplikasi crash
            // saat dibuka (klien ini dibuat di startup). Jatuh ke penyimpanan bawaan supabase-kt
            // (perilaku sebelum D-05) — tetap bisa masuk, hanya tanpa enkripsi tambahan.
            runCatching { createEncryptedSessionManager(context, config.supabaseUrl) }
                .onFailure { android.util.Log.w("Taskify", "Sesi terenkripsi tidak tersedia, memakai penyimpanan bawaan", it) }
                .getOrNull()?.let { sessionManager = it }
        }
        install(Postgrest)
        install(Functions)
    }
