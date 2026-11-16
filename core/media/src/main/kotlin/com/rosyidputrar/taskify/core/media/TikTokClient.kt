package com.rosyidputrar.taskify.core.media

import com.rosyidputrar.taskify.core.domain.media.TikTok
import com.rosyidputrar.taskify.core.domain.media.TikTokAsset
import com.rosyidputrar.taskify.core.domain.media.TikTokPost
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.OutputStream

class TikTokException(message: String, val code: String = "UNKNOWN", val status: Int = 0) : Exception(message)

/**
 * Klien Worker ttdl-taskify (port `api-client.ts`). Worker hanya melayani Origin Taskify; di
 * browser header itu dipasang otomatis, di aplikasi ini dikirim eksplisit dari PUBLIC_WEB_ORIGIN.
 */
class TikTokClient(private val baseUrl: String, private val origin: String) {
    private val http = HttpClient(OkHttp) {
        install(HttpTimeout) { requestTimeoutMillis = 10 * 60_000L; connectTimeoutMillis = 15_000; socketTimeoutMillis = 60_000 }
        expectSuccess = false
    }
    private val json = Json { ignoreUnknownKeys = true }
    private val base = baseUrl.trimEnd('/')

    val configured: Boolean get() = base.isNotBlank()

    private suspend fun error(res: HttpResponse): TikTokException {
        var code = "UNKNOWN"
        var message = "Server TikTok membalas HTTP ${res.status.value}."
        runCatching {
            val e = json.parseToJsonElement(res.bodyAsText()).jsonObject["error"] as? JsonObject
            (e?.get("code") as? JsonPrimitive)?.content?.let { code = it }
            (e?.get("message") as? JsonPrimitive)?.content?.let { message = it }
        }
        if (code == "ORIGIN_NOT_ALLOWED") message = "Server TikTok menolak alamat aplikasi ini. Tambahkan origin Taskify (PUBLIC_WEB_ORIGIN) ke ALLOWED_ORIGINS Worker ttdl-taskify."
        return TikTokException(message, code, res.status.value)
    }

    private inline fun <T> network(block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: TikTokException) {
        throw e
    } catch (e: Exception) {
        throw TikTokException("Tidak bisa menghubungi server TikTok. Periksa koneksi.", "NETWORK")
    }

    suspend fun extract(input: String): TikTokPost = network {
        val res = http.post("$base/v1/extract") {
            if (origin.isNotBlank()) header("Origin", origin)
            setBody(TextContent(buildJsonObject { put("url", input) }.toString(), ContentType.Application.Json))
        }
        if (!res.status.isSuccess()) throw error(res)
        parse(json.parseToJsonElement(res.bodyAsText()).jsonObject["data"]?.jsonObject ?: throw TikTokException("Respons server tidak valid"))
    }

    /** Unduh ke [open] dengan progres; mengembalikan nama berkas dari Content-Disposition. */
    suspend fun download(url: String, fallbackName: String, open: () -> OutputStream, onProgress: (Float?) -> Unit = {}): String = network {
        http.prepareGet(url) { if (origin.isNotBlank()) header("Origin", origin) }.execute { res ->
            if (!res.status.isSuccess()) throw error(res)
            val name = TikTok.fileNameFromDisposition(res.headers["Content-Disposition"], fallbackName)
            val total = res.headers["Content-Length"]?.toLongOrNull()
            val channel = res.bodyAsChannel()
            open().use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = channel.readAvailable(buf, 0, buf.size)
                    if (n < 0) break
                    if (n == 0) { if (channel.isClosedForRead) break else continue }
                    out.write(buf, 0, n)
                    done += n
                    onProgress(total?.takeIf { it > 0 }?.let { (done.toFloat() / it).coerceAtMost(1f) })
                }
            }
            name
        }
    }

    /**
     * Unduh satu aset; link yang sudah > 8 menit diperbarui dulu, dan bila tetap ditolak sebagai
     * kedaluwarsa diperbarui SEKALI lagi (port `downloadAsset`).
     */
    suspend fun downloadAsset(
        current: Pair<TikTokPost, Long>, asset: TikTokAsset, refresh: suspend () -> TikTokPost,
        open: (String) -> OutputStream, onProgress: (Float?) -> Unit = {}, now: () -> Long = System::currentTimeMillis,
    ): String {
        var post = current.first
        var refreshed = false
        if (now() - current.second > TikTok.LINK_MAX_AGE_MS) { post = refresh(); refreshed = true }
        suspend fun attempt(p: TikTokPost): String {
            val url = TikTok.assetUrl(p, asset) ?: throw TikTokException("File ini tidak tersedia untuk postingan ini.")
            val fallback = TikTok.fallbackName(p, asset)
            return download(url, fallback, { open(fallback) }, onProgress)
        }
        return try {
            attempt(post)
        } catch (e: TikTokException) {
            if (refreshed || !TikTok.isStale(e.code)) throw e
            attempt(refresh())
        }
    }

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.obj(k: String) = this[k] as? JsonObject
    private fun JsonObject.long(k: String) = (this[k] as? JsonPrimitive)?.longOrNull ?: 0L

    private fun parse(d: JsonObject): TikTokPost {
        val author = d.obj("author")
        val stats = d.obj("stats")
        val music = d.obj("music")
        val dl = d.obj("downloads")
        return TikTokPost(
            id = d.str("id").orEmpty(), type = d.str("type") ?: "video", description = d.str("description").orEmpty(),
            createdAt = d.long("created_at"), canonicalUrl = d.str("canonical_url").orEmpty(),
            authorId = author?.str("unique_id").orEmpty(), authorName = author?.str("nickname").orEmpty(),
            verified = (author?.get("verified") as? JsonPrimitive)?.booleanOrNull ?: false,
            plays = stats?.long("plays") ?: 0, likes = stats?.long("likes") ?: 0, comments = stats?.long("comments") ?: 0, shares = stats?.long("shares") ?: 0,
            durationSeconds = (d.obj("video")?.get("duration_seconds") as? JsonPrimitive)?.intOrNull,
            musicTitle = music?.str("title"), musicAuthor = music?.str("author"),
            hashtags = (d["hashtags"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content },
            cover = d.str("cover"),
            videoUrl = dl?.str("video"), photoUrls = (dl?.get("photos") as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content },
            audioUrl = dl?.str("audio"),
        )
    }
}
