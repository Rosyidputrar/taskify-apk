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
import kotlinx.coroutines.delay
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
    /** Batas token keluaran per jawaban (dari Pengaturan AI; bawaan 4096). */
    private val maxTokens: () -> Int = { 4096 },
) {
    private val jobs = ConcurrentHashMap<String, Job>()
    /** Anggaran putaran alat yang sudah dipakai giliran ini, dibawa lintas [decide]. */
    private val roundsUsed = ConcurrentHashMap<String, Int>()
    /** Keputusan yang diketuk saat satu keputusan lain masih diproses — dijalankan berurutan. */
    private val queuedDecisions = ConcurrentHashMap<String, ArrayDeque<Pair<String, Boolean>>>()
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
            loop(sessionId, freshTurn = true)
        }
    }

    /** Hentikan jawaban yang sedang berjalan; teks yang sudah diterima tetap disimpan. */
    fun stop(sessionId: String) { jobs[sessionId]?.cancel() }

    /** Ulangi jawaban terakhir (gagal / dihentikan / ingin versi lain) dari pesan user terakhir. */
    fun retry(sessionId: String) {
        if (isRunning(sessionId)) return
        launch(sessionId) {
            // Kartu konfirmasi yang masih terbuka ikut dibatalkan dulu (catatannya ikut
            // terhapus bersama jawaban lama di bawah — jawab ulang memang membuang giliran itu).
            cancelPending(sessionId, "Dibatalkan: pengguna meminta jawaban ulang sebelum menyetujui.")
            val history = repo.messages(sessionId)
            val lastUser = history.lastOrNull { it.role == ChatRole.User } ?: return@launch
            repo.deleteFrom(sessionId, lastUser.seq + 1)
            loop(sessionId, freshTurn = true)
        }
    }

    /** Ubah pesan user terakhir: jawabannya dibuang lalu diminta ulang. */
    fun editLast(sessionId: String, text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || isRunning(sessionId)) return
        launch(sessionId) {
            cancelPending(sessionId, "Dibatalkan: pengguna mengubah pesan sebelum menyetujui.")
            val lastUser = repo.messages(sessionId).lastOrNull { it.role == ChatRole.User } ?: return@launch
            repo.deleteFrom(sessionId, lastUser.seq)
            repo.addMessage(sessionId, ChatRole.User, clean)
            loop(sessionId, freshTurn = true)
        }
    }

    /**
     * Setujui / tolak alat tulis [callId]. Bila semua panggilan di jawaban itu sudah diputuskan, model dilanjutkan.
     * Ketukan saat satu keputusan masih diproses TIDAK hilang: diantrekan dan dijalankan
     * setelah pekerjaan itu selesai (lihat [launch]).
     */
    fun decide(sessionId: String, callId: String, approve: Boolean) {
        if (isRunning(sessionId)) {
            queuedDecisions.getOrPut(sessionId) { ArrayDeque() } += callId to approve
            return
        }
        launch(sessionId) {
            val history = repo.messages(sessionId)
            val owner = history.lastOrNull { it.role == ChatRole.Assistant && it.state == ChatState.Awaiting } ?: return@launch
            val calls = ChatHistory.decodeCalls(owner.toolCalls)
            val call = calls.firstOrNull { it.id == callId } ?: return@launch
            val answered = history.filter { it.role == ChatRole.Tool }.mapNotNull { it.toolCallId }.toSet()
            if (callId in answered) return@launch
            val allowed = repo.session(sessionId)?.dataAccess == true
            val result = when {
                !approve -> ToolResult("Pengguna menolak tindakan ini. Jangan dicoba lagi tanpa diminta.", "dibatalkan")
                !allowed -> NO_ACCESS
                else -> runTool(call)
            }
            repo.addMessage(sessionId, ChatRole.Tool, result.text, state = toolState(result, rejected = !approve), toolCallId = call.id, toolName = call.name)
            if (calls.all { it.id in answered || it.id == callId }) {
                repo.updateMessage(owner.id, state = ChatState.Done)
                loop(sessionId)
            }
        }
    }

    /** Keluar akun: hentikan semua jawaban. */
    fun cancelAll() {
        queuedDecisions.clear()
        roundsUsed.clear()
        jobs.values.forEach { it.cancel() }
    }

    // ------------------------------------------------------------------ inti

    private fun launch(sessionId: String, block: suspend () -> Unit) {
        _running.update { it + sessionId }
        val job = scope.launch {
            try {
                block()
            } finally {
                jobs.remove(sessionId)
                _running.update { it - sessionId }
                // Keputusan yang diantrekan saat pekerjaan ini berjalan, jalankan sekarang.
                queuedDecisions[sessionId]?.removeFirstOrNull()?.let { (callId, approve) -> decide(sessionId, callId, approve) }
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

    private suspend fun loop(sessionId: String, freshTurn: Boolean = false) {
        // Anggaran putaran dibawa lintas decide() dalam giliran yang sama: tanpa ini setiap
        // persetujuan memulai hitungan dari 1 dan model bisa berputar jauh di atas MAX_ROUNDS.
        // Giliran baru (pesan / jawab ulang / ubah) memulai anggaran baru.
        var round = if (freshTurn) 0.also { roundsUsed[sessionId] = 0 } else roundsUsed.getOrDefault(sessionId, 0)
        while (true) {
            val session = repo.session(sessionId) ?: return
            val answer = answerOnce(session) ?: return
            if (answer.calls.isEmpty()) return
            round++
            roundsUsed[sessionId] = round
            var waiting = false
            answer.calls.forEach { c ->
                val result = answer.early[c.id] ?: when {
                    toolbox.kind(c.name) == ToolKind.Write -> { waiting = true; null }
                    round > MAX_ROUNDS -> ToolResult("Batas pemanggilan alat untuk pesan ini tercapai. Jawab dengan data yang sudah ada.", "dilewati")
                    else -> runTool(c)
                }
                if (result != null) repo.addMessage(sessionId, ChatRole.Tool, result.text, state = toolState(result, rejected = false), toolCallId = c.id, toolName = c.name)
            }
            if (waiting) return // menunggu decide()
            if (round > MAX_ROUNDS + 1) return
        }
    }

    /** Panggilan alat dari satu jawaban + hasil yang sudah pasti tanpa dijalankan (ditolak / tidak valid). */
    private class Answer(val calls: List<AiToolCall>, val early: Map<String, ToolResult>)

    /**
     * Satu panggilan model. Mengembalikan panggilan alat (kosong = jawaban selesai), atau null bila
     * gagal / dihentikan (pesan asisten sudah diberi status yang sesuai).
     */
    private suspend fun answerOnce(session: ChatSession): Answer? {
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
            maxTokens = maxTokens(),
        )
        val text = StringBuilder()
        var calls: List<AiToolCall> = emptyList()
        var stopReason: String? = null
        var lastSave = clock()
        // Percobaan ulang terbatas untuk galat sementara (429/5xx/putus koneksi): hormati
        // Retry-After lalu backoff. Teks parsial TIDAK dibuang — percobaan berikutnya
        // melanjutkan dari teks yang sudah ada (lihat mergeAttemptText).
        var attempt = 0
        while (true) {
            attempt++
            val attemptText = StringBuilder()
            var attemptCalls: List<AiToolCall> = emptyList()
            var attemptStop: String? = null
            try {
                clients(provider, connection.baseUrl, connection.apiKey).stream(request).collect { e ->
                    when (e) {
                        is AiEvent.Text -> {
                            attemptText.append(e.delta)
                            val shown = mergedText(text, attemptText)
                            _live.update { it + (assistant.id to shown) }
                            if (clock() - lastSave > SAVE_EVERY_MS) {
                                lastSave = clock()
                                repo.updateMessage(assistant.id, content = shown)
                            }
                        }
                        is AiEvent.ToolCalls -> attemptCalls = e.calls
                        is AiEvent.Done -> attemptStop = e.stopReason
                    }
                }
            } catch (e: CancellationException) {
                mergeAttemptText(text, attemptText)
                withContext(NonCancellable) {
                    repo.updateMessage(assistant.id, content = text.toString(), state = ChatState.Stopped)
                    _live.update { it - assistant.id }
                }
                throw e
            } catch (e: AiException) {
                mergeAttemptText(text, attemptText)
                if (e.isRetryable && attempt < MAX_ATTEMPTS) {
                    try {
                        delay(e.retryDelayMs(attempt))
                    } catch (ce: CancellationException) {
                        // Berhenti saat jeda backoff: simpan sebagai Dihentikan seperti
                        // pembatalan di tengah streaming (bukan tertinggal "Streaming").
                        withContext(NonCancellable) {
                            repo.updateMessage(assistant.id, content = text.toString(), state = ChatState.Stopped)
                            _live.update { it - assistant.id }
                        }
                        throw ce
                    }
                    continue
                }
                repo.updateMessage(assistant.id, content = text.toString(), state = ChatState.Error, error = e.message)
                _live.update { it - assistant.id }
                return null
            } catch (e: Exception) {
                mergeAttemptText(text, attemptText)
                repo.updateMessage(assistant.id, content = text.toString(), state = ChatState.Error, error = "Galat tak terduga: ${e.message ?: e::class.simpleName}")
                _live.update { it - assistant.id }
                return null
            }
            mergeAttemptText(text, attemptText)
            calls = attemptCalls
            stopReason = attemptStop
            break
        }
        // Jawaban terpotong karena batas token: tandai supaya pengguna tahu model belum
        // selesai (bukan jawaban utuh), dan bisa minta lanjutan.
        if (stopReason == "length" || stopReason == "max_tokens") text.append(TRUNCATED_NOTE)
        // Akses data mati → tidak ada alat yang boleh jalan, walau model tetap memanggilnya
        // (mis. meniru riwayat saat akses masih hidup). Alat tulis diperiksa dulu sebelum dikonfirmasi.
        val early = mutableMapOf<String, ToolResult>()
        calls = calls.map { c ->
            when {
                !session.dataAccess -> c.also { early[it.id] = NO_ACCESS }
                toolbox.kind(c.name) != ToolKind.Write -> c
                else -> when (val p = prepare(c)) {
                    is Prepared.Ready -> c.copy(arguments = p.arguments)
                    is Prepared.Invalid -> c.also { early[it.id] = p.result }
                }
            }
        }
        val pending = calls.any { it.id !in early && toolbox.kind(it.name) == ToolKind.Write }
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
        return Answer(calls, early)
    }

    private suspend fun prepare(call: AiToolCall): Prepared = try {
        toolbox.prepare(call.name, call.arguments)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Prepared.Invalid(ToolResult("Argumen tidak valid: ${e.message ?: e::class.simpleName}", "tidak valid", failed = true))
    }

    private suspend fun runTool(call: AiToolCall): ToolResult = try {
        if (toolbox.kind(call.name) == null) ToolResult("Alat \"${call.name}\" tidak ada. Alat yang tersedia: ${toolbox.specs().joinToString { it.name }}.", "tidak dikenal", failed = true)
        else toolbox.run(call.name, call.arguments)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ToolResult("Alat gagal: ${e.message ?: e::class.simpleName}", "gagal", failed = true)
    }

    /**
     * Gabungkan teks percobaan ulang ke teks utama tanpa duplikasi: bila percobaan baru
     * mengulang awalan yang sama (regenerasi deterministik), hanya lanjutannya yang dipakai.
     * Bila keduanya menyimpang (model tak-deterministik), yang lebih panjang dipertahankan —
     * server chat completions tidak punya resume, jadi sambungan sempurna tidak bisa dijamin.
     */
    private fun mergedText(main: StringBuilder, extra: StringBuilder): String {
        val m = main.toString()
        val e = extra.toString()
        return when {
            e.isEmpty() -> m
            m.isEmpty() -> e
            e.startsWith(m) -> e
            m.startsWith(e) -> m
            else -> if (e.length > m.length) e else m
        }
    }

    private fun mergeAttemptText(main: StringBuilder, extra: StringBuilder) {
        val merged = mergedText(main, extra)
        if (merged != main.toString()) {
            main.clear()
            main.append(merged)
        }
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
        /** Percobaan kirim per giliran (1 awal + 2 ulang) untuk galat sementara. */
        const val MAX_ATTEMPTS = 3
        /** Catatan yang ditempel di akhir jawaban terpotong batas token (lihat E-S4). */
        const val TRUNCATED_NOTE = "\n\n— Jawaban terpotong karena batas token. Kirim \"lanjutkan\" untuk menyambung."
        private val NO_ACCESS = ToolResult("Akses data Taskify sedang mati untuk percakapan ini, jadi alat tidak dijalankan.", "akses data mati", failed = true)
        const val NO_DATA = "Akses data Taskify sedang MATI untuk percakapan ini: kamu tidak bisa membaca atau membuat tugas, catatan, jurnal, maupun transaksi. Bila diminta, sarankan pengguna menyalakan \"Akses data\" di atas chat."
    }
}

/** Ringkasan pesan untuk UI: riwayat + teks streaming + status alat. */
internal fun List<ChatMessage>.lastUserSeq() = lastOrNull { it.role == ChatRole.User }?.seq
