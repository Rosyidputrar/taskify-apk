package com.rosyidputrar.taskify.core.ai

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * Format OpenAI Chat Completions (`/models`, `/chat/completions` + SSE). Dipakai OpenAI, OpenRouter,
 * Groq, DeepSeek, Gemini (endpoint kompatibel), Mistral, server lokal, dll.
 */
class OpenAiCompatClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val http: HttpClient = AiHttp.shared(),
) : AiClient {

    override suspend fun models(): List<String> = AiHttp.guard {
        val response = http.get("$baseUrl/models") { header(HttpHeaders.Authorization, "Bearer $apiKey") }
        if (!response.status.isSuccess()) throw AiHttp.failure(response)
        val body = response.bodyAsText()
        val root = runCatching { AiJson.parseToJsonElement(body) }.getOrNull()
            ?: throw AiException(AiErrorKind.BadResponse, "Daftar model bukan JSON. Periksa base URL (biasanya berakhiran /v1).", response.status.value, body.take(AiHttp.RAW_LIMIT))
        val list = when (root) {
            is JsonObject -> root["data"] as? JsonArray ?: root["models"] as? JsonArray
            is JsonArray -> root
            else -> null
        } ?: throw AiException(AiErrorKind.BadResponse, "Format daftar model tidak dikenal.", response.status.value, body.take(AiHttp.RAW_LIMIT))
        list.mapNotNull { (it as? JsonObject)?.str("id") ?: (it as? JsonPrimitive)?.contentOrNull }.filter { it.isNotBlank() }.distinct().sorted()
    }

    override fun stream(request: AiRequest): Flow<AiEvent> = flow {
        AiHttp.guard {
            http.preparePost("$baseUrl/chat/completions") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                header(HttpHeaders.Accept, "text/event-stream")
                contentType(ContentType.Application.Json)
                setBody(body(request).toString())
            }.execute { response ->
                if (!response.status.isSuccess()) throw AiHttp.failure(response)
                val calls = ToolCallBuffer()
                var finish: String? = null
                val type = response.headers[HttpHeaders.ContentType].orEmpty()
                if (type.startsWith("application/json")) {
                    // Server mengabaikan "stream": baca jawaban utuh.
                    val o = parse(response.bodyAsText())
                    // Respons 200 yang berisi `error` (beberapa gateway/proxy) jangan
                    // dibaca sebagai jawaban kosong — perlakukan seperti galat SSE.
                    (o["error"] as? JsonObject)?.let { err ->
                        throw AiException(AiErrorKind.Server, "Server AI mengirim galat: ${err.str("message") ?: "tanpa pesan"}", raw = o.toString().take(AiHttp.RAW_LIMIT))
                    }
                    (o["error"] as? JsonPrimitive)?.let { err ->
                        throw AiException(AiErrorKind.Server, "Server AI mengirim galat: ${err.content}", raw = o.toString().take(AiHttp.RAW_LIMIT))
                    }
                    val choice = (o["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
                    val message = choice?.get("message") as? JsonObject
                    message?.str("content")?.takeIf { it.isNotEmpty() }?.let { emit(AiEvent.Text(it)) }
                    (message?.get("tool_calls") as? JsonArray)?.let(calls::add)
                    finish = choice?.str("finish_reason")
                } else {
                    AiHttp.readSse(response) { e ->
                        if (e.data == "[DONE]") return@readSse
                        val o = parse(e.data)
                        (o["error"] as? JsonObject)?.let { err ->
                            throw AiException(AiErrorKind.Server, "Server AI mengirim galat: ${err.str("message") ?: "tanpa pesan"}", raw = e.data.take(AiHttp.RAW_LIMIT))
                        }
                        val choice = (o["choices"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return@readSse
                        val delta = choice["delta"] as? JsonObject
                        delta?.str("content")?.takeIf { it.isNotEmpty() }?.let { emit(AiEvent.Text(it)) }
                        (delta?.get("tool_calls") as? JsonArray)?.let(calls::add)
                        choice.str("finish_reason")?.let { finish = it }
                    }
                }
                val done = calls.build()
                if (done.isNotEmpty()) emit(AiEvent.ToolCalls(done))
                emit(AiEvent.Done(finish))
            }
        }
    }

    private fun parse(text: String): JsonObject = runCatching { AiJson.parseToJsonElement(text) as JsonObject }.getOrElse {
        throw AiException(AiErrorKind.BadResponse, "Potongan jawaban bukan JSON yang valid.", raw = text.take(AiHttp.RAW_LIMIT))
    }

    internal fun body(r: AiRequest): JsonObject = buildJsonObject {
        put("model", r.model)
        put("stream", true)
        // Batas keluaran (nama modern Chat Completions; `max_tokens` lama ditolak
        // sebagian server untuk model baru). Tanpa ini jawaban tak berbatas.
        put("max_completion_tokens", r.maxTokens)
        put("messages", buildJsonArray {
            if (!r.system.isNullOrBlank()) add(buildJsonObject { put("role", "system"); put("content", r.system) })
            r.messages.forEach { m -> add(message(m)) }
        })
        if (r.tools.isNotEmpty()) {
            put("tools", buildJsonArray {
                r.tools.forEach { t ->
                    add(buildJsonObject {
                        put("type", "function")
                        put("function", buildJsonObject { put("name", t.name); put("description", t.description); put("parameters", t.parameters) })
                    })
                }
            })
        }
    }

    private fun message(m: AiMessage): JsonObject = buildJsonObject {
        when (m.role) {
            AiRole.User -> { put("role", "user"); put("content", m.content) }
            AiRole.Assistant -> {
                put("role", "assistant")
                put("content", m.content)
                if (m.toolCalls.isNotEmpty()) put("tool_calls", buildJsonArray {
                    m.toolCalls.forEach { c ->
                        add(buildJsonObject {
                            put("id", c.id); put("type", "function")
                            put("function", buildJsonObject { put("name", c.name); put("arguments", c.arguments.ifBlank { "{}" }) })
                        })
                    }
                })
            }
            AiRole.Tool -> {
                put("role", "tool")
                put("tool_call_id", m.toolCallId ?: "")
                put("content", m.content)
            }
        }
    }

    /** Potongan `tool_calls` per indeks digabung (argumen datang sepotong-sepotong). */
    private class ToolCallBuffer {
        private class Part(var id: String? = null, var name: String = "", val args: StringBuilder = StringBuilder())
        private val parts = sortedMapOf<Int, Part>()

        fun add(list: JsonArray) {
            list.forEachIndexed { pos, el ->
                val o = el as? JsonObject ?: return@forEachIndexed
                val index = (o["index"] as? JsonPrimitive)?.intOrNull ?: pos
                val part = parts.getOrPut(index) { Part() }
                o.str("id")?.takeIf { it.isNotBlank() }?.let { part.id = it }
                val fn = o["function"] as? JsonObject ?: return@forEachIndexed
                fn.str("name")?.takeIf { it.isNotBlank() }?.let { part.name = it }
                when (val a = fn["arguments"]) {
                    is JsonPrimitive -> if (a !is JsonNull) part.args.append(a.content)
                    is JsonObject -> part.args.append(a.toString()) // beberapa server mengirim objek, bukan string
                    else -> Unit
                }
            }
        }

        fun build(): List<AiToolCall> = parts.entries.filter { it.value.name.isNotBlank() }.map { (i, p) -> AiToolCall(p.id ?: "call_$i", p.name, p.args.toString()) }
    }
}

internal fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

internal fun JsonElement?.asObject(): JsonObject? = this as? JsonObject
