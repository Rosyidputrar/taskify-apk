package com.rosyidputrar.taskify.core.media.youtube

import com.rosyidputrar.taskify.core.innertube.PlaybackAuthState
import com.rosyidputrar.taskify.core.innertube.YouTube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Bootstrap `visitorData` anonim sekali saat aplikasi start (Tahap 5,
 * pola ArchiveTune `App.kt`: bila `visitorData` kosong, ambil dari
 * `sw.js_data`, lalu simpan).
 *
 * Best-effort, bukan prasyarat: Worker membuktikan `player` ANDROID tidak
 * butuh `visitorData`, dan `visitorData` BUKAN penawar `LOGIN_REQUIRED`
 * yang flaky (Worker `direct.ts:23-29`) — jadi gagal di sini tidak boleh
 * menggagalkan apa pun. Gagal (jaringan mati / pola tidak ketemu / timeout
 * singkat) = lanjut anonim tanpa `visitorData`, tanpa throw, tanpa retry
 * berulang, tanpa memblokir start aplikasi.
 *
 * Bila `visitorData` sudah tersimpan, tidak ada panggilan jaringan sama
 * sekali. Bila berhasil diambil, state tersimpan juga diteruskan ke fasad
 * [YouTube] ([YouTube.applyAuthState]) supaya pemanggilan tanpa
 * `authState` eksplisit ikut memakainya.
 */
class VisitorDataBootstrap(
    private val authStore: YouTubeAuthStore,
    private val youTube: YouTube,
    private val timeoutMs: Long = BOOTSTRAP_TIMEOUT_MS,
) {
    /**
     * Pastikan `visitorData` tersedia: kembalikan yang tersimpan bila ada,
     * kalau tidak ambil sekali dari `sw.js_data` lalu simpan.
     * Mengembalikan null bila tidak ada dan tidak bisa diambil.
     * Tidak pernah melempar, kecuali pembatalan coroutine.
     */
    suspend fun ensureVisitorData(): String? {
        try {
            val stored = authStore.loadAuthState()
            stored.visitorData?.let {
                youTube.applyAuthState(stored)
                return it
            }
            val fetched = withTimeoutOrNull(timeoutMs) {
                youTube.visitorData().getOrNull()
            }?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            // MERGE (Tahap 3): gabungkan visitorData baru ke state tersimpan —
            // cookie/dataSyncId/akun yang sudah login TIDAK BOLEH hilang hanya
            // karena visitorData belum ada. Menyimpan state baru yang hanya
            // berisi visitorData akan menghapus sesi login.
            val state = stored.copy(visitorData = fetched).normalized()
            authStore.saveAuthState(state)
            youTube.applyAuthState(state)
            return state.visitorData
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null
        }
    }

    companion object {
        /**
         * Batas tunggu bootstrap — sengaja singkat: ini hiasan opsional
         * saat start, bukan alasan menahan aplikasi. Klien HTTP di bawahnya
         * sendiri punya timeout 15/10/15 detik, jadi batas inilah yang
         * benar-benar membatasi.
         */
        const val BOOTSTRAP_TIMEOUT_MS = 5_000L
    }
}
