package com.rosyidputrar.taskify.core.media

/**
 * Penghitung galat beruntun untuk auto-skip (Agen B, optimasi ketahanan).
 *
 * Bila satu lagu gagal terminal (pemulihan Tahap 7 habis / resolve gagal),
 * antrean dilewati ke lagu berikut supaya tidak macet. Setelah
 * [MAX_CONSECUTIVE_ERRORS] galat beruntun, antrean berhenti dan galat
 * tampil seperti sebelumnya (pesan + detail mentah + "Salin detail"
 * tidak berubah).
 *
 * Aturan:
 * - Galat ke-1..ke-4 → `onTerminalError()` true (boleh skip).
 * - Galat ke-5 → false (tampilkan galat, berhenti).
 * - Lagu berhasil berbunyi → rantai putus (`onPlaybackStarted`).
 * - Galat sesi login TIDAK dihitung di sini — pemutar tidak memanggil
 *   tracker untuk galat yang ditandai tidak-bisa-skip
 *   (`YtEvent.Error.skippable == false`); pengguna harus masuk lagi,
 *   bukan melewati lagu.
 */
internal class ConsecutivePlaybackErrorTracker(
    private val maxConsecutiveErrors: Int = MAX_CONSECUTIVE_ERRORS,
) {
    private var consecutiveErrors: Int = 0

    /** Lagu berhasil berbunyi: rantai galat putus. */
    fun onPlaybackStarted() {
        consecutiveErrors = 0
    }

    /**
     * Galat akhir untuk lagu ini. Kembalikan `true` bila antrean boleh
     * lanjut ke lagu berikut, `false` bila sudah mencapai batas dan
     * galat harus tampil.
     */
    fun onTerminalError(): Boolean {
        consecutiveErrors++
        return consecutiveErrors < maxConsecutiveErrors
    }

    /** Jumlah galat beruntun sejauh ini (0 = tidak ada / baru reset). */
    val count: Int get() = consecutiveErrors

    /** Berhenti / ganti sesi: lupakan rantai galat. */
    fun reset() {
        consecutiveErrors = 0
    }

    companion object {
        /** Maks galat beruntun sebelum antrean berhenti dan galat tampil. */
        const val MAX_CONSECUTIVE_ERRORS = 5
    }
}
