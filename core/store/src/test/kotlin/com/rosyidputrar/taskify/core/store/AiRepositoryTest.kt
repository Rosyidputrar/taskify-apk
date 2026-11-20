package com.rosyidputrar.taskify.core.store

import com.powersync.PowerSyncDatabase
import com.powersync.inMemory
import com.rosyidputrar.taskify.core.store.ai.AiRepository
import com.rosyidputrar.taskify.core.store.ai.ChatRole
import com.rosyidputrar.taskify.core.store.ai.ChatState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiRepositoryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = PowerSyncDatabase.inMemory(TaskifySchema.schema, scope)
    private var now = 1_000L
    private val ai = AiRepository(db) { now }

    @After fun tearDown() = runBlocking {
        db.close()
        scope.cancel()
    }

    @Test fun sessionLifecycleOrderingAndPreview() = runBlocking {
        val a = ai.createSession("OpenAiCompatible", "gpt-4o", "Kamu asisten.", dataAccess = false)
        now = 2_000
        val b = ai.createSession("Anthropic", "claude-x", "sys", dataAccess = true)
        now = 3_000
        ai.addMessage(a, ChatRole.User, "  Tolong ringkas pengeluaran\nbulan ini  ")
        now = 4_000
        ai.addMessage(a, ChatRole.Assistant, "Total pengeluaran Rp 2.150.000.")
        ai.addMessage(a, ChatRole.Tool, "rahasia alat", toolCallId = "c1", toolName = "keuangan") // bukan pratinjau

        val sessions = ai.watchSessions().first()
        assertEquals(listOf(a, b), sessions.map { it.id }) // a baru saja dipakai
        val sa = sessions.first()
        assertEquals("Tolong ringkas pengeluaran", sa.title) // judul dari baris pertama pesan user
        assertEquals("Total pengeluaran Rp 2.150.000.", sa.preview)
        assertEquals(4_000, sa.updatedMs)
        assertTrue(sessions[1].dataAccess)
        assertNull(sessions[1].preview)

        // Judul tidak ditimpa pesan user berikutnya; bisa diganti manual.
        ai.addMessage(a, ChatRole.User, "lanjut")
        assertEquals("Tolong ringkas pengeluaran", ai.session(a)!!.title)
        ai.rename(a, "  Keuangan November  ")
        assertEquals("Keuangan November", ai.session(a)!!.title)

        ai.setModel(a, "Anthropic", "claude-y")
        ai.setDataAccess(a, true)
        ai.setSystemPrompt(a, "Singkat.")
        ai.session(a)!!.let { assertEquals(Triple("claude-y", true, "Singkat."), Triple(it.model, it.dataAccess, it.systemPrompt)) }

        ai.deleteSession(a)
        assertNull(ai.session(a))
        assertEquals(emptyList(), ai.messages(a))
        assertEquals(listOf(b), ai.watchSessions().first().map { it.id })
        ai.deleteAll()
        assertEquals(emptyList(), ai.watchSessions().first())
    }

    @Test fun messagesKeepSequenceUpdateAndTruncate() = runBlocking {
        val s = ai.createSession("p", "m", "sys", false)
        val u1 = ai.addMessage(s, ChatRole.User, "hai")
        val a1 = ai.addMessage(s, ChatRole.Assistant, "", state = ChatState.Streaming)
        ai.updateMessage(a1.id, content = "Halo", state = ChatState.Done)
        ai.addMessage(s, ChatRole.Assistant, "", state = ChatState.Awaiting, toolCalls = """[{"id":"c1"}]""")
        val list = ai.messages(s)
        assertEquals(listOf(1, 2, 3), list.map { it.seq })
        assertEquals("Halo", list[1].content)
        assertEquals(ChatState.Awaiting, list[2].state)
        assertEquals("""[{"id":"c1"}]""", list[2].toolCalls)

        ai.updateMessage(a1.id, state = ChatState.Error, error = "429")
        assertEquals("Halo" to "429", ai.message(a1.id)!!.let { it.content to it.error }) // isi tidak hilang
        ai.updateMessage(a1.id, state = ChatState.Done, clearError = true)
        assertNull(ai.message(a1.id)!!.error)

        // Ulangi jawaban: hapus mulai pesan asisten, pesan user tetap.
        ai.deleteFrom(s, a1.seq)
        assertEquals(listOf(u1.id), ai.messages(s).map { it.id })
        assertEquals(2, ai.addMessage(s, ChatRole.Assistant, "baru").seq)
    }

    @Test fun interruptedStreamsAreMarkedAfterRestart() = runBlocking {
        val s = ai.createSession("p", "m", "sys", false)
        val a = ai.addMessage(s, ChatRole.Assistant, "setengah ja", state = ChatState.Streaming)
        assertEquals(1, ai.recoverInterrupted())
        val m = ai.message(a.id)!!
        assertEquals(ChatState.Error, m.state)
        assertEquals("setengah ja", m.content)
        assertEquals(AiRepository.INTERRUPTED, m.error)
        assertEquals(0, ai.recoverInterrupted())
    }

    @Test fun titles() {
        assertEquals("Halo", AiRepository.titleOf("\n\n  Halo  \nbaris dua"))
        val long = "Tolong bantu saya menyusun rencana anggaran bulanan untuk keluarga kecil di Jakarta"
        val t = AiRepository.titleOf(long)
        assertTrue(t.endsWith("…") && t.length <= AiRepository.TITLE_MAX + 1 && !t.contains("  "))
        assertEquals("Tolong bantu saya menyusun rencana anggaran bulanan untuk…", t)
    }
}
