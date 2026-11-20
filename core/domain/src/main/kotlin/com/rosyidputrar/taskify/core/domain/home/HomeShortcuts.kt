package com.rosyidputrar.taskify.core.domain.home

/**
 * Jenis sasaran pintasan di Hari ini. Kode entitas SAMA dengan `EntityType` (task, note, …) supaya
 * hasil pencarian, Sampah, dan pintasan memakai satu kosakata; sisanya khusus pintasan.
 */
enum class ShortcutType(val code: String, val label: String) {
    Task("task", "Tugas"),
    Journal("journal_entry", "Jurnal"),
    Note("note", "Catatan"),
    Transaction("transaction", "Transaksi"),
    Snippet("snippet", "Snippet"),
    Project("code_project", "Project"),
    Config("config_file", "Konfigurasi"),
    AiChat("ai_session", "Asisten"),
    TaskList("task_list", "Daftar tugas"),
    Wallet("wallet", "Dompet"),
    Tag("tag", "Tag"),
    View("view", "Tampilan"),
    ;

    companion object {
        fun from(code: String?): ShortcutType? = entries.firstOrNull { it.code == code }
    }
}

/** Alamat satu pintasan: jenis + id sasaran (untuk [ShortcutType.View]: nama [QuickView]). */
data class ShortcutRef(val type: ShortcutType, val target: String) {
    /** Kunci stabil (juga id baris di tabel lokal `home_pins`). */
    val key: String get() = "${type.code}:$target"

    /** Tampilan cepat yang dirujuk, bila ini pintasan tampilan. */
    val view: QuickView? get() = if (type == ShortcutType.View) QuickView.from(target) else null

    companion object {
        fun view(v: QuickView) = ShortcutRef(ShortcutType.View, v.name)

        fun parse(key: String): ShortcutRef? {
            val i = key.indexOf(':')
            if (i <= 0 || i == key.lastIndex) return null
            val type = ShortcutType.from(key.substring(0, i)) ?: return null
            val target = key.substring(i + 1)
            if (type == ShortcutType.View && QuickView.from(target) == null) return null
            return ShortcutRef(type, target)
        }
    }
}

/** Modul pemilik (nama `TaskifyModule`) — dipakai UI untuk warna & ikon; domain tidak kenal tema. */
enum class ShortcutModule { Tasks, Journal, Notes, Finance, Vault, Sandi, Calendar, Focus, Assistant, Review, Notifications, Wellbeing, Music, Files, Tags }

/**
 * Tampilan cepat: membuka modul LANGSUNG di tab/filter yang tepat, bukan halaman depannya.
 * Nama enum disimpan di database lokal — jangan diganti; tambahkan yang baru di akhir.
 */
enum class QuickView(val label: String, val hint: String, val module: ShortcutModule) {
    TasksToday("Tugas hari ini", "Jatuh tempo hari ini", ShortcutModule.Tasks),
    TasksOverdue("Tugas terlambat", "Lewat tenggat", ShortcutModule.Tasks),
    TasksUpcoming("Tugas mendatang", "Setelah hari ini", ShortcutModule.Tasks),
    JournalToday("Tulis jurnal", "Halaman hari ini", ShortcutModule.Journal),
    JournalInsight("Insight jurnal", "Mood & kebiasaan menulis", ShortcutModule.Journal),
    FinanceTransactions("Transaksi bulan ini", "Daftar & saring transaksi", ShortcutModule.Finance),
    FinanceBudget("Anggaran", "Sisa anggaran bulan ini", ShortcutModule.Finance),
    FinanceDebt("Hutang & piutang", "Yang belum lunas", ShortcutModule.Finance),
    NewTransaction("Catat transaksi", "Langsung ke formulir", ShortcutModule.Finance),
    NotificationDrafts("Draf dari notifikasi", "Transaksi menunggu disimpan", ShortcutModule.Notifications),
    SandiCodes("Kode 2FA", "Buka kunci lalu lihat kode", ShortcutModule.Sandi),
    SandiGenerator("Buat kata sandi", "Generator Sandi", ShortcutModule.Sandi),
    VaultConfig("Berkas konfigurasi", "Vault · Konfig", ShortcutModule.Vault),
    VaultProjects("Project kode", "Vault · Project", ShortcutModule.Vault),
    CalendarToday("Agenda hari ini", "Kalender di tanggal ini", ShortcutModule.Calendar),
    FocusStart("Mulai fokus", "Pomodoro", ShortcutModule.Focus),
    AiNewChat("Tanya asisten", "Chat baru", ShortcutModule.Assistant),
    Review("Tinjauan", "Ringkasan minggu ini", ShortcutModule.Review),
    Music("Musik", "Lanjutkan mendengarkan", ShortcutModule.Music),
    Wellbeing("Waktu layar", "Kesehatan Digital hari ini", ShortcutModule.Wellbeing),
    Files("Berkas", "Kirim & unduh berkas", ShortcutModule.Files),
    ;

    companion object {
        fun from(name: String?): QuickView? = entries.firstOrNull { it.name == name }
    }
}

/** Satu kartu pintasan yang siap ditampilkan. */
data class HomeShortcut(
    val ref: ShortcutRef,
    val title: String,
    /** Keterangan kecil dari sumbernya (tanggal, bahasa, lingkungan, …). */
    val subtitle: String? = null,
    val pinned: Boolean = false,
    /** Sasaran masih ada (tidak dihapus/di Sampah). Pintasan yatim tetap terlihat di "Susun Beranda". */
    val available: Boolean = true,
    /** ISO waktu terakhir diubah — untuk "Lanjutkan · 2 jam lalu". */
    val updatedAt: String? = null,
)

object HomeShortcuts {
    /** Batas pintasan disematkan — baris Hari ini tetap ringkas. */
    const val MAX_PINNED = 12

    /** Berapa kartu "Lanjutkan" otomatis paling banyak. */
    const val MAX_RECENT = 6

    /**
     * Isi baris Pintasan: yang disematkan (masih ada) dulu sesuai urutan pengguna, lalu
     * "Lanjutkan" otomatis yang BELUM disematkan, tanpa duplikat.
     */
    fun row(pinned: List<HomeShortcut>, recent: List<HomeShortcut>, showRecent: Boolean): List<HomeShortcut> {
        val shown = pinned.filter { it.available }
        if (!showRecent) return shown
        val keys = pinned.mapTo(HashSet()) { it.ref.key }
        return shown + recent.asSequence().filter { it.ref.key !in keys }.distinctBy { it.ref.key }.take(MAX_RECENT).toList()
    }

    /** "baru saja", "5 mnt lalu", "3 jam lalu", "kemarin", "4 hari lalu", lalu tanggal pendek. */
    fun agoLabel(updatedAtIso: String?, nowMs: Long): String? {
        val ms = parseIsoMs(updatedAtIso) ?: return null
        val diff = (nowMs - ms).coerceAtLeast(0L)
        val min = diff / 60_000L
        val hours = min / 60
        val days = hours / 24
        return when {
            min < 1 -> "baru saja"
            min < 60 -> "$min mnt lalu"
            hours < 24 -> "$hours jam lalu"
            days < 2 -> "kemarin"
            days < 7 -> "$days hari lalu"
            else -> null
        }
    }

    /** Parse ISO-8601 UTC sederhana ("2026-11-20T14:30:00.123Z" / tanpa milidetik) → epoch ms. */
    fun parseIsoMs(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        val m = ISO.matchEntire(iso.trim()) ?: return null
        val (y, mo, d, h, mi, s) = m.destructured
        val frac = m.groupValues[7].takeIf { it.isNotEmpty() }?.padEnd(3, '0')?.take(3)?.toLong() ?: 0L
        val days = daysFromCivil(y.toLong(), mo.toInt(), d.toInt())
        return ((days * 24 + h.toLong()) * 60 + mi.toLong()) * 60_000L + s.toLong() * 1000L + frac
    }

    private val ISO = Regex("""(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})(?:\.(\d+))?(?:Z|\+00:00)?""")

    /** Hari sejak 1970-01-01 (algoritma Howard Hinnant). */
    private fun daysFromCivil(year: Long, month: Int, day: Int): Long {
        val y = if (month <= 2) year - 1 else year
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val mp = (month + 9) % 12
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }
}
