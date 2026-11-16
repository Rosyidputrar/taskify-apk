package com.rosyidputrar.taskify.core.files

import com.rosyidputrar.taskify.core.domain.files.ExpiryOption
import com.rosyidputrar.taskify.core.domain.files.TempFile
import com.rosyidputrar.taskify.core.domain.files.TempFileStatus
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
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Galat Edge Function `tempfiles` dengan kode TFxxx (bila ada) dan pesan siap tampil. */
class TempFileException(message: String, val code: String? = null, cause: Throwable? = null) : Exception(message, cause)

/** Info berkas untuk penerima tautan (tanpa id, key, atau pemilik). */
data class PublicFileInfo(val fileName: String, val mimeType: String, val sizeBytes: Long, val expiresAt: String?, val createdAt: String)

/** Perubahan satu berkas. [regenerate] = buat tautan baru; tautan lama langsung tidak berlaku. */
data class TempFilePatch(val name: String? = null, val isPublic: Boolean? = null, val expiry: ExpiryOption? = null, val regenerate: Boolean = false)

private val json = Json { ignoreUnknownKeys = true }

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content

/** Baris dari server (boolean asli Postgres, size bisa string) → model lokal. */
internal fun JsonObject.toTempFile() = TempFile(
    id = str("id") ?: throw TempFileException("Respons server tidak valid"),
    fileName = str("file_name") ?: "berkas",
    mimeType = str("mime_type") ?: "application/octet-stream",
    sizeBytes = (this["size_bytes"] as? JsonPrimitive)?.let { it.longOrNull ?: it.content.toLongOrNull() } ?: 0,
    status = TempFileStatus.from(str("status")),
    shareToken = str("share_token") ?: "",
    isPublic = (this["is_public"] as? JsonPrimitive)?.let { it.booleanOrNull ?: (it.longOrNull == 1L) } ?: false,
    expiresAt = str("expires_at"),
    downloadCount = (this["download_count"] as? JsonPrimitive)?.longOrNull?.toInt() ?: 0,
    lastDownloadAt = str("last_download_at"),
    createdAt = str("created_at") ?: "",
    updatedAt = str("updated_at") ?: "",
    deletedAt = str("deleted_at"),
)

/**
 * Gerbang ke Edge Function `tempfiles` (sama dengan `tempfile-api.ts` webapp). Tabel `temp_files`
 * hanya dibaca dari database lokal; SEMUA perubahan lewat sini karena kuota dan pencabutan tautan
 * harus berlaku di server saat itu juga. Butuh internet.
 */
class TempFilesApi(private val supabase: SupabaseClient?, private val supabaseUrl: String) {
    private val plain = HttpClient(OkHttp) {
        install(HttpTimeout) { requestTimeoutMillis = 20_000; connectTimeoutMillis = 15_000 }
        expectSuccess = false
    }

    private suspend fun call(body: JsonObject): JsonObject {
        val client = supabase ?: throw TempFileException("Server belum dikonfigurasi.")
        try {
            val response = client.functions.invoke("tempfiles") { setBody(TextContent(body.toString(), ContentType.Application.Json)) }
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

    private fun JsonObject.file(): TempFile = (this["file"] as? JsonObject)?.toTempFile() ?: throw TempFileException("Respons server tidak valid")

    /** Pesan tempat (kuota dicek atomik di server) → baris pending + URL PUT. */
    suspend fun create(name: String, size: Long, mime: String, expiry: ExpiryOption): Pair<TempFile, String> {
        val res = call(buildJsonObject {
            put("action", "create"); put("name", name); put("size", size); put("mime", mime); put("expiry", expiry.value)
        })
        val url = res.str("uploadUrl") ?: throw TempFileException("Respons server tidak valid")
        return res.file() to url
    }

    /** Server memeriksa objek di R2 (ukuran) lalu menandai siap. */
    suspend fun complete(id: String): TempFile = call(buildJsonObject { put("action", "complete"); put("id", id) }).file()

    suspend fun update(id: String, patch: TempFilePatch): TempFile = call(buildJsonObject {
        put("action", "update"); put("id", id)
        patch.name?.let { put("name", it) }
        patch.isPublic?.let { put("isPublic", it) }
        patch.expiry?.let { put("expiry", it.value) }
        if (patch.regenerate) put("regenerate", true)
    }).file()

    suspend fun delete(id: String) {
        call(buildJsonObject { put("action", "delete"); put("id", id) })
    }

    /** Presigned GET untuk pemilik (Content-Disposition: attachment). */
    suspend fun ownerDownloadUrl(id: String): String =
        call(buildJsonObject { put("action", "download"); put("id", id) }).str("url") ?: throw TempFileException("Respons server tidak valid")

    /**
     * Info untuk halaman publik — TANPA login (sama dengan webapp: tidak membawa sesi siapa pun).
     * null = tidak ada / kedaluwarsa / tidak publik.
     */
    suspend fun publicInfo(token: String): PublicFileInfo? {
        if (supabaseUrl.isBlank()) throw TempFileException("Server belum dikonfigurasi.")
        val res = try {
            plain.post("${supabaseUrl.trimEnd('/')}/functions/v1/tempfiles") {
                setBody(TextContent(buildJsonObject { put("action", "info"); put("token", token) }.toString(), ContentType.Application.Json))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw TempFileException("Tidak bisa menghubungi server.", cause = e)
        }
        if (res.status == HttpStatusCode.NotFound) return null
        if (!res.status.isSuccess()) throw TempFileException("Server menjawab ${res.status.value}")
        val o = json.parseToJsonElement(res.bodyAsText()).jsonObject
        return PublicFileInfo(
            o.str("file_name") ?: "berkas", o.str("mime_type") ?: "application/octet-stream",
            (o["size_bytes"] as? JsonPrimitive)?.let { it.longOrNull ?: it.content.toLongOrNull() } ?: 0,
            o.str("expires_at"), o.str("created_at") ?: "",
        )
    }

    fun directDownloadUrl(token: String) = TempFiles.directDownloadUrl(supabaseUrl, token)
}

