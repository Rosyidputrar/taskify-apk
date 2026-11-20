package com.rosyidputrar.taskify.core.media

/**
 * Niat putar untuk auto-resume setelah gangguan sesaat (Agen B, optimasi
 * ketahanan).
 *
 * Masalah yang dipecahkan: pemulihan galat putar Tahap 7 (C2-19) hanya
 * men-STAGE URL baru (status Jeda) — pemutaran lanjut hanya bila pengguna
 * menekan putar. Bila lagu memang sedang berbunyi saat terputus (jaringan
 * putus sesaat, lalu kembali), pemutaran harus lanjut sendiri dari posisi
 * terakhir (posisi sudah dipertahankan via `resumeId`/`resumeMs`).
 *
 * Yang TIDAK dilakukan tracker ini (sengaja):
 * - Jeda oleh pengguna (tombol jeda UI, notifikasi, pewaktu tidur,
 *   berhenti) mematikan niat putar — pemulihan tetap jalan, tapi tanpa
 *   lanjut otomatis. Jeda dari listener ExoPlayer TIDAK mematikan niat:
 *   jeda itu juga terjadi saat audio-focus hilang sesaat, dan media3
 *   yang mengurus lanjut-nya sendiri (lihat bawah).
 * - Resume setelah audio-focus TIDAK dibangun di sini: media3 1.9.2
 *   (`AudioFocusManager.handlePlatformAudioFocusChange`, diverifikasi
 *   dari bytecode AAR) sudah menjeda saat loss transient
 *   (`WAIT_FOR_CALLBACK`) dan lanjut sendiri saat gain
 *   (`PLAY_WHEN_READY`). Membangun resume fokus sendiri berarti
 *   memutar dua kali / merebut fokus saat panggilan telepon.
 *
 * Invarian: tiap percobaan pemulihan (`reResolveAfterFailure`) selalu
 * didahului `onInterrupted` untuk lagu yang sama, jadi flag gangguan
 * tidak pernah bocor ke percobaan lain tanpa lewat sini; `onPlaybackStarted`
 * (lagu berbunyi / ganti lagu) dan `onUserPaused` selalu membersihkannya.
 * Karena itu tidak ada `reset()` di jalur galat akhir — tidak dibutuhkan.
 */
internal class PlaybackAutoResumeTracker {
    /** Lagu yang terakhir mulai berbunyi (niat putar masih hidup). */
    private var playingVideoId: String? = null

    /** Lagu yang putarnya terputus galat dan sedang dipulihkan. */
    private var interruptedVideoId: String? = null

    /** Lagu mulai berbunyi (juga lewat settle Preparing→Playing): niat putar hidup, gangguan lama dilupakan. */
    fun onPlaybackStarted(videoId: String) {
        playingVideoId = videoId
        interruptedVideoId = null
    }

    /** Pengguna menjeda/menghentikan sendiri: tidak ada yang boleh lanjut otomatis. */
    fun onUserPaused() {
        playingVideoId = null
        interruptedVideoId = null
    }

    /**
     * Galat yang bisa pulih memutus lagu yang sedang diputar: catat
     * gangguannya. Kembalikan `false` bila lagu itu memang tidak sedang
     * berbunyi (mis. pengguna sudah menjeda) — pemulihan tetap jalan,
     * tapi tanpa lanjut otomatis.
     */
    fun onInterrupted(videoId: String): Boolean {
        if (playingVideoId != videoId) return false
        interruptedVideoId = videoId
        return true
    }

    /** Pemulihan berhasil: boleh lanjut otomatis bila lagu ini yang terputus? */
    fun shouldAutoResume(videoId: String): Boolean = interruptedVideoId == videoId

    /** Lanjut otomatis sudah dipakai: jangan dipakai dua kali. */
    fun onResumed(videoId: String) {
        if (interruptedVideoId == videoId) interruptedVideoId = null
    }

    /** Berhenti / ganti sesi: lupakan semuanya. */
    fun reset() {
        playingVideoId = null
        interruptedVideoId = null
    }
}
