package com.rosyidputrar.taskify.core.domain.sandi

import java.net.URI
import kotlin.math.abs
import kotlin.math.ceil

// ===================================================================== kunci otomatis

/** Pilihan kunci otomatis (menit). 0 = segera saat aplikasi ditinggalkan. */
val SANDI_AUTO_LOCK_OPTIONS = listOf(0, 1, 5, 15)
const val SANDI_DEFAULT_AUTO_LOCK = 5

/**
 * Harus dikunci sekarang? (port `shouldLock`)
 *  - Aplikasi di latar: setelah [autoLockMinutes] (0 = segera).
 *  - Terlihat tapi tanpa interaksi: setelah [autoLockMinutes] (0 = tanpa batas idle saat terlihat).
 */
fun shouldLockSandi(now: Long, lastActivity: Long, hiddenAt: Long?, autoLockMinutes: Int): Boolean {
    val limit = autoLockMinutes * 60_000L
    if (hiddenAt != null) return now - hiddenAt >= limit
    if (autoLockMinutes == 0) return false
    return now - lastActivity >= limit
}

const val CLIPBOARD_CLEAR_MS = 30_000L

// ===================================================================== ingat perangkat

const val TRUST_DAYS = 30

data class TrustMeta(val stamp: String, val expiresAt: Long)

enum class TrustState { Ok, Expired, Stale }

fun trustExpiresAt(nowMs: Long, days: Int = TRUST_DAYS) = nowMs + days * 86_400_000L

/** expired = lewat masanya; stale = kunci brankas sudah diganti (DEK ikut dirotasi). */
fun trustState(meta: TrustMeta, currentStamp: String, nowMs: Long): TrustState = when {
    nowMs >= meta.expiresAt -> TrustState.Expired
    meta.stamp != currentStamp -> TrustState.Stale
    else -> TrustState.Ok
}

fun trustDaysLeft(meta: TrustMeta, nowMs: Long): Int = maxOf(0, ceil((meta.expiresAt - nowMs) / 86_400_000.0).toInt())

// ===================================================================== penggantian kunci (rotasi)

enum class RotationBlocker(val message: String) {
    Offline("Mengganti kunci brankas butuh internet supaya semua perangkat tetap bisa membukanya. Sambungkan lalu coba lagi."),
    NotSynced("Sinkronisasi pertama belum selesai. Tunggu sebentar lalu coba lagi."),
    Downloading("Masih mengunduh perubahan dari server. Tunggu sampai selesai lalu coba lagi."),
    PendingUploads("Masih ada perubahan di perangkat ini yang belum terkirim. Tunggu sampai terkirim (lihat Pengaturan → Sinkronisasi) lalu coba lagi."),
    KeyChanged("Kunci brankas sudah diganti di perangkat lain. Tunggu sinkronisasi, buka brankas dengan master password terbaru, lalu coba lagi."),
    Unreachable("Tidak bisa memeriksa server. Periksa koneksi lalu coba lagi."),
}

/** Keadaan yang diperiksa sebelum rotasi dimulai. [remoteWrappedKey]: null = tidak ada brankas; [UNREACHABLE] = tidak terjawab. */
data class RotationProbe(
    val connected: Boolean,
    val hasSynced: Boolean,
    val downloading: Boolean,
    val pendingUploads: Long,
    val remoteWrappedKey: String?,
)

const val UNREACHABLE = "\u0000unreachable"

/** null = boleh mulai (port `rotationBlocker`). */
fun rotationBlocker(p: RotationProbe, localWrappedKey: String): RotationBlocker? = when {
    !p.connected -> RotationBlocker.Offline
    !p.hasSynced -> RotationBlocker.NotSynced
    p.downloading -> RotationBlocker.Downloading
    p.pendingUploads > 0 -> RotationBlocker.PendingUploads
    p.remoteWrappedKey == UNREACHABLE -> RotationBlocker.Unreachable
    p.remoteWrappedKey != localWrappedKey -> RotationBlocker.KeyChanged
    else -> null
}

/** Kode penolakan server (trigger/RPC brankas), kelas SQLSTATE 23 → permanen. */
object VaultError {
    const val KEY_CHANGED = "23V01"
    const val INCOMPLETE = "23V02"
    const val STALE_KEY = "23V03"
    const val TOO_LARGE = "23V04"
    const val INVALID = "23V05"
}

fun rotationRejectedMessage(code: String?): String {
    val tail = " Tidak ada yang berubah di server. Tunggu beberapa detik sampai sinkron, lalu buka brankas dengan master password LAMA."
    return when (code) {
        VaultError.KEY_CHANGED -> "Kunci brankas sudah diganti di perangkat lain sebelum perubahan ini terkirim.$tail"
        VaultError.INCOMPLETE -> "Ada data brankas di server yang belum sampai ke perangkat ini, atau baru saja diubah di perangkat lain.$tail"
        VaultError.TOO_LARGE -> "Data brankas terlalu besar untuk dikirim sekaligus. Hapus versi konfigurasi/riwayat yang tidak perlu dulu.$tail"
        else -> "Server menolak penggantian kunci${code?.let { " ($it)" } ?: ""}.$tail"
    }
}

enum class RemoteKeyState { None, Exists, Unreachable }

val REMOTE_KEY_MESSAGE = mapOf(
    RemoteKeyState.Exists to "Brankas sudah dibuat di perangkat lain. Tunggu sinkronisasi selesai, lalu buka dengan master password-nya.",
    RemoteKeyState.Unreachable to "Tidak bisa memeriksa server. Membuat brankas pertama kali butuh internet; sambungkan lalu coba lagi.",
)

const val KEY_REJECTED_MESSAGE =
    "Server menolak brankas ini karena brankas sudah dibuat di perangkat lain. Tidak ada sandi yang disimpan. Buka lagi Sandi lalu masukkan master password brankas yang pertama dibuat."

/** Hasil penggantian kunci: berapa baris dienkripsi ulang / dilewati (tidak bisa dibuka dengan kunci lama). */
data class RekeyResult(
    val reencrypted: Int = 0,
    val skipped: Int = 0,
    val logReencrypted: Int = 0,
    val logSkipped: Int = 0,
    val configReencrypted: Int = 0,
    val configSkipped: Int = 0,
)

fun skippedMessage(r: RekeyResult): String? {
    val parts = buildList {
        if (r.skipped > 0) add("${r.skipped} sandi")
        if (r.configSkipped > 0) add("${r.configSkipped} versi konfigurasi")
    }
    return if (parts.isEmpty()) null else "${parts.joinToString(" dan ")} lama tidak bisa dibuka dan dibiarkan apa adanya."
}

// ===================================================================== tampilan item

/** Hanya http(s) yang boleh dibuka dari URL item (tanpa skema → https). */
fun safeUrl(input: String): String? {
    val text = input.trim()
    if (text.isEmpty() || Regex("[\\s<>\"'`]").containsMatchIn(text)) return null
    val withScheme = if (Regex("^[a-z][a-z0-9+.-]*:", RegexOption.IGNORE_CASE).containsMatchIn(text)) text else "https://$text"
    return try {
        val u = URI(withScheme)
        val scheme = u.scheme?.lowercase()
        val host = u.host ?: return null
        if (scheme != "https" && scheme != "http") return null
        if (!host.contains('.') && host != "localhost") return null
        u.toString()
    } catch (_: Exception) {
        null
    }
}

/** "https://www.github.com/login" → "github.com". */
fun displayHost(input: String): String = safeUrl(input)?.let { URI(it).host.removePrefix("www.") } ?: input.trim()

/** Huruf/angka pertama bermakna, kapital; "?" bila kosong. */
fun avatarInitial(title: String): String = title.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"

/** Indeks warna avatar deterministik dari judul (djb2, sama dengan webapp). */
fun avatarIndex(seed: String, count: Int): Int {
    var h = 5381
    seed.lowercase().codePoints().forEach { cp -> h = (h shl 5) + h + cp }
    return abs(h) % count
}

/** Isi berkas .txt kunci pemulihan (format sama dengan webapp). */
fun recoveryFileText(recoveryKey: String, email: String?, date: String): String = listOfNotNull(
    "KUNCI PEMULIHAN — SANDI TASKIFY",
    email?.let { "Akun   : $it" },
    "Dibuat : $date",
    "",
    recoveryKey,
    "",
    "Gunakan kunci ini bila lupa master password (layar kunci → \"Lupa master password?\").",
    "Simpan di tempat aman dan TERPISAH dari perangkat ini (dicetak, atau di pengelola",
    "sandi lain). Siapa pun yang memegang kunci ini beserta data terenkripsi Anda dapat",
    "membuka brankas. Tanpa master password DAN kunci ini, tidak ada yang bisa memulihkan data.",
    "",
).joinToString("\n")

/** Riwayat kode TOTP disimpan 90 hari. */
const val TOTP_LOG_RETENTION_DAYS = 90
const val TOTP_LOG_PAGE_SIZE = 200
