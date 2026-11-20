package com.rosyidputrar.taskify.core.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import java.net.URI

/** Penyedia AI yang didukung. Hampir semua layanan (OpenRouter, Groq, DeepSeek, Gemini, LM Studio…) memakai format OpenAI. */
enum class AiProvider(val label: String, val defaultBaseUrl: String, val hint: String) {
    OpenAiCompatible("OpenAI-compatible", "https://api.openai.com/v1", "OpenAI, OpenRouter, Groq, DeepSeek, Gemini, dll. Base URL biasanya berakhiran /v1."),
    Anthropic("Anthropic (Claude)", "https://api.anthropic.com", "API Claude langsung dari Anthropic."),
}

enum class AiRole { User, Assistant, Tool }

/** Permintaan alat dari model. [arguments] = JSON mentah dari model (bisa saja tidak valid). */
data class AiToolCall(val id: String, val name: String, val arguments: String)

/** Satu pesan percakapan dalam format netral (dipetakan ke format tiap penyedia). */
data class AiMessage(
    val role: AiRole,
    val content: String,
    val toolCalls: List<AiToolCall> = emptyList(),
    /** Untuk [AiRole.Tool]: id panggilan alat yang dijawab. */
    val toolCallId: String? = null,
    val toolName: String? = null,
)

/** Definisi alat yang boleh dipanggil model. [parameters] = JSON Schema objek. */
data class AiToolSpec(val name: String, val description: String, val parameters: JsonObject)

data class AiRequest(
    val model: String,
    val system: String?,
    val messages: List<AiMessage>,
    val tools: List<AiToolSpec> = emptyList(),
    val maxTokens: Int = 4096,
)

/** Kejadian selama streaming jawaban. */
sealed interface AiEvent {
    data class Text(val delta: String) : AiEvent
    /** Model meminta alat (dikirim sekali, setelah semua potongan argumen terkumpul). */
    data class ToolCalls(val calls: List<AiToolCall>) : AiEvent
    data class Done(val stopReason: String?) : AiEvent
}

enum class AiErrorKind { Config, Auth, Forbidden, NotFound, RateLimit, Server, Network, BadResponse }

/** Galat dengan pesan siap tampil + teks mentah (dipotong) untuk diagnosis. */
class AiException(val kind: AiErrorKind, message: String, val status: Int? = null, val raw: String? = null) : Exception(message)

interface AiClient {
    /** Daftar id model dari penyedia (urut abjad). */
    suspend fun models(): List<String>

    /** Jawaban sebagai aliran kejadian. Batalkan koleksinya untuk menghentikan. */
    fun stream(request: AiRequest): Flow<AiEvent>
}

object AiEndpoint {
    /**
     * Normalkan base URL: wajib HTTPS (Android memblokir HTTP biasa), tanpa garis miring di akhir,
     * tanpa query. Untuk Anthropic, akhiran "/v1" dibuang (ditambahkan sendiri saat memanggil).
     * @throws AiException [AiErrorKind.Config] dengan pesan jelas.
     */
    fun normalize(raw: String, provider: AiProvider): String {
        val text = raw.trim().trimEnd('/')
        if (text.isEmpty()) throw AiException(AiErrorKind.Config, "Isi base URL dulu.")
        val uri = try { URI(text) } catch (_: Exception) { throw AiException(AiErrorKind.Config, "Base URL tidak valid.") }
        when (uri.scheme?.lowercase()) {
            "https" -> Unit
            "http" -> throw AiException(AiErrorKind.Config, "Base URL harus HTTPS. Android memblokir koneksi HTTP biasa.")
            else -> throw AiException(AiErrorKind.Config, "Base URL harus diawali https://")
        }
        if (uri.host.isNullOrBlank()) throw AiException(AiErrorKind.Config, "Base URL tidak punya nama host.")
        if (uri.rawQuery != null || uri.rawFragment != null) throw AiException(AiErrorKind.Config, "Base URL tidak boleh memuat ? atau #.")
        return if (provider == AiProvider.Anthropic) text.removeSuffix("/v1") else text
    }

    /** "https://api.openai.com/v1" → "api.openai.com" (untuk penanda "data dikirim ke …"). */
    fun host(baseUrl: String): String = runCatching { URI(baseUrl.trim()).host }.getOrNull().orEmpty()
}
