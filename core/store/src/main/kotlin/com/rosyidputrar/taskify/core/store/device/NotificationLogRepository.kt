package com.rosyidputrar.taskify.core.store.device

import com.powersync.PowerSyncDatabase
import com.powersync.db.SqlCursor
import com.rosyidputrar.taskify.core.domain.device.MoneyNotif
import com.rosyidputrar.taskify.core.domain.device.NotifCategory
import com.rosyidputrar.taskify.core.domain.device.ParcelNotif
import com.rosyidputrar.taskify.core.domain.device.ParcelStage
import com.rosyidputrar.taskify.core.domain.finance.TxKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/** Status draf transaksi dari notifikasi bank/e-wallet. */
enum class DraftState(val db: String) {
    Pending("pending"), Saved("saved"), Dismissed("dismissed");

    companion object {
        fun of(v: String?) = entries.firstOrNull { it.db == v }
    }
}

/** Tindak lanjut yang sudah dibuat dari satu notifikasi. */
enum class NotifAction(val db: String, val label: String) {
    Task("task", "Jadi tugas"), Note("note", "Jadi catatan"), Transaction("tx", "Dicatat");

    companion object {
        fun of(v: String?) = entries.firstOrNull { it.db == v }
    }
}

data class NotifEntry(
    val id: String,
    val pkg: String,
    val appLabel: String,
    val key: String?,
    val conversation: String?,
    val title: String?,
    val text: String?,
    val category: NotifCategory,
    val postedAt: Long,
    val day: String,
    val hidden: Boolean = false,
    val handled: NotifAction? = null,
    val money: MoneyNotif? = null,
    val draft: DraftState? = null,
    val parcel: ParcelNotif? = null,
)

/** Notifikasi baru dari pendengar (isi sudah disaring sesuai tingkat penyimpanan & sensor OTP). */
data class NewNotif(
    val pkg: String,
    val appLabel: String,
    val key: String?,
    val conversation: String?,
    val title: String?,
    val text: String?,
    val category: NotifCategory,
    val postedAt: Long,
    val day: String,
    val money: MoneyNotif? = null,
    val parcel: ParcelNotif? = null,
)

data class AppCount(val pkg: String, val label: String, val count: Int)

/** Status terakhir satu paket. */
data class Parcel(val ref: String, val pkg: String, val appLabel: String, val stage: ParcelStage, val updatedAt: Long, val title: String?)

/** Riwayat notifikasi lokal (tabel `notif_log` + hitungan `notif_daily`). */
class NotificationLogRepository(private val db: PowerSyncDatabase, private val crypto: NotifLogCrypto = PlainNotifLogCrypto) {

    /**
     * Catat satu notifikasi. Notifikasi yang sama persis (paket + key + judul + isi) dalam
     * [DEDUPE_MS] terakhir diabaikan — aplikasi sering memperbarui notifikasi tanpa isi baru.
     * D-06: judul/isi tersimpan terenkripsi, jadi dedupe membandingkan hasil dekripsi di memori
     * (bukan SQL) — baris lama plaintext tetap ikut terbandingkan.
     * @return id baris, atau null bila duplikat.
     */
    suspend fun log(n: NewNotif): String? {
        val since = n.postedAt - DEDUPE_MS
        val candidates = db.getAll(
            "SELECT title, text FROM notif_log WHERE package = ? AND IFNULL(n_key, '') = ? AND posted_ms >= ?",
            listOf(n.pkg, n.key.orEmpty(), since),
        ) { c -> c.getString(0) to c.getString(1) }
        if (candidates.any { (t, x) -> decForCompare(t) == n.title && decForCompare(x) == n.text }) return null
        val id = UUID.randomUUID().toString()
        db.writeTransaction { tx ->
            tx.execute(
                """INSERT INTO notif_log (id, package, app_label, n_key, conversation, title, text, category, posted_ms, day, hidden,
                   money_amount, money_kind, money_party, money_source, draft, parcel_stage, parcel_ref)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?)""",
                listOf(
                    id, n.pkg, n.appLabel, n.key, enc(n.conversation), enc(n.title), enc(n.text), n.category.name, n.postedAt, n.day,
                    n.money?.amount, n.money?.kind?.value, enc(n.money?.counterparty), enc(n.money?.source), n.money?.let { DraftState.Pending.db },
                    n.parcel?.stage?.name, n.parcel?.reference,
                ),
            )
            bump(tx, n.day, n.pkg, n.appLabel)
        }
        return id
    }

    /** Tingkat "Hitungan saja": hanya menambah hitungan harian. */
    suspend fun count(day: String, pkg: String, label: String) {
        db.writeTransaction { tx -> bump(tx, day, pkg, label) }
    }

    private fun bump(tx: com.powersync.db.internal.PowerSyncTransaction, day: String, pkg: String, label: String) {
        // Tabel PowerSync adalah view ber-trigger: jumlah baris yang berubah selalu 0, jadi cek dulu.
        val id = "$day|$pkg"
        val exists = tx.getOptional("SELECT 1 FROM notif_daily WHERE id = ?", listOf(id)) { 1 } != null
        if (exists) tx.execute("UPDATE notif_daily SET count = count + 1, app_label = ? WHERE id = ?", listOf(label, id))
        else tx.execute("INSERT INTO notif_daily (id, day, package, app_label, count) VALUES (?, ?, ?, ?, 1)", listOf(id, day, pkg, label))
    }

    /** Notifikasi satu hari (yang tidak disembunyikan), terbaru dulu. */
    fun watchDay(day: String): Flow<List<NotifEntry>> =
        db.watch("SELECT $COLUMNS FROM notif_log WHERE day = ? AND hidden = 0 ORDER BY posted_ms DESC", listOf(day)) { it.toEntry() }

    /** Notifikasi sejak timestamp tertentu (untuk ringkasan pasca-sesi Mode Fokus). */
    fun watchSince(sinceMs: Long, limit: Int = 80): Flow<List<NotifEntry>> =
        db.watch("SELECT $COLUMNS FROM notif_log WHERE posted_ms >= ? AND hidden = 0 ORDER BY posted_ms DESC LIMIT ?", listOf(sinceMs, limit)) { it.toEntry() }

    /** Hari yang punya catatan notifikasi (untuk pita hari), terbaru dulu. */
    fun watchDays(): Flow<List<String>> = db.watch("SELECT DISTINCT day FROM notif_daily ORDER BY day DESC LIMIT 60") { it.getString(0)!! }

    /** Jumlah notifikasi per hari dalam rentang (dari `notif_daily`, tetap ada setelah isi dibuang). */
    fun watchDailyTotals(from: String, to: String): Flow<Map<String, Int>> = db.watch(
        "SELECT day, SUM(count) FROM notif_daily WHERE day BETWEEN ? AND ? GROUP BY day",
        listOf(from, to),
    ) { c -> c.getString(0)!! to (c.getLong(1)?.toInt() ?: 0) }.map { it.toMap() }

    /** Jumlah per aplikasi dalam rentang, terbanyak dulu. */
    fun watchAppCounts(from: String, to: String): Flow<List<AppCount>> = db.watch(
        """SELECT package, MAX(app_label), SUM(count) AS n FROM notif_daily WHERE day BETWEEN ? AND ?
           GROUP BY package ORDER BY n DESC""",
        listOf(from, to),
    ) { c -> AppCount(c.getString(0)!!, c.getString(1) ?: c.getString(0)!!, c.getLong(2)?.toInt() ?: 0) }

    /** Jam-jam (ms) notifikasi satu aplikasi/semua pada satu hari — untuk titik di "Sepanjang hari". */
    fun watchTimes(day: String, pkg: String? = null): Flow<List<Long>> = db.watch(
        "SELECT posted_ms FROM notif_log WHERE day = ? AND (? IS NULL OR package = ?) ORDER BY posted_ms",
        listOf(day, pkg, pkg),
    ) { it.getLong(0) ?: 0L }

    fun watchDrafts(): Flow<List<NotifEntry>> =
        db.watch("SELECT $COLUMNS FROM notif_log WHERE draft = 'pending' ORDER BY posted_ms DESC") { it.toEntry() }

    fun watchDraftCount(): Flow<Int> =
        db.watch("SELECT COUNT(*) FROM notif_log WHERE draft = 'pending'") { it.getLong(0)?.toInt() ?: 0 }.map { it.firstOrNull() ?: 0 }

    /** Status terakhir tiap paket dalam [sinceMs] terakhir (tanpa nomor resi → per notifikasi). */
    fun watchParcels(sinceMs: Long): Flow<List<Parcel>> = db.watch(
        """SELECT IFNULL(parcel_ref, id), package, app_label, parcel_stage, posted_ms, title FROM notif_log
           WHERE parcel_stage IS NOT NULL AND posted_ms >= ? ORDER BY posted_ms DESC""",
        listOf(sinceMs),
    ) { c -> Parcel(c.getString(0)!!, c.getString(1)!!, c.getString(2) ?: c.getString(1)!!, ParcelStage.valueOf(c.getString(3)!!), c.getLong(4) ?: 0, dec(c.getString(5))) }
        .map { rows -> rows.distinctBy { it.pkg to it.ref }.sortedWith(compareBy<Parcel> { it.stage == ParcelStage.Delivered }.thenByDescending { it.updatedAt }) }

    suspend fun get(id: String): NotifEntry? = db.getOptional("SELECT $COLUMNS FROM notif_log WHERE id = ?", listOf(id)) { it.toEntry() }

    suspend fun setHidden(id: String, hidden: Boolean) {
        db.execute("UPDATE notif_log SET hidden = ? WHERE id = ?", listOf(if (hidden) 1 else 0, id))
    }

    suspend fun markHandled(id: String, action: NotifAction?) {
        db.execute("UPDATE notif_log SET handled = ? WHERE id = ?", listOf(action?.db, id))
    }

    suspend fun setDraft(id: String, state: DraftState) {
        db.execute("UPDATE notif_log SET draft = ? WHERE id = ?", listOf(state.db, id))
    }

    /**
     * Cari di judul/isi/nama aplikasi (Spotlight). D-06: judul/isi tersimpan terenkripsi, jadi
     * pencarian mendekripsi 500 baris terbaru di memori lalu mencocokkan di sana — hanya 500
     * baris terbaru yang bisa dicari (bukan seluruh riwayat seperti LIKE SQL sebelumnya).
     */
    suspend fun search(query: String, limit: Int = 20): List<NotifEntry> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        return db.getAll(
            "SELECT $COLUMNS FROM notif_log WHERE hidden = 0 ORDER BY posted_ms DESC LIMIT 500",
            emptyList(),
        ) { it.toEntry() }
            .filter { e -> e.title?.lowercase()?.contains(q) == true || e.text?.lowercase()?.contains(q) == true || e.appLabel.lowercase().contains(q) }
            .take(limit)
    }

    /** Buang isi notifikasi lebih lama dari [beforeDay]; hitungan harian disimpan [COUNT_KEEP_DAYS]. */
    suspend fun prune(beforeDay: String, countsBeforeDay: String) {
        db.writeTransaction { tx ->
            // Draf pending ikut terhapus bila melewati masa simpan: isi notifikasi bank yang
            // tidak ditinjau tidak boleh tersimpan tanpa batas waktu.
            tx.execute("DELETE FROM notif_log WHERE day < ?", listOf(beforeDay))
            tx.execute("DELETE FROM notif_daily WHERE day < ?", listOf(countsBeforeDay))
        }
    }

    /** Hapus isi dari satu aplikasi (saat aplikasi dikecualikan). */
    suspend fun forgetApp(pkg: String) {
        db.execute("DELETE FROM notif_log WHERE package = ?", listOf(pkg))
    }

    suspend fun clearAll() {
        db.writeTransaction { tx ->
            tx.execute("DELETE FROM notif_log")
            tx.execute("DELETE FROM notif_daily")
        }
    }

    private fun SqlCursor.toEntry(): NotifEntry {
        val amount = getLong(11)
        val kind = TxKind.entries.firstOrNull { it.value == getString(12) }
        return NotifEntry(
            id = getString(0)!!,
            pkg = getString(1)!!,
            appLabel = getString(2) ?: getString(1)!!,
            key = getString(3),
            conversation = dec(getString(4)),
            title = dec(getString(5)),
            text = dec(getString(6)),
            category = runCatching { NotifCategory.valueOf(getString(7)!!) }.getOrDefault(NotifCategory.Other),
            postedAt = getLong(8) ?: 0,
            day = getString(9)!!,
            hidden = (getLong(10) ?: 0) != 0L,
            money = if (amount != null && kind != null) MoneyNotif(amount, kind, dec(getString(13)), dec(getString(14)) ?: "") else null,
            draft = DraftState.of(getString(15)),
            parcel = getString(16)?.let { s -> runCatching { ParcelNotif(ParcelStage.valueOf(s), getString(17)) }.getOrNull() },
            handled = NotifAction.of(getString(18)),
        )
    }

    /** D-06: enkripsi kolom teks sensitif; null tetap null. */
    private fun enc(v: String?): String? = v?.let { crypto.encrypt(it) }

    /**
     * D-06: dekripsi kolom; baris lama plaintext dikembalikan apa adanya oleh [NotifLogCrypto].
     * Baris yang gagal dibuka (kunci berubah / data rusak) menampilkan null — bukan ciphertext
     * mentah — supaya ciphertext tidak pernah bocor ke UI.
     */
    private fun dec(v: String?): String? = v?.let { runCatching { crypto.decrypt(it) }.getOrNull() }

    /** Dedupe: dekripsi gagal mengembalikan nilai mentah (tidak pernah sama dengan judul/isi polos). */
    private fun decForCompare(v: String?): String? = v?.let { runCatching { crypto.decrypt(it) }.getOrDefault(it) }

    companion object {
        const val DEDUPE_MS = 10 * 60_000L
        const val COUNT_KEEP_DAYS = 400
        private const val COLUMNS = """id, package, app_label, n_key, conversation, title, text, category, posted_ms, day, hidden,
            money_amount, money_kind, money_party, money_source, draft, parcel_stage, parcel_ref, handled"""
    }
}
