package com.rosyidputrar.taskify.core.ai

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/** Messages API Anthropic (`/v1/models`, `/v1/messages` + SSE). */
class AnthropicClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val http: HttpClient = AiHttp.shared(),
) : AiClient {

    private fun HttpRequestBuilder.auth() {
        header("x-api-key", apiKey)
        header("anthropic-version", VERSION)
    }

    override suspend fun models(): List<String> = AiHttp.guard {
        // Paginasi (`has_more` + `after_id`): tanpa ini model di atas 1000 pertama hilang diam-diam.
        val out = mutableListOf<String>()
        var afterId: String? = null
        repeat(MAX_MODEL_PAGES) {
            val url = "$baseUrl/v1/models?limit=1000" + (afterId?.let { "&after_id=$it" }.orEmpty())
            val response = http.get(url) { auth() }
            if (!response.status.isSuccess()) throw AiHttp.failure(response)
            val body = response.bodyAsText()
            val root = runCatching { AiJson.parseToJsonElement(body) as JsonObject }.getOrNull()
                ?: throw AiException(AiErrorKind.BadResponse, "Daftar model bukan JSON.", response.status.value, body.take(AiHttp.RAW_LIMIT))
            val page = (root["data"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.str("id") }
            out += page
            val hasMore = (root["has_more"] as? JsonPrimitive)?.contentOrNull == "true"
            afterId = if (hasMore) page.lastOrNull() else null
            if (afterId == null) return@guard out.distinct().sorted()
        }
        out.distinct().sorted()
    }

    override fun stream(request: AiRequest): Flow<AiEvent> = flow {
        AiHttp.guard {
            http.preparePost("$baseUrl/v1/messages") {
                auth()
                header(HttpHeaders.Accept, "text/event-stream")
                contentType(ContentType.Application.Json)
                setBody(body(request).toString())
            }.execute { response ->
                if (!response.status.isSuccess()) throw AiHttp.failure(response)
                val tools = sortedMapOf<Int, Triple<String, String, StringBuilder>>()
                var stop: String? = null
                AiHttp.readSse(response) { e ->
                    val o = runCatching { AiJson.parseToJsonElement(e.data) as JsonObject }.getOrNull() ?: return@readSse
                    when (o.str("type") ?: e.event) {
                        "content_block_start" -> {
                            val block = o["content_block"] as? JsonObject ?: return@readSse
                            val index = (o["index"] as? JsonPrimitive)?.intOrNull ?: return@readSse
                            if (block.str("type") == "tool_use") tools[index] = Triple(block.str("id") ?: "toolu_$index", block.str("name").orEmpty(), StringBuilder())
                            block.str("text")?.takeIf { it.isNotEmpty() }?.let { emit(AiEvent.Text(it)) }
                        }
                        "content_block_delta" -> {
                            val index = (o["index"] as? JsonPrimitive)?.intOrNull
                            val delta = o["delta"] as? JsonObject ?: return@readSse
                            when (delta.str("type")) {
                                "text_delta" -> delta.str("text")?.takeIf { it.isNotEmpty() }?.let { emit(AiEvent.Text(it)) }
                                "input_json_delta" -> index?.let { tools[it]?.third?.append(delta.str("partial_json").orEmpty()) }
                                else -> Unit // thinking_delta, signature_delta, citations… diabaikan
                            }
                        }
                        "message_delta" -> (o["delta"] as? JsonObject)?.str("stop_reason")?.let { stop = it }
                        "error" -> {
                            val err = o["error"] as? JsonObject
                            val kind = if (err?.str("type") == "overloaded_error" || err?.str("type") == "rate_limit_error") AiErrorKind.RateLimit else AiErrorKind.Server
                            throw AiException(kind, "Server Claude mengirim galat: ${err?.str("message") ?: "tanpa pesan"}", raw = e.data.take(AiHttp.RAW_LIMIT))
                        }
                        else -> Unit // message_start, ping, content_block_stop, message_stop
                    }
                }
                val calls = tools.values.filter { it.second.isNotBlank() }.map { (id, name, args) -> AiToolCall(id, name, args.toString().ifBlank { "{}" }) }
                if (calls.isNotEmpty()) emit(AiEvent.ToolCalls(calls))
                emit(AiEvent.Done(stop))
            }
        }
    }

    internal fun body(r: AiRequest): JsonObject = buildJsonObject {
        put("model", r.model)
        put("max_tokens", r.maxTokens)
        put("stream", true)
        if (!r.system.isNullOrBlank()) put("system", r.system)
        put("messages", messages(r.messages))
        if (r.tools.isNotEmpty()) put("tools", buildJsonArray {
            r.tools.forEach { t -> add(buildJsonObject { put("name", t.name); put("description", t.description); put("input_schema", t.parameters) }) }
        })
    }

    /**
     * Peran beruntun digabung (Anthropic wajib berselang-seling user/assistant); hasil alat dikirim
     * sebagai blok `tool_result` di pesan user; blok teks kosong dibuang (ditolak server).
     */
    internal fun messages(list: List<AiMessage>): JsonArray {
        val turns = mutableListOf<Pair<String, MutableList<JsonObject>>>()
        fun push(role: String, block: JsonObject) {
            val last = turns.lastOrNull()
            if (last != null && last.first == role) last.second += block else turns += role to mutableListOf(block)
        }
        list.forEach { m ->
            when (m.role) {
                AiRole.User -> if (m.content.isNotBlank()) push("user", text(m.content))
                AiRole.Assistant -> {
                    if (m.content.isNotBlank()) push("assistant", text(m.content))
                    m.toolCalls.forEach { c ->
                        val input = runCatching { AiJson.parseToJsonElement(c.arguments.ifBlank { "{}" }) as JsonObject }.getOrElse { JsonObject(emptyMap()) }
                        push("assistant", buildJsonObject { put("type", "tool_use"); put("id", c.id); put("name", c.name); put("input", input) })
                    }
                }
                AiRole.Tool -> push("user", buildJsonObject {
                    put("type", "tool_result")
                    put("tool_use_id", m.toolCallId ?: "")
                    put("content", m.content.ifBlank { "(kosong)" })
                })
            }
        }
        // Percakapan wajib diawali user.
        while (turns.isNotEmpty() && turns.first().first != "user") turns.removeAt(0)
        return buildJsonArray { turns.forEach { (role, blocks) -> add(buildJsonObject { put("role", role); put("content", JsonArray(blocks)) }) } }
    }

    private fun text(t: String) = buildJsonObject { put("type", "text"); put("text", t) }

    companion object {
        const val VERSION = "2023-06-01"
        /** Pengaman: jangan kejar halaman tanpa henti bila server selalu menjawab `has_more`. */
        const val MAX_MODEL_PAGES = 10
    }
}
