package com.rosyidputrar.taskify.core.files

import com.rosyidputrar.taskify.core.domain.files.TempFiles
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** Isi snippet untuk penerima tautan publik (tanpa id atau pemilik). */
data class PublicSnippetInfo(val title: String, val language: String, val code: String, val description: String?, val updatedAt: String)

private val json = Json { ignoreUnknownKeys = true }

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content

/**
 * Gerbang ke Edge Function `snippets` (tautan publik snippet Vault, /snippet/<slug>).
 * Tabel `public_snippets` tidak disinkron; keadaan tautan dibaca dari server. Butuh internet
 * untuk perubahan; halaman publik dibaca tanpa login.
 */
class SnippetsApi(private val supabase: SupabaseClient?, private val supabaseUrl: String) {
    private val plain = HttpClient(OkHttp) {
        install(HttpTimeout) { requestTimeoutMillis = 20_000; connectTimeoutMillis = 15_000 }
        expectSuccess = false
    }

    private suspend fun call(body: JsonObject): JsonObject {
        val client = supabase ?: throw TempFileException("Server belum dikonfigurasi.")
        try {
            val response = client.functions.invoke("snippets") { setBody(TextContent(body.toString(), ContentType.Application.Json)) }
            return json.parseToJsonElement(response.bodyAsText()).jsonObject
        } catch (e: RestException) {
            val detail = runCatching { json.parseToJsonElement(e.response.bodyAsText()).jsonObject }.getOrNull()
            val code = detail?.str("code")
            throw TempFileException(TempFiles.errorMessage(code, detail?.str("error") ?: e.description ?: e.error), code, e)
        } catch (e: TempFileException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw TempFileException("Butuh koneksi internet.", cause = e)
        }
    }

    /** Cek ketersediaan nama custom; suggestion terisi bila nama sudah dipakai. */
    suspend fun checkSlug(slug: String): SlugCheck {
        val res = call(buildJsonObject { put("action", "checkSlug"); put("slug", slug) })
        return SlugCheck(
            available = (res["available"] as? JsonPrimitive)?.booleanOrNull ?: false,
            suggestion = res.str("suggestion"),
            error = res.str("error"),
        )
    }

    /** Pasang/ganti tautan publik snippet. */
    suspend fun setPublic(snippetId: String, slug: String): String =
        call(buildJsonObject { put("action", "setPublic"); put("snippetId", snippetId); put("slug", slug) }).str("slug")
            ?: throw TempFileException("Respons server tidak valid")

    /** Cabut tautan publik (mati seketika). */
    suspend fun revokePublic(snippetId: String) {
        call(buildJsonObject { put("action", "revokePublic"); put("snippetId", snippetId) })
    }

    /** Slug tautan publik saat ini, atau null bila snippet tidak publik. */
    suspend fun getPublic(snippetId: String): String? =
        call(buildJsonObject { put("action", "getPublic"); put("snippetId", snippetId) }).str("slug")

    /** Isi snippet untuk halaman publik — TANPA login. null = tautan tidak ada/sudah dicabut. */
    suspend fun publicInfo(slug: String): PublicSnippetInfo? {
        if (supabaseUrl.isBlank()) throw TempFileException("Server belum dikonfigurasi.")
        val res = try {
            plain.post("${supabaseUrl.trimEnd('/')}/functions/v1/snippets") {
                setBody(TextContent(buildJsonObject { put("action", "info"); put("slug", slug) }.toString(), ContentType.Application.Json))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw TempFileException("Tidak bisa menghubungi server.", cause = e)
        }
        if (res.status == HttpStatusCode.NotFound) return null
        if (!res.status.isSuccess()) throw TempFileException("Server menjawab ${res.status.value}")
        val o = json.parseToJsonElement(res.bodyAsText()).jsonObject
        return PublicSnippetInfo(
            o.str("title") ?: "Snippet", o.str("language") ?: "plaintext",
            o.str("code") ?: "", o.str("description"), o.str("updated_at") ?: "",
        )
    }
}
