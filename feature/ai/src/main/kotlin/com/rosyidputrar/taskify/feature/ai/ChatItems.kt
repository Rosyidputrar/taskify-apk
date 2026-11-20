package com.rosyidputrar.taskify.feature.ai

import com.rosyidputrar.taskify.core.store.ai.ChatMessage
import com.rosyidputrar.taskify.core.store.ai.ChatRole
import com.rosyidputrar.taskify.core.store.ai.ChatState

enum class ToolStatus { Running, Done, Failed, Awaiting, Rejected, Skipped }

/** Satu panggilan alat yang terlihat di bawah jawaban asisten. */
data class ToolActivity(
    val callId: String,
    val name: String,
    val label: String,
    val status: ToolStatus,
    /** Hasil yang dikirim ke model (bisa dibuka pengguna). */
    val result: String? = null,
    /** Untuk alat tulis yang menunggu persetujuan. */
    val preview: ToolPreview? = null,
)

sealed interface ChatItem {
    val id: String

    data class User(override val id: String, val text: String, val editable: Boolean) : ChatItem

    data class Assistant(
        override val id: String,
        val text: String,
        val state: ChatState,
        val error: String?,
        /** Teks masih mengalir. */
        val streaming: Boolean,
        val tools: List<ToolActivity>,
        /** Jawaban terakhir di percakapan (boleh diulang). */
        val last: Boolean,
        /** Jawaban terpotong karena batas token (lihat [ChatEngine.TRUNCATED_NOTE]). */
        val truncated: Boolean = false,
    ) : ChatItem
}

/**
 * Riwayat → item tampilan. Pesan alat tidak tampil sendiri: hasilnya dilipat ke jawaban asisten
 * yang memintanya. [live] = teks streaming terbaru per id pesan.
 */
fun chatItems(messages: List<ChatMessage>, live: Map<String, String>, running: Boolean, toolbox: AiToolbox): List<ChatItem> {
    val results = messages.filter { it.role == ChatRole.Tool && it.toolCallId != null }.associateBy { it.toolCallId!! }
    val lastUser = messages.lastOrNull { it.role == ChatRole.User }?.id
    val lastAssistant = messages.lastOrNull { it.role == ChatRole.Assistant }?.id
    return messages.mapNotNull { m ->
        when (m.role) {
            ChatRole.User -> ChatItem.User(m.id, m.content, editable = m.id == lastUser && !running)
            ChatRole.Tool -> null
            ChatRole.Assistant -> {
                val tools = ChatHistory.decodeCalls(m.toolCalls).map { c ->
                    val r = results[c.id]
                    val status = when {
                        r != null -> when (r.state) {
                            ChatState.Error -> ToolStatus.Failed
                            ChatState.Stopped -> ToolStatus.Rejected
                            else -> ToolStatus.Done
                        }
                        m.state == ChatState.Awaiting && toolbox.kind(c.name) == ToolKind.Write -> ToolStatus.Awaiting
                        running && m.id == lastAssistant -> ToolStatus.Running
                        else -> ToolStatus.Skipped
                    }
                    ToolActivity(
                        c.id, c.name, toolbox.label(c.name), status, r?.content,
                        preview = if (toolbox.kind(c.name) == ToolKind.Write) toolbox.preview(c.name, c.arguments) else null,
                    )
                }
                ChatItem.Assistant(
                    id = m.id, text = live[m.id] ?: m.content, state = m.state, error = m.error,
                    streaming = m.state == ChatState.Streaming, tools = tools, last = m.id == lastAssistant,
                    truncated = m.content.endsWith(ChatEngine.TRUNCATED_NOTE),
                )
            }
        }
    }
}
