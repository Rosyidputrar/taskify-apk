package com.rosyidputrar.taskify.core.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException

internal val AiJson = Json { ignoreUnknownKeys = true; isLenient = true }

internal object AiHttp {
    /**
     * Satu klien HTTP untuk seluruh proses (E-M1): satu connection pool + dispatcher OkHttp,
     * bukan satu per giliran chat. [HttpClient] Ktor aman dipakai dari banyak coroutine.
     * Ditutup sekali saat proses mati (OS juga membereskannya); [closeShared] ada untuk
     * jalur keluar akun / tes. Setelah ditutup, [shared] membuat yang baru bila dibutuhkan.
     */
    private var sharedClient: HttpClient? = null

    @Synchronized
    fun shared(): HttpClient {
        sharedClient?.let { return it }
        return client().also { sharedClient = it }
    }

    @Synchronized
    fun closeShared() {
        sharedClient?.close()
        sharedClient = null
    }

    /** Klien bawaan: tanpa batas total (jawaban streaming bisa lama), tapi diam > 5 mnt = galat. */
    fun client(): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            // Batas diam ANTAR token, bukan total: model reasoning boleh diam lama sebelum
            // token pertama / antar penalaran, tapi 5 menit tanpa token apa pun dianggap macet.
            socketTimeoutMillis = 300_000
            requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
        }
    }

    const val RAW_LIMIT = 2_000

    /** Ubah respons gagal (bukan 2xx) menjadi [AiException] dengan pesan yang jelas. */
    suspend fun failure(response: HttpResponse): AiException {
        val status = response.status.value
        val body = runCatching { response.bodyAsText() }.getOrDefault("").take(RAW_LIMIT)
        val server = serverMessage(body)?.let { " Pesan server: ${it.take(300)}" } ?: ""
        val retryAfterMs = response.headers["Retry-After"]?.trim()?.toLongOrNull()
            ?.coerceIn(0, 60)?.times(1_000)
        // Konteks kepanjangan: "Coba lagi" tidak akan pernah berhasil — beri tahu yang harus dilakukan.
        if (status in 400..422 && isContextTooLong(body)) {
            return AiException(
                AiErrorKind.Config,
                "Riwayat terlalu panjang untuk model ini. Mulai percakapan baru atau minta ringkasan dulu, lalu lanjut di chat baru.$server",
                status, body, retryAfterMs,
            )
        }
        val (kind, text) = when (status) {
            401 -> AiErrorKind.Auth to "API key ditolak (401). Periksa kuncinya."
            403 -> AiErrorKind.Forbidden to "Akses ditolak (403). Kunci ini tidak punya izin untuk layanan atau model tersebut."
            404 -> AiErrorKind.NotFound to "Alamat atau model tidak ditemukan (404). Periksa base URL dan nama model."
            408, 504 -> AiErrorKind.Network to "Server AI tidak menjawab tepat waktu ($status)."
            429 -> AiErrorKind.RateLimit to "Terlalu banyak permintaan atau kuota habis (429). Coba lagi nanti."
            400, 413, 422 -> AiErrorKind.Config to "Permintaan ditolak server ($status)."
            in 500..599 -> AiErrorKind.Server to "Server AI sedang bermasalah ($status)."
            else -> AiErrorKind.BadResponse to "Jawaban server tidak terduga ($status)."
        }
        return AiException(kind, text + server, status, body, retryAfterMs)
    }

    /** Benar bila badan galat menyebut konteks/token habis (format OpenAI & Anthropic). */
    fun isContextTooLong(body: String): Boolean {
        val b = body.lowercase()
        return "context_length_exceeded" in b || "context length" in b ||
            ("maximum context length" in b) || ("max_tokens" in b && "exceed" in b) ||
            ("input is too long" in b)
    }

    /** Ambil `error.message` / `error` / `message` dari badan galat JSON (format OpenAI & Anthropic). */
    fun serverMessage(body: String): String? {
        val o = runCatching { AiJson.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return body.trim().takeIf { it.isNotEmpty() && !it.startsWith("<") }
        val err = o["error"]
        return when {
            err is JsonObject -> (err["message"] as? JsonPrimitive)?.content
            err is JsonPrimitive -> err.content
            else -> (o["message"] as? JsonPrimitive)?.content
        }?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Baca badan streaming baris demi baris ke [parser]; setiap kejadian SSE diteruskan ke [onEvent]. */
    suspend fun readSse(response: HttpResponse, onEvent: suspend (SseEvent) -> Unit) {
        val parser = SseParser()
        val channel = response.bodyAsChannel()
        while (true) {
            val line = channel.readUTF8Line() ?: break
            for (e in parser.feed(line + "\n")) onEvent(e)
        }
        for (e in parser.finish()) onEvent(e)
    }

    /** Bungkus galat jaringan menjadi [AiException]; pembatalan diteruskan apa adanya. */
    suspend fun <T> guard(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: AiException) {
        throw e
    } catch (e: HttpRequestTimeoutException) {
        throw AiException(AiErrorKind.Network, "Server AI tidak menjawab tepat waktu.")
    } catch (e: java.net.SocketTimeoutException) {
        throw AiException(AiErrorKind.Network, "Server AI berhenti mengirim jawaban (timeout).")
    } catch (e: java.net.UnknownHostException) {
        throw AiException(AiErrorKind.Network, "Server tidak ditemukan. Periksa base URL dan koneksi internet.")
    } catch (e: javax.net.ssl.SSLException) {
        throw AiException(AiErrorKind.Network, "Sambungan aman (HTTPS) gagal: ${e.message ?: "sertifikat ditolak"}.")
    } catch (e: IOException) {
        throw AiException(AiErrorKind.Network, "Tidak bisa menghubungi server AI: ${e.message ?: "koneksi terputus"}.")
    }
}
