package com.rosyidputrar.taskify.feature.ai

import com.rosyidputrar.taskify.core.ai.AiMessage
import com.rosyidputrar.taskify.core.ai.AiRole
import com.rosyidputrar.taskify.core.ai.AiToolCall
import com.rosyidputrar.taskify.core.store.ai.ChatMessage
import com.rosyidputrar.taskify.core.store.ai.ChatRole
import com.rosyidputrar.taskify.core.store.ai.ChatState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Penyimpanan & pemetaan riwayat chat ke permintaan model. Murni, tanpa Android. */
object ChatHistory {
    /** Pesan terakhir yang dikirim ke model (konteks); dipotong di awal pesan user. */
    const val MAX_MESSAGES = 40
    /** Hasil alat yang sangat panjang dipotong supaya tidak menghabiskan konteks. */
    const val MAX_TOOL_TEXT = 8_000

    private val json = Json { ignoreUnknownKeys = true }

    fun encodeCalls(calls: List<AiToolCall>): String? = if (calls.isEmpty()) null else buildJsonArray {
        calls.forEach { c -> add(buildJsonObject { put("id", c.id); put("name", c.name); put("arguments", c.arguments) }) }
    }.toString()

    fun decodeCalls(text: String?): List<AiToolCall> {
        if (text.isNullOrBlank()) return emptyList()
        val arr = runCatching { json.parseToJsonElement(text) as JsonArray }.getOrNull() ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = (o[k] as? JsonPrimitive)?.content
            val id = s("id") ?: return@mapNotNull null
            AiToolCall(id, s("name").orEmpty(), s("arguments").orEmpty())
        }
    }

    /**
     * Riwayat → pesan untuk model:
     *  - jawaban gagal / masih streaming tidak dikirim; jawaban yang dihentikan dikirim teksnya saja;
     *  - panggilan alat hanya dikirim bila hasilnya ada (pasangan selalu lengkap), hasil yatim dibuang;
     *  - hanya [MAX_MESSAGES] terakhir, dimulai dari pesan user (pasangan alat tidak terpotong).
     */
    fun toRequest(history: List<ChatMessage>): List<AiMessage> {
        val results = history.filter { it.role == ChatRole.Tool && it.toolCallId != null }.associateBy { it.toolCallId!! }
        val out = mutableListOf<AiMessage>()
        val sentCalls = HashSet<String>()
        history.forEach { m ->
            when (m.role) {
                ChatRole.User -> out += AiMessage(AiRole.User, m.content)
                ChatRole.Assistant -> {
                    if (m.state == ChatState.Error || m.state == ChatState.Streaming) return@forEach
                    val calls = if (m.state == ChatState.Stopped) emptyList() else decodeCalls(m.toolCalls).filter { it.id in results }
                    if (m.content.isBlank() && calls.isEmpty()) return@forEach
                    calls.forEach { sentCalls += it.id }
                    out += AiMessage(AiRole.Assistant, m.content, toolCalls = calls)
                }
                ChatRole.Tool -> {
                    val id = m.toolCallId ?: return@forEach
                    if (id !in sentCalls) return@forEach
                    out += AiMessage(AiRole.Tool, clip(m.content), toolCallId = id, toolName = m.toolName)
                }
            }
        }
        if (out.size <= MAX_MESSAGES) return out
        var start = out.size - MAX_MESSAGES
        while (start < out.size && out[start].role != AiRole.User) start++
        return if (start < out.size) out.subList(start, out.size).toList() else out.takeLast(1)
    }

    private fun clip(text: String) = if (text.length <= MAX_TOOL_TEXT) text else text.take(MAX_TOOL_TEXT) + "\n…(dipotong)"
}
