package com.rosyidputrar.taskify.core.sandi

import com.rosyidputrar.taskify.core.domain.sandi.RemoteKeyState
import com.rosyidputrar.taskify.core.domain.sandi.UNREACHABLE
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/** Pemeriksaan ke SERVER (bukan database lokal) untuk keputusan brankas yang tidak boleh buta. */
interface VaultServer {
    /** Brankas sudah ada di server? Timeout 8 dtk → Unreachable. */
    suspend fun keyState(): RemoteKeyState
    /** `wrapped_key` di server; null = tidak ada; [UNREACHABLE] = tidak terjawab. */
    suspend fun wrappedKey(): String?
    /** Waktu server dari header Date + waktu kirim/terima, untuk koreksi jam TOTP. */
    suspend fun clockSample(): Triple<Long, Long, Long>?
}

class SupabaseVaultServer(private val supabase: SupabaseClient, private val supabaseUrl: String) : VaultServer {
    private suspend fun rows(column: String): List<JsonObject>? = withTimeoutOrNull(8_000) {
        runCatching { supabase.from("pw_keys").select(Columns.list(column)) { limit(1) }.decodeList<JsonObject>() }.getOrNull()
    }

    override suspend fun keyState(): RemoteKeyState {
        val r = rows("id") ?: return RemoteKeyState.Unreachable
        return if (r.isEmpty()) RemoteKeyState.None else RemoteKeyState.Exists
    }

    override suspend fun wrappedKey(): String? {
        val r = rows("wrapped_key") ?: return UNREACHABLE
        return r.firstOrNull()?.get("wrapped_key")?.jsonPrimitive?.content
    }

    override suspend fun clockSample(): Triple<Long, Long, Long>? = withContext(Dispatchers.IO) {
        runCatching {
            val sent = System.currentTimeMillis()
            val conn = (URL("${supabaseUrl.trimEnd('/')}/auth/v1/health").openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"; connectTimeout = 8_000; readTimeout = 8_000; useCaches = false
            }
            conn.responseCode
            val received = System.currentTimeMillis()
            val date = conn.getHeaderFieldDate("Date", 0)
            conn.disconnect()
            if (date == 0L) null else Triple(date, sent, received)
        }.getOrNull()
    }
}
