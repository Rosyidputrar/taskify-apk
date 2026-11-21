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
        // Galat TAK-dapat-diulang (404): tidak ada percobaan ulang otomatis — jalur
        // "Coba lagi" manual yang diuji di sini. (429/5xx diuji di tes percobaan ulang.)
        val client = ScriptedClient(
            { emit(AiEvent.Text("Sebagian")); throw AiException(AiErrorKind.NotFound, "Model tidak ditemukan (404).") },
            { emit(AiEvent.Text("Jawaban utuh.")); emit(AiEvent.Done("stop")) },
        )
        val e = engine(client)
        e.send("s1", "hai")
        advanceUntilIdle()
        assertEquals(ChatState.Error to "Sebagian", store.list.last().state to store.list.last().content)
        assertEquals("Model tidak ditemukan (404).", store.list.last().error)
        assertEquals(1, client.requests.size)
        e.retry("s1")
        advanceUntilIdle()
        assertEquals(listOf(ChatRole.User to ChatState.Done, ChatRole.Assistant to ChatState.Done), store.roles())
        assertEquals("Jawaban utuh.", store.list.last().content)
        // Jawaban gagal tidak ikut dikirim ulang.
        assertEquals(listOf(AiRole.User), client.requests[1].messages.map { it.role })
    }

    @Test fun rateLimitMidStreamRetriesAutomaticallyAndKeepsPartialText() = runTest {
        store.newSession()
        val client = ScriptedClient(
            { emit(AiEvent.Text("Halo ")); throw AiException(AiErrorKind.RateLimit, "Terlalu banyak permintaan (429).") },
            { emit(AiEvent.Text("Halo dunia.")); emit(AiEvent.Done("stop")) },
        )
        val e = engine(client)
        e.send("s1", "hai")
        advanceUntilIdle()
        assertFalse(e.isRunning("s1"))
        assertEquals(2, client.requests.size) // percobaan ulang otomatis, tanpa ketuk "Coba lagi"
        assertEquals(ChatState.Done to "Halo dunia.", store.list.last().state to store.list.last().content)
        assertTrue(!store.list.last().content.contains("Halo Halo")) // awalan tidak ganda
    }

    @Test fun nonRetryableErrorIsNotRetried() = runTest {
        store.newSession()
        val client = ScriptedClient(
            { throw AiException(AiErrorKind.Auth, "API key ditolak (401).") },
            { emit(AiEvent.Text("Seharusnya tidak sampai sini.")); emit(AiEvent.Done("stop")) },
        )
        val e = engine(client)
        e.send("s1", "hai")
        advanceUntilIdle()
        assertEquals(1, client.requests.size)
        assertEquals(ChatState.Error, store.list.last().state)
    }

    @Test fun truncatedAnswerIsMarkedWithNote() = runTest {
        store.newSession()
        val client = ScriptedClient(
            { emit(AiEvent.Text("Jawaban panjang")); emit(AiEvent.Done("length")) },
        )
        val e = engine(client)
        e.send("s1", "cerita panjang")
        advanceUntilIdle()
        val answer = store.list.last()
        assertEquals(ChatState.Done, answer.state)
        assertTrue(answer.content.endsWith(ChatEngine.TRUNCATED_NOTE))
        assertTrue(answer.content.startsWith("Jawaban panjang"))
        // Catatan hanya untuk pengguna: tidak muncul dua kali di layar, tidak dikirim ke model.
        val item = chatItems(store.list, emptyMap(), running = false, toolbox = tools).last() as ChatItem.Assistant
        assertEquals("Jawaban panjang", item.text)
        assertTrue(item.truncated)
        assertEquals("Jawaban panjang", ChatHistory.toRequest(store.list).last().content)
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
        // Hasil alat dibungkus pembatas data (E-M2) — isi asli tetap ada di dalamnya.
        assertTrue(second[2].content.startsWith(ChatHistory.TOOL_RESULT_HEADER))
        assertTrue(second[2].content.contains("hasil baca"))
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
        assertTrue(client.requests[1].messages.last().content.contains("Pengguna menolak"))
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

    @Test fun toolRoundsBudgetSurvivesApprovals() = runTest {
        store.newSession(dataAccess = true)
        // Dua giliran tulis (masing-masing butuh persetujuan) lalu 10 alat baca.
        // Tanpa anggaran lintas decide(), tiap persetujuan me-reset hitungan ke 1.
        val scripts = mutableListOf<suspend kotlinx.coroutines.flow.FlowCollector<AiEvent>.() -> Unit>()
        scripts += { emit(AiEvent.ToolCalls(listOf(AiToolCall("w1", "simpan", "{}")))); emit(AiEvent.Done("tool_calls")) }
        scripts += { emit(AiEvent.ToolCalls(listOf(AiToolCall("w2", "simpan", "{}")))); emit(AiEvent.Done("tool_calls")) }
        repeat(10) { i ->
            scripts += { emit(AiEvent.ToolCalls(listOf(AiToolCall("r$i", "baca", "{}")))); emit(AiEvent.Done("tool_calls")) }
        }
        val client = ScriptedClient(*scripts.toTypedArray())
        val e = engine(client)
        e.send("s1", "kerjakan banyak")
        advanceUntilIdle()
        assertEquals(ChatState.Awaiting, store.list.last().state)
        e.decide("s1", "w1", approve = true)
        advanceUntilIdle()
        assertEquals(ChatState.Awaiting, store.list.last().state)
        e.decide("s1", "w2", approve = true)
        advanceUntilIdle()
        assertFalse(e.isRunning("s1"))
        // Anggaran total = MAX_ROUNDS putaran: 2 tulis + 4 baca = 6 alat yang jalan.
        assertEquals(6, tools.ran.size)
        assertEquals(listOf("simpan", "simpan", "baca", "baca", "baca", "baca"), tools.ran.map { it.first })
        assertEquals(ChatEngine.MAX_ROUNDS + 2, client.requests.size)
    }

    @Test fun secondDecisionWhileFirstRunsIsQueuedNotLost() = runTest {
        store.newSession(dataAccess = true)
        val client = ScriptedClient(
            { emit(AiEvent.ToolCalls(listOf(AiToolCall("w1", "simpan", "{}"), AiToolCall("w2", "simpan", "{}")))); emit(AiEvent.Done("tool_calls")) },
            { emit(AiEvent.Text("Selesai.")); emit(AiEvent.Done("stop")) },
        )
        val e = engine(client)
        e.send("s1", "simpan dua")
        advanceUntilIdle()
        assertEquals(ChatState.Awaiting, store.list.last().state)
        // Keputusan pertama dibuat lambat (DB sibuk) — ketukan kedua datang saat masih berjalan.
        tools.runBlocker = kotlinx.coroutines.CompletableDeferred()
        e.decide("s1", "w1", approve = true)
        advanceUntilIdle()
        assertTrue(e.isRunning("s1"))
        assertTrue(tools.ran.isEmpty())
        e.decide("s1", "w2", approve = true) // dulu: hilang tanpa jejak
        advanceUntilIdle()
        assertTrue(tools.ran.isEmpty())
        tools.runBlocker!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(e.isRunning("s1"))
        assertEquals(listOf("simpan", "simpan"), tools.ran.map { it.first })
        assertEquals("Selesai.", store.list.last().content)
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

    @Test fun toolsNeverRunWhileDataAccessIsOff() = runTest {
        store.newSession(dataAccess = false)
        val client = ScriptedClient(
            // Model tetap memanggil alat walau tidak diberi (mis. meniru riwayat lama).
            { emit(AiEvent.ToolCalls(listOf(AiToolCall("c1", "baca", "{}"), AiToolCall("w1", "simpan", "{}")))); emit(AiEvent.Done("tool_calls")) },
            { emit(AiEvent.Text("Akses data mati.")); emit(AiEvent.Done("stop")) },
        )
        val e = engine(client)
        e.send("s1", "cek tugasku")
        advanceUntilIdle()
        assertTrue(tools.ran.isEmpty())
        // Tidak ada kartu konfirmasi: keduanya langsung dijawab "akses mati", lalu model lanjut.
        assertEquals(
            listOf(ChatRole.User to ChatState.Done, ChatRole.Assistant to ChatState.Done, ChatRole.Tool to ChatState.Error, ChatRole.Tool to ChatState.Error, ChatRole.Assistant to ChatState.Done),
            store.roles(),
        )
        assertTrue(store.list[2].content.contains("Akses data Taskify sedang mati"))
    }

    @Test fun approvingAfterDataAccessWasTurnedOffDoesNothing() = runTest {
        store.newSession(dataAccess = true)
        val client = ScriptedClient(
            { emit(AiEvent.ToolCalls(listOf(AiToolCall("w1", "simpan", "{}")))); emit(AiEvent.Done("tool_calls")) },
            { emit(AiEvent.Text("Oke.")); emit(AiEvent.Done("stop")) },
        )
        val e = engine(client)
        e.send("s1", "simpan")
        advanceUntilIdle()
        assertEquals(ChatState.Awaiting, store.list.last().state)
        store.sessions["s1"] = store.sessions["s1"]!!.copy(dataAccess = false)
        e.decide("s1", "w1", approve = true)
        advanceUntilIdle()
        assertTrue(tools.ran.isEmpty())
        assertEquals(ChatRole.Tool to ChatState.Error, store.roles()[2])
    }

    @Test fun invalidWriteIsAnsweredWithoutAskingTheUser() = runTest {
        store.newSession(dataAccess = true)
        tools.check = { Prepared.Invalid(ToolResult("Dompet \"BCA\" tidak ada.", "tidak valid", failed = true)) }
        val client = ScriptedClient(
            { emit(AiEvent.ToolCalls(listOf(AiToolCall("w1", "simpan", "{\"wallet\":\"BCA\"}")))); emit(AiEvent.Done("tool_calls")) },
            { emit(AiEvent.Text("Dompet BCA tidak ada, mau pakai yang mana?")); emit(AiEvent.Done("stop")) },
        )
        val e = engine(client)
        e.send("s1", "catat 20rb di BCA")
        advanceUntilIdle()
        assertTrue(tools.ran.isEmpty())
        assertEquals(
            listOf(ChatRole.User to ChatState.Done, ChatRole.Assistant to ChatState.Done, ChatRole.Tool to ChatState.Error, ChatRole.Assistant to ChatState.Done),
            store.roles(),
        )
        assertTrue(client.requests[1].messages.last().content.contains("Dompet \"BCA\" tidak ada."))
    }

    @Test fun preparedArgumentsAreWhatTheUserApprovesAndWhatRuns() = runTest {
        store.newSession(dataAccess = true)
        tools.check = { Prepared.Ready("{\"wallet_id\":\"w-1\",\"wallet\":\"Tunai\"}") }
        val client = ScriptedClient(
            { emit(AiEvent.ToolCalls(listOf(AiToolCall("w1", "simpan", "{\"wallet\":\"tunai\"}")))); emit(AiEvent.Done("tool_calls")) },
            { emit(AiEvent.Text("Tercatat.")); emit(AiEvent.Done("stop")) },
        )
        val e = engine(client)
        e.send("s1", "catat")
        advanceUntilIdle()
        val stored = ChatHistory.decodeCalls(store.list.last().toolCalls).single()
        assertEquals("{\"wallet_id\":\"w-1\",\"wallet\":\"Tunai\"}", stored.arguments)
        e.decide("s1", "w1", approve = true)
        advanceUntilIdle()
        assertEquals(listOf("simpan" to "{\"wallet_id\":\"w-1\",\"wallet\":\"Tunai\"}"), tools.ran)
    }

    @Test fun turningOffDataAccessMidStreamPreventsToolExecution() = runTest {
        store.newSession(dataAccess = true)
        val client = ScriptedClient(
            {
                emit(AiEvent.Text("Sebentar..."))
                // Pengguna mematikan sakelar akses data saat respons masih di-stream.
                store.sessions["s1"] = store.sessions["s1"]!!.copy(dataAccess = false)
                emit(AiEvent.ToolCalls(listOf(AiToolCall("c1", "baca", "{}"), AiToolCall("w1", "simpan", "{}"))))
                emit(AiEvent.Done("tool_calls"))
            },
            { emit(AiEvent.Text("Akses data telah dimatikan.")); emit(AiEvent.Done("stop")) },
        )
        val e = engine(client)
        e.send("s1", "cek dan simpan")
        advanceUntilIdle()
        assertTrue(tools.ran.isEmpty())
        assertEquals(
            listOf(ChatRole.User to ChatState.Done, ChatRole.Assistant to ChatState.Done, ChatRole.Tool to ChatState.Error, ChatRole.Tool to ChatState.Error, ChatRole.Assistant to ChatState.Done),
            store.roles(),
        )
        assertTrue(store.list[2].content.contains("Akses data Taskify sedang mati"))
    }
}
