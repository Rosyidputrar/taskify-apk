package com.rosyidputrar.taskify.core.domain.sandi

import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.Base64

/** Tujuan ekspor isi brankas. Semuanya TEKS BIASA — hanya dibuat setelah master password dicek ulang. */
enum class ExportKind {
    /** CSV sandi yang bisa diimpor Pengelola Sandi Google (juga Chrome, Bitwarden, 1Password, dll.). */
    GoogleCsv,

    /** QR `otpauth-migration://` yang dipindai dari menu "Transfer akun" Google Authenticator. */
    GoogleAuthenticator,

    /** Satu URI `otpauth://` per baris — untuk Aegis, 2FAS, Proton Pass, dan aplikasi lain. */
    OtpauthList,
}

/** Alasan kunci 2FA tidak ikut QR Google Authenticator. */
enum class GoogleAuthIssue(val label: String) {
    Period("periode bukan 30 dtk"),
    Algorithm("algoritme bukan SHA1"),
    Digits("bukan 6 digit"),
}

/** Ringkasan sebelum ekspor sandi ke CSV. */
data class CsvSummary(
    /** Baris yang masuk CSV (item yang punya sandi). */
    val rows: Int,
    /** Item tanpa sandi (mis. hanya kunci 2FA) — tidak ditulis. */
    val skippedNoPassword: Int,
    /** Baris tanpa alamat web yang valid — Google bisa menolaknya (aplikasi lain umumnya menerima). */
    val withoutWebAddress: Int,
)

/** Rencana QR Google Authenticator: satu URI per QR, berurutan. */
data class GoogleAuthPlan(
    val codes: List<String>,
    val accounts: Int,
    /** Item yang tidak bisa dipindahkan ke Google Authenticator dengan aman: (judul, alasan). */
    val skipped: List<Pair<String, GoogleAuthIssue>>,
)

/**
 * Ekspor Sandi ke aplikasi lain. Semua keluaran berisi rahasia dalam TEKS BIASA, jadi:
 *  - tidak pernah ditulis ke log (fungsi di sini murni, tidak mencetak apa pun);
 *  - isi sandi TIDAK diubah sama sekali (mis. tidak ada awalan `'` anti-formula seperti CSV
 *    Keuangan) — mengubahnya berarti sandi yang diimpor salah.
 * Urutan keluaran stabil: judul (tanpa beda huruf besar/kecil), lalu nama pengguna.
 */
object SandiExport {
    /** Header CSV ekspor Chrome / Pengelola Sandi Google — dikenali importer Google & pengelola lain. */
    val CSV_HEADER = listOf("name", "url", "username", "password", "note")

    /** Batas ukuran per QR Google Authenticator supaya tetap mudah dipindai kamera. */
    const val GOOGLE_MAX_ACCOUNTS_PER_CODE = 8
    const val GOOGLE_MAX_PAYLOAD_BYTES = 550

    /** Nama/penerbit lebih panjang dipotong (hanya label; kunci rahasia tidak pernah dipotong). */
    const val GOOGLE_MAX_LABEL = 64

    private fun ordered(items: List<ItemPayload>) =
        items.sortedWith(compareBy<ItemPayload> { it.title.trim().lowercase() }.thenBy { it.username.lowercase() }.thenBy { it.url })

    // ================================================================== CSV sandi

    /** Item yang masuk CSV: hanya yang punya sandi. */
    private fun withPassword(items: List<ItemPayload>) = ordered(items).filter { it.password.isNotEmpty() }

    fun csvSummary(items: List<ItemPayload>): CsvSummary {
        val rows = withPassword(items)
        return CsvSummary(rows.size, items.size - rows.size, rows.count { safeUrl(it.url) == null })
    }

    /**
     * Alamat untuk kolom `url`: alamat web dinormalkan ke `https://…` ("jago.com" → "https://jago.com")
     * karena Google menolak alamat tanpa skema. Yang bukan alamat web (mis. `androidapp://com.bca`,
     * catatan bebas) ditulis apa adanya — pengelola lain mengenalinya, Google akan melewatinya.
     */
    fun csvUrl(url: String): String = safeUrl(url) ?: url.trim()

    /** Kolom `name`: judul, atau host situs bila judul kosong. */
    fun csvName(item: ItemPayload): String = item.title.trim().ifEmpty { displayHost(item.url) }

    /** CSV RFC 4180 (pemisah koma, baris CRLF, tanpa BOM) dengan header [CSV_HEADER]. */
    fun passwordsCsv(items: List<ItemPayload>): String {
        val sb = StringBuilder()
        sb.append(CSV_HEADER.joinToString(",")).append("\r\n")
        withPassword(items).forEach { p ->
            listOf(csvName(p), csvUrl(p.url), p.username, p.password, p.notes).joinTo(sb, ",") { csvField(it) }
            sb.append("\r\n")
        }
        return sb.toString()
    }

    /**
     * Kolom CSV: dikutip bila memuat koma, kutip, baris baru, atau spasi/tab di awal/akhir (supaya
     * tidak dipangkas importer). Kutip di dalam digandakan. Isi tidak diubah.
     */
    fun csvField(value: String): String {
        val quote = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' } ||
            (value.isNotEmpty() && (value.first().isWhitespace() || value.last().isWhitespace()))
        return if (quote) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    // ================================================================== daftar otpauth://

    private fun issuerOf(item: ItemPayload) = item.title.trim().ifEmpty { if (item.url.isBlank()) "" else displayHost(item.url) }

    /** Item yang punya kunci 2FA, urut stabil. */
    fun withTotp(items: List<ItemPayload>): List<ItemPayload> = ordered(items).filter { it.totp != null }

    /** Satu URI `otpauth://totp/…` per baris (LF), lengkap dengan digit/periode/algoritme aslinya. */
    fun otpauthList(items: List<ItemPayload>): String = withTotp(items).joinToString("") { p ->
        Totp.otpauthUri(p.totp!!, issuerOf(p), p.username) + "\n"
    }

    // ================================================================== Google Authenticator

    /**
     * Google Authenticator hanya menjamin kode yang benar untuk TOTP standar (SHA1, 6 digit, 30 dtk);
     * format migrasinya bahkan tidak punya kolom periode. Yang lain tidak diikutkan supaya tidak ada
     * akun yang "pindah" tapi kodenya salah.
     */
    fun googleIssue(config: TotpConfig): GoogleAuthIssue? = when {
        config.period != Totp.DEFAULT_PERIOD -> GoogleAuthIssue.Period
        config.algorithm != TotpAlgorithm.SHA1 -> GoogleAuthIssue.Algorithm
        config.digits != 6 -> GoogleAuthIssue.Digits
        else -> null
    }

    /**
     * Susun QR "Transfer akun" Google Authenticator (`otpauth-migration://offline?data=…`).
     * [batchId] sama untuk semua QR dalam satu ekspor (Google memakainya untuk menggabungkan).
     */
    fun googleMigration(items: List<ItemPayload>, batchId: Int): GoogleAuthPlan {
        require(batchId >= 0) { "batchId harus positif" }
        val skipped = mutableListOf<Pair<String, GoogleAuthIssue>>()
        val accounts = mutableListOf<ByteArray>()
        withTotp(items).forEach { p ->
            val cfg = p.totp!!
            val issue = googleIssue(cfg)
            if (issue != null) {
                skipped += (p.title.trim().ifEmpty { p.username.ifBlank { "(tanpa judul)" } }) to issue
                return@forEach
            }
            // Kunci sudah divalidasi saat disimpan; tetap dicek supaya tidak pernah menulis rahasia kosong.
            val secret = Totp.decodeBase32(cfg.secret)?.takeIf { it.size >= Totp.MIN_SECRET_BYTES } ?: return@forEach
            val issuer = clip(issuerOf(p))
            val name = clip(p.username.trim().ifEmpty { issuer.ifEmpty { "Taskify" } })
            accounts += otpParameters(secret, name, issuer)
            secret.fill(0)
        }
        val batches = mutableListOf<MutableList<ByteArray>>()
        var size = 0
        accounts.forEach { a ->
            val last = batches.lastOrNull()
            val entry = a.size + 1 + varintSize(a.size.toLong())
            if (last == null || last.size >= GOOGLE_MAX_ACCOUNTS_PER_CODE || size + entry > GOOGLE_MAX_PAYLOAD_BYTES) {
                batches += mutableListOf(a)
                size = entry
            } else {
                last += a
                size += entry
            }
        }
        val codes = batches.mapIndexed { index, batch ->
            val payload = migrationPayload(batch, batchSize = batches.size, batchIndex = index, batchId = batchId)
            "otpauth-migration://offline?data=" + URLEncoder.encode(Base64.getEncoder().encodeToString(payload), "UTF-8")
        }
        accounts.forEach { it.fill(0) }
        return GoogleAuthPlan(codes, accounts.size, skipped)
    }

    /** Potong label ke [GOOGLE_MAX_LABEL] karakter tanpa memotong pasangan surrogate (emoji). */
    private fun clip(text: String): String {
        if (text.codePointCount(0, text.length) <= GOOGLE_MAX_LABEL) return text
        return text.substring(0, text.offsetByCodePoints(0, GOOGLE_MAX_LABEL))
    }

    // ------------------------------------------------------------------ protobuf (MigrationPayload)

    // message OtpParameters { bytes secret = 1; string name = 2; string issuer = 3;
    //   Algorithm algorithm = 4 (SHA1 = 1); DigitCount digits = 5 (SIX = 1);
    //   OtpType type = 6 (TOTP = 2); int64 counter = 7; }
    private fun otpParameters(secret: ByteArray, name: String, issuer: String): ByteArray = ByteArrayOutputStream().apply {
        bytesField(1, secret)
        bytesField(2, name.toByteArray(Charsets.UTF_8))
        if (issuer.isNotEmpty()) bytesField(3, issuer.toByteArray(Charsets.UTF_8))
        varintField(4, 1) // SHA1
        varintField(5, 1) // 6 digit
        varintField(6, 2) // TOTP
    }.toByteArray()

    // message MigrationPayload { repeated OtpParameters otp_parameters = 1; int32 version = 2;
    //   int32 batch_size = 3; int32 batch_index = 4; int32 batch_id = 5; }
    private fun migrationPayload(accounts: List<ByteArray>, batchSize: Int, batchIndex: Int, batchId: Int): ByteArray = ByteArrayOutputStream().apply {
        accounts.forEach { bytesField(1, it) }
        varintField(2, 1)
        varintField(3, batchSize.toLong())
        varintField(4, batchIndex.toLong())
        varintField(5, batchId.toLong())
    }.toByteArray()

    private fun ByteArrayOutputStream.bytesField(field: Int, value: ByteArray) {
        varint(((field shl 3) or 2).toLong())
        varint(value.size.toLong())
        write(value)
    }

    private fun ByteArrayOutputStream.varintField(field: Int, value: Long) {
        varint((field shl 3).toLong())
        varint(value)
    }

    private fun ByteArrayOutputStream.varint(value: Long) {
        var v = value
        while (v and 0x7fL.inv() != 0L) {
            write(((v and 0x7f) or 0x80).toInt())
            v = v ushr 7
        }
        write(v.toInt())
    }

    private fun varintSize(value: Long): Int {
        var v = value
        var n = 1
        while (v and 0x7fL.inv() != 0L) { v = v ushr 7; n++ }
        return n
    }

    // ================================================================== nama berkas

    /** "taskify-sandi-2026-11-19.csv" / "taskify-2fa-2026-11-19.txt". [date] = yyyy-MM-dd. */
    fun fileName(kind: ExportKind, date: String): String = when (kind) {
        ExportKind.GoogleCsv -> "taskify-sandi-$date.csv"
        ExportKind.OtpauthList -> "taskify-2fa-$date.txt"
        ExportKind.GoogleAuthenticator -> error("QR tidak disimpan sebagai berkas")
    }

    fun mimeType(kind: ExportKind): String = when (kind) {
        ExportKind.GoogleCsv -> "text/csv"
        ExportKind.OtpauthList -> "text/plain"
        ExportKind.GoogleAuthenticator -> error("QR tidak disimpan sebagai berkas")
    }
}
