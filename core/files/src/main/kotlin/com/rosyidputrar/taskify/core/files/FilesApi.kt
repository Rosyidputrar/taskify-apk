package com.rosyidputrar.taskify.core.files

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Galat transfer file dengan pesan siap tampil. */
class FileTransferException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Gerbang ke Edge Function `files` (sama dengan webapp): kunci R2 hanya ada di server, aplikasi
 * hanya menerima presigned URL berumur pendek. Token sesi (aal2) dikirim otomatis oleh Supabase.
 */
class FilesApi(private val supabase: SupabaseClient) {
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun call(body: JsonObject): JsonObject {
        try {
            val response = supabase.functions.invoke("files") {
                setBody(TextContent(body.toString(), ContentType.Application.Json))
            }
            return json.parseToJsonElement(response.bodyAsText()).jsonObject
        } catch (e: RestException) {
            val detail = runCatching { json.parseToJsonElement(e.response.bodyAsText()).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
            throw FileTransferException(detail ?: e.description ?: e.error, e)
        } catch (e: FileTransferException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw FileTransferException("Butuh koneksi internet", e)
        }
    }

    private fun JsonObject.url(): String =
        (this["url"] as? JsonPrimitive)?.content ?: throw FileTransferException((this["error"] as? JsonPrimitive)?.content ?: "Respons server tidak valid")

    /** URL PUT (berlaku 15 menit); Content-Length ikut ditandatangani → ukuran harus sama persis. */
    suspend fun uploadUrl(key: String, size: Long, contentType: String): String = call(
        buildJsonObject {
            put("action", "upload"); put("key", key); put("size", size); put("contentType", contentType)
        },
    ).url()

    /** URL GET (berlaku 10 menit). */
    suspend fun downloadUrl(key: String, fileName: String, inline: Boolean = true): String = call(
        buildJsonObject {
            put("action", "download"); put("key", key); put("fileName", fileName); put("inline", inline)
        },
    ).url()

    suspend fun delete(key: String) {
        call(buildJsonObject { put("action", "delete"); put("key", key) })
    }
}
