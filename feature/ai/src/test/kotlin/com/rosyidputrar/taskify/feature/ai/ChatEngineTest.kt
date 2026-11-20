package com.rosyidputrar.taskify.feature.ai

import com.rosyidputrar.taskify.core.ai.AiConnection
import com.rosyidputrar.taskify.core.ai.AiConnector
import com.rosyidputrar.taskify.core.ai.AiErrorKind
import com.rosyidputrar.taskify.core.ai.AiEvent
import com.rosyidputrar.taskify.core.ai.AiException
import com.rosyidputrar.taskify.core.ai.AiRole
import com.rosyidputrar.taskify.core.ai.AiToolCall
import com.rosyidputrar.taskify.core.store.ai.ChatRole
import com.rosyidputrar.taskify.core.store.ai.ChatState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChatEngineTest {
    private val store = FakeChatStore()
    private val tools = FakeToolbox()
    private val friday = ZonedDateTime.of(2026, 11, 20, 9, 0, 0, 0, ZoneId.of("Asia/Jakarta"))

    private fun TestScope.engine(client: ScriptedClient, connected: Boolean = true) = ChatEngine(
        repo = store,
        connector = AiConnector { if (connected) AiConnection("https://api.example.com/v1", "sk") else null },
        clients = { _, _, _ -> client },
        toolbox = tools,
        scope = this,
        now = { friday },
        clock = { 0L },
    )

    @Test fun plainAnswerIsStreamedAndSaved() = runTest {
        store.newSession()
        val client = ScriptedClient({ emit(AiEvent.Text("Halo ")); emit(AiEvent.Text("Rosyid.")); emit(AiEvent.Done("stop")) })
        val e = engine(client)
        e.send("s1", "  hai  ")
        assertTrue(e.isRunning("s1"))
        advanceUntilIdle()
        assertFalse(e.isRunning("s1"))
        assertEquals(listOf(ChatRole.User to ChatState.Done, ChatRole.Assistant to ChatState.Done), store.roles())
        assertEquals("hai", store.list[0].content)
        assertEquals("Halo Rosyid.", store.list[1].content)
        assertEquals(emptyMap(), e.live.value)
        // Akses data mati: tanpa alat, dan model diberi tahu.
        val req = client.requests.single()
        assertEquals(emptyList(), req.tools)
        assertTrue(req.system!!.startsWith("Kamu asisten. Jumat"))
        assertTrue(req.system!!.contains("Akses data Taskify sedang MATI"))
        assertEquals(listOf(AiRole.User), req.messages.map { it.role })
    }

    @Test fun missingKeyBecomesAnInlineError() = runTest {
        store.newSession()
        val e = engine(ScriptedClient(), connected = false)
        e.send("s1", "hai")
        advanceUntilIdle()
        val answer = store.list.last()
        assertEquals(ChatState.Error, answer.state)
        assertTrue(answer.error!!.startsWith("Kunci API untuk OpenAI-compatible belum diatur"))
    }

    @Test fun serverErrorKeepsPartialTextAndRetryReplacesIt() = runTest {
        store.newSession()
        val client = ScriptedClient(
            { emit(AiEvent.Text("Sebagian")); throw AiException(AiErrorKind.RateLimit, "Terlalu banyak permintaan (429).") },
            { emit(AiEvent.Text("Jawaban utuh.")); emit(AiEvent.Done("stop")) },
        )
        val e = engine(client)
        e.send("s1", "hai")
        advanceUntilIdle()
        assertEquals(ChatState.Error to "Sebagian", store.list.last().state to store.list.last().content)
        assertEquals("Terlalu banyak permintaan (429).", store.list.last().error)
        e.retry("s1")
        advanceUntilIdle()
        assertEquals(listOf(ChatRole.User to ChatState.Done, ChatRole.Assistant to ChatState.Done), store.roles())
        assertEquals("Jawaban utuh.", store.list.last().content)
        // Jawaban gagal tidak ikut dikirim ulang.
        assertEquals(listOf(AiRole.User), client.requests[1].messages.map { it.role })
    }

    @Test fun stopKeepsWhatWasReceived() = runTest {
        store.newSession()
        val e = engine(ScriptedClient({ emit(AiEvent.Text("Setengah jawaban")); hang() }))
        e.send("s1", "cerita panjang")
        advanceUntilIdle()
        assertTrue(e.isRunning("s1"))
        assertEquals("Setengah jawaban", e.live.value.values.single())
        e.stop("s1")
        advanceUntilIdle()
        assertFalse(e.isRunning("s1"))
        assertEquals(ChatState.Stopped to "Setengah jawaban", store.list.last().state to store.list.last().content)
        assertEquals(emptyMap(), e.live.value)
    }

    @Test fun readToolsRunAutomaticallyAndModelContinues() = runTest {
        store.newSession(dataAccess = true)
        val client = ScriptedClient(
            { emit(AiEvent.Text("Saya cek dulu.")); emit(AiEvent.ToolCalls(listOf(AiToolCall("c1", "baca", "{\"q\":\"x\"}")))); emit(AiEvent.Done("tool_calls")) },
            { emit(AiEvent.Text("Ada 1 hasil.")); emit(AiEvent.Done("stop")) },
        )
        val e = engine(client)
        e.send("s1", "cari x")
        advanceUntilIdle()
        assertEquals(listOf("baca" to "{\"q\":\"x\"}"), tools.ran)
        assertEquals(
            listOf(ChatRole.User to ChatState.Done, ChatRole.Assistant to ChatState.Done, ChatRole.Tool to ChatState.Done, ChatRole.Assistant to ChatState.Done),
            store.roles(),
        )
        assertEquals(listOf("baca", "simpan"), client.requests[0].tools.map { it.name })
        assertFalse(client.requests[0].system!!.contains("MATI"))
        val second = client.requests[1].messages
        assertEquals(listOf(AiRole.User, AiRole.Assistant, AiRole.Tool), second.map { it.role })
        assertEquals("c1", second[2].toolCallId)
        assertEquals("hasil baca", second[2].content)
    }

    @Test fun writeToolWaitsForApproval() = runTest {
        store.newSession(dataAccess = true)
        val client = ScriptedClient(
            { emit(AiEvent.ToolCalls(listOf(AiToolCall("w1", "simpan", "{\"judul\":\"Bayar listrik\"}")))); emit(AiEvent.Done("tool_calls")) },
            { emit(AiEvent.Text("Sudah disimpan.")); emit(AiEvent.Done("stop")) },
        )
        val e = engine(client)
        e.send("s1", "buat tugas bayar listrik")
        advanceUntilIdle()
        assertFalse(e.isRunning("s1"))
        assertEquals(ChatState.Awaiting, store.list.last().state)
        assertTrue(tools.ran.isEmpty()) // belum disetujui = belum dijalankan
        assertEquals(1, client.requests.size)

        e.decide("s1", "w1", approve = true)
        advanceUntilIdle()
        assertEquals(listOf("simpan" to "{\"judul\":\"Bayar listrik\"}"), tools.ran)
        assertEquals(
            listOf(ChatRole.User to ChatState.Done, ChatRole.Assistant to ChatState.Done, ChatRole.Tool to ChatState.Done, ChatRole.Assistant to ChatState.Done),
            store.roles(),
        )
        assertEquals("Sudah disimpan.", store.list.last().content)
        // Keputusan ganda diabaikan.
        e.decide("s1", "w1", approve = true)
        advanceUntilIdle()
        assertEquals(1, tools.ran.size)
    }

    @Test fun rejectedWriteIsReportedToModel() = runTest {
        store.newSession(dataAccess = true)
        val client = ScriptedClient(
            { emit(AiEvent.ToolCalls(listOf(AiToolCall("w1", "simpan", "{}")))); emit(AiEvent.Done("tool_calls")) },
            { emit(AiEvent.Text("Baik, tidak jadi.")); emit(AiEvent.Done("stop")) },
        )
        val e = engine(client)
        e.send("s1", "simpan")
        advanceUntilIdle()
        e.decide("s1", "w1", approve = false)
        advanceUntilIdle()
        assertTrue(tools.ran.isEmpty())
        val tool = store.list.single { it.role == ChatRole.Tool }
        assertEquals(ChatState.Stopped, tool.state)
        assertTrue(client.requests[1].messages.last().content.startsWith("Pengguna menolak"))
    }

    @Test fun newMessageWhilePendingCancelsThePendingWrite() = runTest {
        store.newSession(dataAccess = true)
        val client = ScriptedClient(
            { emit(AiEvent.ToolCalls(listOf(AiToolCall("w1", "simpan", "{}")))); emit(AiEvent.Done("tool_calls")) },
            { emit(AiEvent.Text("Oke, topik lain.")); emit(AiEvent.Done("stop")) },
        )
        val e = engine(client)
        e.send("s1", "simpan")
        advanceUntilIdle()
        e.send("s1", "eh, lupakan. Apa kabar?")
        advanceUntilIdle()
        assertEquals(
            listOf(ChatRole.User, ChatRole.Assistant, ChatRole.Tool, ChatRole.User, ChatRole.Assistant),
            store.roles().map { it.first },
        )
        assertEquals(ChatState.Stopped, store.list.single { it.role == ChatRole.Tool }.state)
        assertTrue(tools.ran.isEmpty())
        // Pasangan alat tetap lengkap di permintaan berikutnya.
        assertEquals(listOf(AiRole.User, AiRole.Assistant, AiRole.Tool, AiRole.User), client.requests[1].messages.map { it.role })
    }

    @Test fun failingToolIsReportedNotCrashing() = runTest {
        store.newSession(dataAccess = true)
        tools.fail = true
        val e = engine(ScriptedClient(
            { emit(AiEvent.ToolCalls(listOf(AiToolCall("c1", "baca", "{}"), AiToolCall("c2", "tidak_ada", "{}")))); emit(AiEvent.Done("tool_calls")) },
            { emit(AiEvent.Text("Maaf, gagal.")); emit(AiEvent.Done("stop")) },
        ))
        e.send("s1", "x")
        advanceUntilIdle()
        val results = store.list.filter { it.role == ChatRole.Tool }
        assertEquals(listOf(ChatState.Error, ChatState.Error), results.map { it.state })
        assertTrue(results[0].content.contains("database terkunci"))
        assertTrue(results[1].content.contains("tidak ada"))
    }

    @Test fun toolRoundsAreBounded() = runTest {
        store.newSession(dataAccess = true)
        val loops = Array<suspend kotlinx.coroutines.flow.FlowCollector<AiEvent>.() -> Unit>(20) { i ->
            { emit(AiEvent.ToolCalls(listOf(AiToolCall("c$i", "baca", "{}")))); emit(AiEvent.Done("tool_calls")) }
        }
        val client = ScriptedClient(*loops)
        val e = engine(client)
        e.send("s1", "ulang terus")
        advanceUntilIdle()
        assertFalse(e.isRunning("s1"))
        assertEquals(ChatEngine.MAX_ROUNDS + 2, client.requests.size)
        assertEquals(ChatEngine.MAX_ROUNDS, tools.ran.size) // setelah batas, alat tidak dijalankan lagi
    }

    @Test fun editLastReplacesQuestionAndAnswer() = runTest {
        store.newSession()
        val client = ScriptedClient(
            { emit(AiEvent.Text("Jawaban A.")); emit(AiEvent.Done("stop")) },
            { emit(AiEvent.Text("Jawaban B.")); emit(AiEvent.Done("stop")) },
        )
        val e = engine(client)
        e.send("s1", "pertanyaan A")
        advanceUntilIdle()
        e.editLast("s1", "pertanyaan B")
        advanceUntilIdle()
        assertEquals(listOf("pertanyaan B", "Jawaban B."), store.list.sortedBy { it.seq }.map { it.content })
        assertEquals("pertanyaan B", client.requests[1].messages.single().content)
    }

    @Test fun emptyAnswerIsAnError() = runTest {
        store.newSession()
        val e = engine(ScriptedClient({ emit(AiEvent.Done("stop")) }))
        e.send("s1", "x")
        advanceUntilIdle()
        assertEquals(ChatState.Error, store.list.last().state)
        assertNull(e.live.value[store.list.last().id])
    }
}
