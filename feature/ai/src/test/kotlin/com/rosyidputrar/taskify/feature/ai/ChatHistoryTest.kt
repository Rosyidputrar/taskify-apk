package com.rosyidputrar.taskify.feature.ai

import com.rosyidputrar.taskify.core.ai.AiRole
import com.rosyidputrar.taskify.core.ai.AiToolCall
import com.rosyidputrar.taskify.core.store.ai.ChatMessage
import com.rosyidputrar.taskify.core.store.ai.ChatRole
import com.rosyidputrar.taskify.core.store.ai.ChatState
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatHistoryTest {
    private var seq = 0
    private fun m(role: ChatRole, content: String, state: ChatState = ChatState.Done, calls: List<AiToolCall> = emptyList(), callId: String? = null) =
        ChatMessage("id${++seq}", "s", seq, role, content, ChatHistory.encodeCalls(calls), callId, null, state)

    @Test fun callsRoundTrip() {
        val calls = listOf(AiToolCall("a", "cari", "{\"q\":\"x\\\"y\"}"), AiToolCall("b", "tugas", ""))
        assertEquals(calls, ChatHistory.decodeCalls(ChatHistory.encodeCalls(calls)))
        assertEquals(null, ChatHistory.encodeCalls(emptyList()))
        assertEquals(emptyList(), ChatHistory.decodeCalls("rusak"))
    }

    @Test fun failedStoppedAndOrphanMessagesAreFiltered() {
        val history = listOf(
            m(ChatRole.User, "hai"),
            m(ChatRole.Assistant, "gagal", ChatState.Error),
            m(ChatRole.Assistant, "setengah", ChatState.Stopped, calls = listOf(AiToolCall("x", "cari", "{}"))),
            m(ChatRole.Tool, "yatim", callId = "zzz"),
            m(ChatRole.User, "lanjut"),
            m(ChatRole.Assistant, "", calls = listOf(AiToolCall("c1", "cari", "{}"), AiToolCall("c2", "cari", "{}"))),
            m(ChatRole.Tool, "hasil c1", callId = "c1"), // c2 tanpa hasil → panggilannya tidak dikirim
            m(ChatRole.Assistant, "", ChatState.Streaming),
        )
        val req = ChatHistory.toRequest(history)
        assertEquals(listOf(AiRole.User, AiRole.Assistant, AiRole.User, AiRole.Assistant, AiRole.Tool), req.map { it.role })
        assertEquals("setengah", req[1].content)
        assertEquals(emptyList(), req[1].toolCalls) // jawaban dihentikan: teks saja
        assertEquals(listOf("c1"), req[3].toolCalls.map { it.id })
    }

    @Test fun contextIsTrimmedAtAUserMessage() {
        val history = (1..30).flatMap { i ->
            listOf(m(ChatRole.User, "u$i"), m(ChatRole.Assistant, "", calls = listOf(AiToolCall("c$i", "cari", "{}"))), m(ChatRole.Tool, "t$i", callId = "c$i"), m(ChatRole.Assistant, "a$i"))
        }
        val req = ChatHistory.toRequest(history)
        assertEquals(AiRole.User, req.first().role)
        assertEquals(true, req.size <= ChatHistory.MAX_MESSAGES)
        assertEquals("a30", req.last().content)
        val long = ChatHistory.toRequest(listOf(m(ChatRole.User, "x"), m(ChatRole.Assistant, "", calls = listOf(AiToolCall("k", "cari", "{}"))), m(ChatRole.Tool, "y".repeat(20_000), callId = "k")))
        assertEquals(true, long.last().content.length < 8_100 && long.last().content.endsWith("(dipotong)"))
    }
}
