package com.rosyidputrar.taskify.core.innertube

import com.rosyidputrar.taskify.core.innertube.models.YouTubeLocale
import kotlinx.coroutines.flow.StateFlow

/**
 * Seam antara `:core:innertube` dan pemakai di atasnya (`:core:media`,
 * `:feature:media`) — sumber kebenaran tunggal untuk konteks auth dan locale.
 *
 * Ditulis baru untuk rewrite ini. ArchiveTune punya interface bernama sama
 * (`MusicBackend.kt`, 247 baris) yang juga mendeklarasikan SELURUH API
 * katalog/player; separuh itu sengaja TIDAK dibawa ke sini: di clone
 * ArchiveTune tidak ditemukan implementornya (PLAN A.7), dan fasad `YouTube`
 * Tahap 2 adalah kelas biasa yang menerima `InnerTube` lewat konstruktor,
 * bukan implementasi interface ini. Bila Tahap 6-7 butuh seam yang bisa
 * diganti (mis. untuk tes ViewModel/Player), interface ini yang diperluas —
 * bukan sebaliknya.
 *
 * Yang belum ada di tahap 1 dan dicatat sebagai follow-up (bukan prasyarat):
 * proxy/DNS kustom dan `streamBypassProxy` (ada di ArchiveTune, dipakai
 * untuk DoH/stream); `InnerTube` tahap 1 tidak memakai keduanya.
 */
interface MusicBackend {
    val authStateFlow: StateFlow<PlaybackAuthState>

    var authState: PlaybackAuthState
    var locale: YouTubeLocale
    var visitorData: String?
    var dataSyncId: String?
    var cookie: String?
    var poToken: String?
    var webClientPoTokenEnabled: Boolean
    var useLoginForBrowse: Boolean

    fun currentPlaybackAuthState(): PlaybackAuthState

    fun hasLoginCookie(): Boolean

    fun hasPlaybackLoginContext(): Boolean
}
