package com.rosyidputrar.taskify.feature.ai

import com.rosyidputrar.taskify.core.ai.AiClient
import com.rosyidputrar.taskify.core.ai.AiConnector
import com.rosyidputrar.taskify.core.ai.AiEvent
import com.rosyidputrar.taskify.core.ai.AiException
import com.rosyidputrar.taskify.core.ai.AiPrompt
import com.rosyidputrar.taskify.core.ai.AiProvider
import com.rosyidputrar.taskify.core.ai.AiRequest
import com.rosyidputrar.taskify.core.ai.AiToolCall
import com.rosyidputrar.taskify.core.store.ai.ChatStore
import com.rosyidputrar.taskify.core.store.ai.ChatMessage
import com.rosyidputrar.taskify.core.store.ai.ChatRole
import com.rosyidputrar.taskify.core.store.ai.ChatSession
import com.rosyidputrar.taskify.core.store.ai.ChatState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * Mesin percakapan AI. Hidup di cakupan aplikasi: jawaban tetap berjalan & tersimpan walau layar
 * chat ditutup. Satu jawaban aktif per sesi.
 *
 * Alur satu giliran: kirim riwayat → streaming jawaban (teks di memori [live], disimpan berkala) →
 * bila model meminta alat: alat BACA langsung dijalankan lalu model dipanggil lagi; alat TULIS
 * menunggu [decide] pengguna. Maks. [MAX_ROUNDS] putaran alat per pesan supaya tidak berputar terus.
 */
class ChatEngine(
    private val repo: ChatStore,
    private val connector: AiConnector,
    private val clients: (AiProvider, String, String) -> AiClient,
    private val toolbox: AiToolbox,
    private val scope: CoroutineScope,
    private val now: () -> ZonedDateTime = { ZonedDateTime.now() },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val jobs = ConcurrentHashMap<String, Job>()
    private val _running = MutableStateFlow<Set<String>>(emptySet())
    /** Sesi yang sedang menunggu jawaban. */
    val running: StateFlow<Set<String>> = _running.asStateFlow()

    private val _live = MutableStateFlow<Map<String, String>>(emptyMap())
    /** Teks streaming per id pesan (lebih cepat dari database). */
    val live: StateFlow<Map<String, String>> = _live.asStateFlow()

    init {
        // Aplikasi sempat mati di tengah jawaban → pesan "streaming" yang tertinggal ditandai terputus.
        scope.launch { runCatching { repo.recoverInterrupted() } }
    }

    fun isRunning(sessionId: String) = sessionId in _running.value

    /** Kirim pesan user lalu minta jawaban. Diabaikan bila sesi masih menjawab atau teks kosong. */
    fun send(sessionId: String, text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || isRunning(sessionId)) return
        launch(sessionId) {
            cancelPending(sessionId, "Dibatalkan: pengguna mengirim pesan baru sebelum menyetujui.")
            repo.addMessage(sessionId, ChatRole.User, clean)
            loop(sessionId)
        }
    }

    /** Hentikan jawaban yang sedang berjalan; teks yang sudah diterima tetap disimpan. */
    fun stop(sessionId: String) { jobs[sessionId]?.cancel() }

    /** Ulangi jawaban terakhir (gagal / dihentikan / ingin versi lain) dari pesan user terakhir. */
    fun retry(sessionId: String) {
        if (isRunning(sessionId)) return
        launch(sessionId) {
            val history = repo.messages(sessionId)
            val lastUser = history.lastOrNull { it.role == ChatRole.User } ?: return@launch
            repo.deleteFrom(sessionId, lastUser.seq + 1)
            loop(sessionId)
        }
    }

    /** Ubah pesan user terakhir: jawabannya dibuang lalu diminta ulang. */
    fun editLast(sessionId: String, text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || isRunning(sessionId)) return
        launch(sessionId) {
            val lastUser = repo.messages(sessionId).lastOrNull { it.role == ChatRole.User } ?: return@launch
            repo.deleteFrom(sessionId, lastUser.seq)
            repo.addMessage(sessionId, ChatRole.User, clean)
            loop(sessionId)
        }
    }

    /** Setujui / tolak alat tulis [callId]. Bila semua panggilan di jawaban itu sudah diputuskan, model dilanjutkan. */
    fun decide(sessionId: String, callId: String, approve: Boolean) {
        if (isRunning(sessionId)) return
        launch(sessionId) {
            val history = repo.messages(sessionId)
            val owner = history.lastOrNull { it.role == ChatRole.Assistant && it.state == ChatState.Awaiting } ?: return@launch
            val calls = ChatHistory.decodeCalls(owner.toolCalls)
            val call = calls.firstOrNull { it.id == callId } ?: return@launch
            val answered = history.filter { it.role == ChatRole.Tool }.mapNotNull { it.toolCallId }.toSet()
            if (callId in answered) return@launch
            val result = if (approve) runTool(call) else ToolResult("Pengguna menolak tindakan ini. Jangan dicoba lagi tanpa diminta.", "dibatalkan")
            repo.addMessage(sessionId, ChatRole.Tool, result.text, state = toolState(result, rejected = !approve), toolCallId = call.id, toolName = call.name)
            if (calls.all { it.id in answered || it.id == callId }) {
                repo.updateMessage(owner.id, state = ChatState.Done)
                loop(sessionId, rounds = 1)
            }
        }
    }

    /** Keluar akun: hentikan semua jawaban. */
    fun cancelAll() { jobs.values.forEach { it.cancel() } }

    // ------------------------------------------------------------------ inti

    private fun launch(sessionId: String, block: suspend () -> Unit) {
        _running.update { it + sessionId }
        val job = scope.launch {
            try {
                block()
            } finally {
                jobs.remove(sessionId)
                _running.update { it - sessionId }
            }
        }
        jobs[sessionId] = job
    }

    /** Tool yang masih menunggu persetujuan dianggap ditolak (mis. pengguna sudah mengirim pesan lain). */
    private suspend fun cancelPending(sessionId: String, reason: String) {
        val history = repo.messages(sessionId)
        val owner = history.lastOrNull { it.role == ChatRole.Assistant && it.state == ChatState.Awaiting } ?: return
        val answered = history.filter { it.role == ChatRole.Tool }.mapNotNull { it.toolCallId }.toSet()
        ChatHistory.decodeCalls(owner.toolCalls).filter { it.id !in answered }.forEach { c ->
            repo.addMessage(sessionId, ChatRole.Tool, reason, state = ChatState.Stopped, toolCallId = c.id, toolName = c.name)
        }
        repo.updateMessage(owner.id, state = ChatState.Done)
    }

    private suspend fun loop(sessionId: String, rounds: Int = 0) {
        var round = rounds
        while (true) {
            val session = repo.session(sessionId) ?: return
            val calls = answerOnce(session) ?: return
            if (calls.isEmpty()) return
            round++
            val writes = calls.filter { toolbox.kind(it.name) == ToolKind.Write }
            calls.filter { toolbox.kind(it.name) != ToolKind.Write }.forEach { c ->
                val result = if (round > MAX_ROUNDS) ToolResult("Batas pemanggilan alat untuk pesan ini tercapai. Jawab dengan data yang sudah ada.", "dilewati") else runTool(c)
                repo.addMessage(sessionId, ChatRole.Tool, result.text, state = toolState(result, rejected = false), toolCallId = c.id, toolName = c.name)
            }
            if (writes.isNotEmpty()) return // menunggu decide()
            if (round > MAX_ROUNDS + 1) return
        }
    }

    /**
     * Satu panggilan model. Mengembalikan panggilan alat (kosong = jawaban selesai), atau null bila
     * gagal / dihentikan (pesan asisten sudah diberi status yang sesuai).
     */
    private suspend fun answerOnce(session: ChatSession): List<AiToolCall>? {
        val provider = AiProvider.entries.firstOrNull { it.name == session.provider }
        val connection = provider?.let(connector::connection)
        val history = repo.messages(session.id)
        val assistant = repo.addMessage(session.id, ChatRole.Assistant, "", state = ChatState.Streaming)
        if (provider == null || connection == null || session.model.isBlank()) {
            val why = when {
                provider == null -> "Penyedia AI sesi ini tidak dikenal."
                connection == null -> "Kunci API untuk ${provider.label} belum diatur. Buka Pengaturan AI."
                else -> "Belum ada model. Pilih model di Pengaturan AI."
            }
            repo.updateMessage(assistant.id, state = ChatState.Error, error = why)
            return null
        }
        val tools = if (session.dataAccess) toolbox.specs() else emptyList()
        val request = AiRequest(
            model = session.model,
            system = AiPrompt.render(session.systemPrompt.ifBlank { AiPrompt.DEFAULT }, now()) + if (tools.isEmpty()) "\n" + NO_DATA else "",
            messages = ChatHistory.toRequest(history),
            tools = tools,
        )
        val text = StringBuilder()
        var calls: List<AiToolCall> = emptyList()
        var lastSave = clock()
        try {
            clients(provider, connection.baseUrl, connection.apiKey).stream(request).collect { e ->
                when (e) {
                    is AiEvent.Text -> {
                        text.append(e.delta)
                        _live.update { it + (assistant.id to text.toString()) }
                        if (clock() - lastSave > SAVE_EVERY_MS) {
                            lastSave = clock()
                            repo.updateMessage(assistant.id, content = text.toString())
                        }
                    }
                    is AiEvent.ToolCalls -> calls = e.calls
                    is AiEvent.Done -> Unit
                }
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                repo.updateMessage(assistant.id, content = text.toString(), state = ChatState.Stopped)
                _live.update { it - assistant.id }
            }
            throw e
        } catch (e: AiException) {
            repo.updateMessage(assistant.id, content = text.toString(), state = ChatState.Error, error = e.message)
            _live.update { it - assistant.id }
            return null
        } catch (e: Exception) {
            repo.updateMessage(assistant.id, content = text.toString(), state = ChatState.Error, error = "Galat tak terduga: ${e.message ?: e::class.simpleName}")
            _live.update { it - assistant.id }
            return null
        }
        val pending = calls.any { toolbox.kind(it.name) == ToolKind.Write }
        repo.updateMessage(
            assistant.id, content = text.toString(),
            state = if (pending) ChatState.Awaiting else ChatState.Done,
            toolCalls = ChatHistory.encodeCalls(calls),
        )
        _live.update { it - assistant.id }
        if (text.isBlank() && calls.isEmpty()) {
            repo.updateMessage(assistant.id, state = ChatState.Error, error = "Model tidak mengirim jawaban apa pun.")
            return null
        }
        return calls
    }

    private suspend fun runTool(call: AiToolCall): ToolResult = try {
        if (toolbox.kind(call.name) == null) ToolResult("Alat \"${call.name}\" tidak ada. Alat yang tersedia: ${toolbox.specs().joinToString { it.name }}.", "tidak dikenal", failed = true)
        else toolbox.run(call.name, call.arguments)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ToolResult("Alat gagal: ${e.message ?: e::class.simpleName}", "gagal", failed = true)
    }

    /** Status pesan alat: Done = berhasil, Error = gagal, Stopped = ditolak / dibatalkan. */
    private fun toolState(r: ToolResult, rejected: Boolean) = when {
        rejected -> ChatState.Stopped
        r.failed -> ChatState.Error
        else -> ChatState.Done
    }

    /** Pesan galat untuk riwayat yang tertinggal saat aplikasi mati di tengah jawaban. */
    suspend fun recover() = repo.recoverInterrupted()

    companion object {
        const val MAX_ROUNDS = 6
        const val SAVE_EVERY_MS = 1_200L
        const val NO_DATA = "Akses data Taskify sedang MATI untuk percakapan ini: kamu tidak bisa membaca atau membuat tugas, catatan, jurnal, maupun transaksi. Bila diminta, sarankan pengguna menyalakan \"Akses data\" di atas chat."
    }
}

/** Ringkasan pesan untuk UI: riwayat + teks streaming + status alat. */
internal fun List<ChatMessage>.lastUserSeq() = lastOrNull { it.role == ChatRole.User }?.seq
