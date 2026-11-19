/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ensure PoToken sebelum resolve (porting ArchiveTune Tahap 5, C1-1..5,
 * C1-9/10, C2-4, C2-32): padanan `YTPlayerUtils.ensureYoutubeiPoTokensForPlayback`
 * + `preWarmYoutubeiPoTokens` ArchiveTune, di atas
 * [BotGuardTokenGenerator] (WebView headless sistem).
 *
 * Semantik penting: ensure TIDAK PERNAH menggagalkan resolve. Bila tidak
 * ada binding sesi, mint gagal, atau mesin belum siap → kembalikan
 * [PlaybackAuthState] apa adanya (tanpa token) dan resolve lanjut tanpa
 * PoToken (perilaku sebelum Tahap 5). Token hanya menaikkan peluang
 * `player`/URL diterima; ketiadaannya bukan galat.
 *
 * Anggaran mint saat resolve dibatasi 10 detik (`mintToken` 3-argumen):
 * cold start mesin bisa 45 detik, tapi resolve tidak boleh menunggu selama
 * itu — pre-warm saat aplikasi start (lihat `TaskifyApplication`) yang
 * membuat mint pertama cepat (mesin hangat: ~5 detik). Bila mesin masih
 * dingin, resolve pertama jalan tanpa token dan resolve berikut (mesin
 * sudah hangat) mendapat token.
 *
 * Invalidasi eksplisit saat logout/login belum di-wire: tidak diperlukan
 * untuk kebenaran — engine terikat `sessionId` (visitorData/dataSyncId)
 * dan cache token pemain dibersihkan saat engine baru dibuat
 * (`getOrCreateEngineLocked`); sesi baru = engine baru = token baru.
 */
package com.rosyidputrar.taskify.core.media.mytube

import android.util.Log
import com.rosyidputrar.taskify.core.innertube.PlaybackAuthState
import com.rosyidputrar.taskify.core.media.mytube.potoken.BotGuardTokenGenerator
import com.rosyidputrar.taskify.core.media.mytube.potoken.PoTokenResult

/**
 * Memastikan [PlaybackAuthState] membawa PoToken video + sesi untuk
 * [videoId]. [mint] bisa dipalsukan di tes; bawaan memakai mesin BotGuard
 * asli dengan anggaran 10 detik.
 */
class PoTokenEnsurer(
    private val mint: suspend (videoId: String, sessionId: String) -> PoTokenResult? = { videoId, sessionId ->
        runCatching {
            BotGuardTokenGenerator.mintToken(videoId, sessionId, MINT_BUDGET_MS)
        }.getOrNull()
    },
) {
    /**
     * Mint token bila belum ada untuk video ini. Token yang sudah ada
     * untuk video yang sama dipakai lagi (mesin juga punya LRU 200).
     */
    suspend fun ensure(videoId: String, authState: PlaybackAuthState): PlaybackAuthState {
        if (authState.poTokenGvsVideoId == videoId && !authState.poTokenGvs.isNullOrBlank()) {
            return authState
        }
        val binding = authState.sessionId?.trim()?.takeIf { it.isNotBlank() } ?: return authState
        val result = runCatching { mint(videoId, binding) }.getOrNull() ?: run {
            Log.d(TAG, "ensure PoToken $videoId: mint gagal, lanjut tanpa token")
            return authState
        }
        return authState.copy(
            poTokenGvs = result.playerToken,
            poTokenGvsSession = result.sessionToken,
            poTokenGvsVideoId = videoId,
            poTokenPlayer = result.playerToken,
            poTokenPlayerVideoId = videoId,
            poTokenSubs = result.playerToken,
            poTokenSubsVideoId = videoId,
            webClientPoTokenEnabled = true,
        ).normalized()
    }

    /** Panaskan mesin BotGuard di latar (best-effort; tidak pernah melempar). */
    suspend fun preWarm(authState: PlaybackAuthState) {
        val binding = authState.sessionId?.trim()?.takeIf { it.isNotBlank() } ?: return
        runCatching { BotGuardTokenGenerator.preWarm(binding) }
            .onFailure { Log.w(TAG, "pre-warm PoToken gagal (non-fatal)", it) }
    }

    /** Buang semua token + engine (dipakai saat sesi berubah; lihat catatan kelas). */
    suspend fun invalidateAll() = BotGuardTokenGenerator.invalidateAll()

    private companion object {
        const val TAG = "PoTokenEnsurer"
        const val MINT_BUDGET_MS = 10_000L
    }
}
