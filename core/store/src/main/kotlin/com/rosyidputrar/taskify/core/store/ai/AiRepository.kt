package com.rosyidputrar.taskify.core.store.ai

import com.powersync.PowerSyncDatabase
import com.powersync.db.SqlCursor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

enum class ChatRole(val db: String) {
    User("user"), Assistant("assistant"), Tool("tool");

    companion object {
        fun of(v: String?) = entries.firstOrNull { it.db == v } ?: User
    }
}

enum class ChatState(val db: String) {
    /** Selesai normal. */
    Done("done"),
    /** Sedang diterima dari server. */
    Streaming("streaming"),
    /** Gagal (lihat [ChatMessage.error]); bisa diulang. */
    Error("error"),
    /** Dihentikan pengguna — teks sebagian tetap disimpan. */
    Stopped("stopped"),
    /** Alat tulis menunggu persetujuan pengguna. */
    Awaiting("awaiting");

    companion object {
        fun of(v: String?) = entries.firstOrNull { it.db == v } ?: Done
    }
}

data class ChatSession(
    val id: String,
    val title: String,
    val provider: String,
    val model: String,
    val systemPrompt: String,
    val dataAccess: Boolean,
    val createdMs: Long,
    val updatedMs: Long,
    /** Potongan pesan user/asisten terakhir (untuk daftar sesi). */
    val preview: String? = null,
)

data class ChatMessage(
    val id: String,
    val sessionId: String,
    val seq: Int,
    val role: ChatRole,
    val content: String,
    /** JSON panggilan alat dari asisten (format netral), null bila tidak ada. */
    val toolCalls: String? = null,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val state: ChatState = ChatState.Done,
    val error: String? = null,
    val createdMs: Long = 0,
) {
    override fun toString() = "ChatMessage(id=$id, seq=$seq, role=$role, state=$state, len=${content.length})"
}

/** Bagian penyimpanan yang dipakai mesin chat (dipisah supaya mesin bisa diuji tanpa database). */
interface ChatStore {
    suspend fun session(id: String): ChatSession?
    suspend fun messages(sessionId: String): List<ChatMessage>
    suspend fun addMessage(
        sessionId: String,
        role: ChatRole,
        content: String,
        state: ChatState = ChatState.Done,
        toolCalls: String? = null,
        toolCallId: String? = null,
        toolName: String? = null,
    ): ChatMessage
    suspend fun updateMessage(id: String, content: String? = null, state: ChatState? = null, error: String? = null, toolCalls: String? = null, clearError: Boolean = false)
    suspend fun deleteFrom(sessionId: String, fromSeq: Int)
    suspend fun recoverInterrupted(): Int
}

/**
 * Sesi & pesan asisten AI — tabel LOKAL (tidak disinkronkan; ikut terhapus saat keluar akun).
 * Urutan pesan memakai [ChatMessage.seq] (bukan waktu), jadi tetap benar walau jam HP berubah.
 */
class AiRepository(private val db: PowerSyncDatabase, private val clock: () -> Long = System::currentTimeMillis) : ChatStore {

    private val sessionCols = """s.id, s.title, s.provider, s.model, s.system_prompt, s.data_access, s.created_ms, s.updated_ms,
        (SELECT m.content FROM ai_messages m WHERE m.session_id = s.id AND m.role IN ('user','assistant') AND m.content <> ''
          ORDER BY m.seq DESC LIMIT 1)"""

    private fun SqlCursor.toSession() = ChatSession(
        id = getString(0)!!, title = getString(1).orEmpty(), provider = getString(2).orEmpty(), model = getString(3).orEmpty(),
        systemPrompt = getString(4).orEmpty(), dataAccess = (getLong(5) ?: 0L) == 1L, createdMs = getLong(6) ?: 0, updatedMs = getLong(7) ?: 0,
        preview = getString(8)?.let(::previewOf),
    )

    private val messageCols = "id, session_id, seq, role, content, tool_calls, tool_call_id, tool_name, state, error, created_ms"

    private fun SqlCursor.toMessage() = ChatMessage(
        id = getString(0)!!, sessionId = getString(1).orEmpty(), seq = (getLong(2) ?: 0L).toInt(), role = ChatRole.of(getString(3)),
        content = getString(4).orEmpty(), toolCalls = getString(5), toolCallId = getString(6), toolName = getString(7),
        state = ChatState.of(getString(8)), error = getString(9), createdMs = getLong(10) ?: 0,
    )

    // ------------------------------------------------------------------ sesi

    fun watchSessions(): Flow<List<ChatSession>> =
        db.watch("SELECT $sessionCols FROM ai_sessions s ORDER BY s.updated_ms DESC") { it.toSession() }

    fun watchSession(id: String): Flow<ChatSession?> =
        db.watch("SELECT $sessionCols FROM ai_sessions s WHERE s.id = ?", listOf(id)) { it.toSession() }.map { it.firstOrNull() }

    override suspend fun session(id: String): ChatSession? = db.getOptional("SELECT $sessionCols FROM ai_sessions s WHERE s.id = ?", listOf(id)) { it.toSession() }

    suspend fun createSession(provider: String, model: String, systemPrompt: String, dataAccess: Boolean, title: String = ""): String {
        val id = UUID.randomUUID().toString()
        val now = clock()
        db.execute(
            "INSERT INTO ai_sessions (id, title, provider, model, system_prompt, data_access, created_ms, updated_ms) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            listOf(id, title, provider, model, systemPrompt, if (dataAccess) 1L else 0L, now, now),
        )
        return id
    }

    suspend fun rename(id: String, title: String) = db.execute("UPDATE ai_sessions SET title = ? WHERE id = ?", listOf(title.trim().take(TITLE_MAX), id))

    suspend fun setModel(id: String, provider: String, model: String) =
        db.execute("UPDATE ai_sessions SET provider = ?, model = ? WHERE id = ?", listOf(provider, model, id))

    suspend fun setSystemPrompt(id: String, prompt: String) = db.execute("UPDATE ai_sessions SET system_prompt = ? WHERE id = ?", listOf(prompt, id))

    suspend fun setDataAccess(id: String, on: Boolean) = db.execute("UPDATE ai_sessions SET data_access = ? WHERE id = ?", listOf(if (on) 1L else 0L, id))

    suspend fun deleteSession(id: String) {
        db.writeTransaction { tx ->
            tx.execute("DELETE FROM ai_messages WHERE session_id = ?", listOf(id))
            tx.execute("DELETE FROM ai_sessions WHERE id = ?", listOf(id))
        }
    }

    suspend fun deleteAll() {
        db.writeTransaction { tx ->
            tx.execute("DELETE FROM ai_messages")
            tx.execute("DELETE FROM ai_sessions")
        }
    }

    // ------------------------------------------------------------------ pesan

    fun watchMessages(sessionId: String): Flow<List<ChatMessage>> =
        db.watch("SELECT $messageCols FROM ai_messages WHERE session_id = ? ORDER BY seq", listOf(sessionId)) { it.toMessage() }

    override suspend fun messages(sessionId: String): List<ChatMessage> =
        db.getAll("SELECT $messageCols FROM ai_messages WHERE session_id = ? ORDER BY seq", listOf(sessionId)) { it.toMessage() }

    suspend fun message(id: String): ChatMessage? = db.getOptional("SELECT $messageCols FROM ai_messages WHERE id = ?", listOf(id)) { it.toMessage() }

    /**
     * Tambah pesan di akhir sesi (seq = terakhir + 1, dalam satu transaksi) dan perbarui waktu sesi.
     * Pesan user pertama juga menjadi judul sesi bila judul masih kosong.
     */
    override suspend fun addMessage(
        sessionId: String,
        role: ChatRole,
        content: String,
        state: ChatState,
        toolCalls: String?,
        toolCallId: String?,
        toolName: String?,
    ): ChatMessage {
        val id = UUID.randomUUID().toString()
        val now = clock()
        val seq = db.writeTransaction { tx ->
            val next = tx.get("SELECT COALESCE(MAX(seq), 0) + 1 FROM ai_messages WHERE session_id = ?", listOf(sessionId)) { it.getLong(0) ?: 1L }
            tx.execute(
                "INSERT INTO ai_messages (id, session_id, seq, role, content, tool_calls, tool_call_id, tool_name, state, error, created_ms) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?)",
                listOf(id, sessionId, next, role.db, content, toolCalls, toolCallId, toolName, state.db, now),
            )
            tx.execute("UPDATE ai_sessions SET updated_ms = ? WHERE id = ?", listOf(now, sessionId))
            if (role == ChatRole.User) tx.execute("UPDATE ai_sessions SET title = ? WHERE id = ? AND title = ''", listOf(titleOf(content), sessionId))
            next
        }
        return ChatMessage(id, sessionId, seq.toInt(), role, content, toolCalls, toolCallId, toolName, state, null, now)
    }

    /** Perbarui isi / status satu pesan (mis. teks streaming, galat). Kolom null = tidak diubah. */
    override suspend fun updateMessage(id: String, content: String?, state: ChatState?, error: String?, toolCalls: String?, clearError: Boolean) {
        db.execute(
            """UPDATE ai_messages SET content = COALESCE(?, content), state = COALESCE(?, state),
                      error = CASE WHEN ? THEN NULL ELSE COALESCE(?, error) END, tool_calls = COALESCE(?, tool_calls) WHERE id = ?""",
            listOf(content, state?.db, if (clearError) 1L else 0L, error, toolCalls, id),
        )
    }

    /** Hapus pesan mulai [fromSeq] (untuk ulangi jawaban / ubah pesan terakhir). */
    override suspend fun deleteFrom(sessionId: String, fromSeq: Int) {
        db.execute("DELETE FROM ai_messages WHERE session_id = ? AND seq >= ?", listOf(sessionId, fromSeq.toLong()))
    }

    /** Aplikasi mati saat streaming → pesan tertinggal "streaming" ditandai terputus. */
    override suspend fun recoverInterrupted(): Int {
        val n = db.get("SELECT COUNT(*) FROM ai_messages WHERE state = 'streaming'") { it.getLong(0) ?: 0L }
        if (n > 0) db.execute("UPDATE ai_messages SET state = 'error', error = ? WHERE state = 'streaming'", listOf(INTERRUPTED))
        return n.toInt()
    }

    companion object {
        const val TITLE_MAX = 60
        const val INTERRUPTED = "Jawaban terputus karena aplikasi ditutup."

        /** Judul dari pesan pertama: satu baris, maks. [TITLE_MAX] karakter, dipotong di batas kata. */
        fun titleOf(text: String): String {
            val line = text.trim().lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty().replace(Regex("\\s+"), " ")
            if (line.length <= TITLE_MAX) return line
            val cut = line.take(TITLE_MAX)
            val space = cut.lastIndexOf(' ')
            return (if (space > TITLE_MAX / 2) cut.substring(0, space) else cut).trimEnd() + "…"
        }

        private fun previewOf(text: String): String = text.replace(Regex("\\s+"), " ").trim().take(120)
    }
}
