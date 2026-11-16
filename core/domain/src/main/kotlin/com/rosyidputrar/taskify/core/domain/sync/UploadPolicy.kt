package com.rosyidputrar.taskify.core.domain.sync

/**
 * Aturan "coba lagi atau simpan ke upload_errors" untuk antrean upload — port dari
 * `db/upload-policy.ts` webapp supaya HP dan laptop memperlakukan penolakan server sama persis.
 *
 *  1. PERMANEN langsung: 22xxx, 23xxx (termasuk 23V0n penjaga kunci brankas), 42501,
 *     dan [ROW_MISSING_CODE].
 *  2. SKEMA (PGRST1xx/2xx): dicoba ulang dulu; setelah [SCHEMA_GRACE_MS] dan minimal
 *     [SCHEMA_MIN_ATTEMPTS] percobaan dianggap permanen agar antrean tidak macet selamanya.
 *  3. Lainnya (jaringan, 5xx, JWT kedaluwarsa) → selalu coba ulang.
 */
data class PostgrestLikeError(val code: String?, val message: String?)

const val ROW_MISSING_CODE = "TASKIFY_ROW_MISSING"
const val SCHEMA_GRACE_MS = 10 * 60_000L
const val SCHEMA_MIN_ATTEMPTS = 5

private val fatalCodes = listOf(
    Regex("^22...$"),
    Regex("^23...$"),
    Regex("^42501$"),
    Regex("^$ROW_MISSING_CODE$"),
)
private val schemaCodes = Regex("^PGRST[12]\\d\\d$")

fun isFatal(code: String?): Boolean = code != null && fatalCodes.any { it.matches(code) }

fun isSchemaError(code: String?): Boolean = code != null && schemaCodes.matches(code)

enum class UploadVerdict { Retry, Fatal }

/**
 * Mengingat berapa lama transaksi TERDEPAN antrean sudah gagal karena error skema.
 * Di memori saja — membuka ulang aplikasi memberi masa tenggang baru (sama dengan webapp).
 */
class UploadFailureTracker(private val now: () -> Long = System::currentTimeMillis) {
    private var key: String? = null
    private var firstAt = 0L
    private var attempts = 0

    @Synchronized
    fun verdict(key: String, code: String?): UploadVerdict {
        if (isFatal(code)) {
            reset()
            return UploadVerdict.Fatal
        }
        // Error sementara tidak mereset hitungan skema.
        if (!isSchemaError(code)) return UploadVerdict.Retry
        if (this.key != key) {
            this.key = key
            firstAt = now()
            attempts = 0
        }
        attempts++
        if (attempts >= SCHEMA_MIN_ATTEMPTS && now() - firstAt >= SCHEMA_GRACE_MS) {
            reset()
            return UploadVerdict.Fatal
        }
        return UploadVerdict.Retry
    }

    @Synchronized
    fun reset() {
        key = null
        firstAt = 0
        attempts = 0
    }
}

/** Penjelasan singkat kode error permanen untuk daftar di Pengaturan. */
fun explainErrorCode(code: String?): String = when {
    code == null -> "Ditolak server"
    code == ROW_MISSING_CODE -> "Data ini tidak ada di server (mungkin sudah dihapus permanen di perangkat lain)"
    schemaCodes.matches(code) -> "Tabel/kolom belum ada di server (migrasi database belum dijalankan?)"
    code == "23514" -> "Melanggar batas isian (mis. teks terlalu panjang atau nilai tidak valid)"
    code == "23503" -> "Mengacu ke data yang sudah tidak ada"
    code == "23502" -> "Ada isian wajib yang kosong"
    code == "23505" -> "Data dengan kunci yang sama sudah ada"
    code == "42501" -> "Ditolak izin akses (RLS)"
    code.startsWith("22") -> "Format data tidak valid"
    else -> "Ditolak server"
}
