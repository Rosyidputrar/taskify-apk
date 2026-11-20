package com.rosyidputrar.taskify.feature.ai

import com.rosyidputrar.taskify.core.ai.AiClient
import com.rosyidputrar.taskify.core.ai.AiEvent
import com.rosyidputrar.taskify.core.ai.AiRequest
import com.rosyidputrar.taskify.core.ai.AiToolSpec
import com.rosyidputrar.taskify.core.store.ai.ChatMessage
import com.rosyidputrar.taskify.core.store.ai.ChatRole
import com.rosyidputrar.taskify.core.store.ai.ChatSession
import com.rosyidputrar.taskify.core.store.ai.ChatState
import com.rosyidputrar.taskify.core.store.ai.ChatStore
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Penyimpanan memori dengan perilaku sama seperti AiRepository (seq, status). */
class FakeChatStore : ChatStore {
    val sessions = mutableMapOf<String, ChatSession>()
    val list = mutableListOf<ChatMessage>()
    private var n = 0

    fun newSession(id: String = "s1", dataAccess: Boolean = false, model: String = "m", provider: String = "OpenAiCompatible") =
        ChatSession(id, "", provider, model, "Kamu asisten. {hari}", dataAccess, 0, 0).also { sessions[id] = it }

    override suspend fun session(id: String) = sessions[id]
    override suspend fun messages(sessionId: String) = list.filter { it.sessionId == sessionId }.sortedBy { it.seq }
    override suspend fun addMessage(sessionId: String, role: ChatRole, content: String, state: ChatState, toolCalls: String?, toolCallId: String?, toolName: String?): ChatMessage {
        val seq = (list.filter { it.sessionId == sessionId }.maxOfOrNull { it.seq } ?: 0) + 1
        return ChatMessage("m${++n}", sessionId, seq, role, content, toolCalls, toolCallId, toolName, state).also { list += it }
    }
    override suspend fun updateMessage(id: String, content: String?, state: ChatState?, error: String?, toolCalls: String?, clearError: Boolean) {
        val i = list.indexOfFirst { it.id == id }
        val m = list[i]
        list[i] = m.copy(content = content ?: m.content, state = state ?: m.state, error = if (clearError) null else error ?: m.error, toolCalls = toolCalls ?: m.toolCalls)
    }
    override suspend fun deleteFrom(sessionId: String, fromSeq: Int) { list.removeAll { it.sessionId == sessionId && it.seq >= fromSeq } }
    override suspend fun recoverInterrupted() = 0

    fun roles(s: String = "s1") = list.filter { it.sessionId == s }.sortedBy { it.seq }.map { it.role to it.state }
}

/** Klien skenario: setiap panggilan stream() memakai skenario berikutnya. */
class ScriptedClient(vararg scripts: suspend kotlinx.coroutines.flow.FlowCollector<AiEvent>.() -> Unit) : AiClient {
    private val queue = ArrayDeque(scripts.toList())
    val requests = mutableListOf<AiRequest>()
    var models = listOf("m")
    override suspend fun models() = models
    override fun stream(request: AiRequest): Flow<AiEvent> = flow {
        requests += request
        val script = queue.removeFirstOrNull() ?: { emit(AiEvent.Text("(tidak ada skenario)")); emit(AiEvent.Done("stop")) }
        script()
    }
}

/** Alat palsu: "baca" (Read) dan "simpan" (Write). */
class FakeToolbox : AiToolbox {
    val ran = mutableListOf<Pair<String, String>>()
    var fail = false
    /** Pemeriksa argumen alat tulis (bawaan: diterima apa adanya). */
    var check: (String) -> Prepared = { Prepared.Ready(it) }
    override suspend fun prepare(name: String, arguments: String) = check(arguments)
    override fun specs() = listOf(
        AiToolSpec("baca", "Baca data", buildJsonObject { put("type", "object") }),
        AiToolSpec("simpan", "Simpan data", buildJsonObject { put("type", "object") }),
    )
    override fun kind(name: String) = when (name) { "baca" -> ToolKind.Read; "simpan" -> ToolKind.Write; else -> null }
    override fun label(name: String) = if (name == "baca") "Membaca data" else "Menyimpan"
    override fun preview(name: String, arguments: String) = ToolPreview("Simpan?", listOf("Isi" to arguments))
    override suspend fun run(name: String, arguments: String): ToolResult {
        ran += name to arguments
        if (fail) throw IllegalStateException("database terkunci")
        return ToolResult("hasil $name", "1 hasil")
    }
}

suspend fun kotlinx.coroutines.flow.FlowCollector<AiEvent>.hang(): Nothing = awaitCancellation()
