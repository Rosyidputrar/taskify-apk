/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

// Diadaptasi dari ArchiveTune `app/.../utils/YTPlayerUtils.kt`
// (`markStreamClientFailed` / `isStreamClientTemporarilyBlocked` /
// `buildFailedClientKey`, c7f0e24) — hanya paket yang diubah ke Taskify;
// `ConcurrentHashMap` dan konstanta dipertahankan, pemakaian
// `MyTube.currentPlaybackAuthState()` diganti parameter eksplisit
// (fingerprint diteruskan pemanggil) supaya bisa diuji murni JVM.
// Lihat licenses/ARCHIVETUNE.md.
//
// Aturan ArchiveTune yang di-porting di sini (INVESTIGASI-C2, C2-19):
// klien yang memberi URL stream yang gagal HTTP 403/404/410/416
// di-blacklist 10 menit untuk video + sesi itu (kunci
// `fingerprint:videoId:KLIEN@VERSI`); resolve berikutnya melewati klien
// itu dan pindah ke klien lain. URL yang sudah (hampir) kedaluwarsa TIDAK
// mem-blacklist kliennya — 403-nya soal umur URL, bukan soal klien.

package com.rosyidputrar.taskify.core.media.mytube

import com.rosyidputrar.taskify.core.innertube.models.MyTubeClient
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Blacklist sementara klien stream yang gagal (Tahap 7, C2-19).
 * Murni JVM — [nowMs] bisa di-injeksi tes.
 */
class FailedStreamClients(
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val failedUntil = ConcurrentHashMap<String, Long>()

    /**
     * Tandai klien [clientKey] gagal untuk [videoId] + [authFingerprint].
     * Hanya status di [RETRYABLE_STREAM_RESPONSE_CODES] yang dicatat
     * (403/404/410/416 — URL mati, bukan kliennya, bila di luar itu);
     * kunci kosong diabaikan.
     */
    fun markFailed(
        videoId: String,
        clientKey: String?,
        httpStatusCode: Int?,
        authFingerprint: String,
    ) {
        if (httpStatusCode != null && httpStatusCode !in RETRYABLE_STREAM_RESPONSE_CODES) return
        val normalized = normalizeClientKey(clientKey)
        if (normalized.isEmpty()) return
        failedUntil[buildKey(videoId, normalized, authFingerprint)] =
            nowMs() + FAILED_CLIENT_BACKOFF_MS
    }

    /** `true` bila [clientKey] masih dalam masa blacklist untuk video + sesi ini. */
    fun isBlocked(videoId: String, clientKey: String?, authFingerprint: String): Boolean {
        val normalized = normalizeClientKey(clientKey)
        if (normalized.isEmpty()) return false
        val key = buildKey(videoId, normalized, authFingerprint)
        val until = failedUntil[key] ?: return false
        if (until <= nowMs()) {
            failedUntil.remove(key)
            return false
        }
        return true
    }

    /** Kunci-kunci klien yang sedang di-blacklist untuk video + sesi ini (untuk taksonomi galat). */
    fun blockedClientKeys(videoId: String, authFingerprint: String): Set<String> {
        val now = nowMs()
        val prefix = "$authFingerprint:$videoId:"
        return failedUntil.entries
            .filter { (key, until) -> key.startsWith(prefix) && until > now }
            .map { (key, _) -> key.removePrefix(prefix) }
            .toSet()
    }

    /** Kosongkan semua blacklist (logout / tes). */
    fun clear() {
        failedUntil.clear()
    }

    companion object {
        /** Masa blacklist klien gagal (ArchiveTune `FAILED_CLIENT_BACKOFF_MS`). */
        const val FAILED_CLIENT_BACKOFF_MS = 10 * 60 * 1000L

        /** Ambang umur URL: kedaluwarsa dalam 60 detik dianggap (hampir) kedaluwarsa. */
        const val STREAM_URL_EXPIRY_SAFETY_MS = 60_000L

        /** Status HTTP stream yang boleh memicu blacklist + retry (ArchiveTune). */
        val RETRYABLE_STREAM_RESPONSE_CODES = setOf(403, 404, 410, 416)

        /** Kunci blacklist ArchiveTune: `fingerprint:videoId:KLIEN@VERSI`. */
        fun buildKey(videoId: String, clientKey: String, authFingerprint: String): String =
            "$authFingerprint:$videoId:${normalizeClientKey(clientKey)}"

        /** Kunci klien dari objek klien (`NAMA@VERSI`, huruf besar). */
        fun clientKey(client: MyTubeClient): String =
            "${client.clientName}@${client.clientVersion}".uppercase(Locale.US)

        fun normalizeClientKey(clientKey: String?): String =
            clientKey?.trim()?.takeIf { it.isNotBlank() }?.uppercase(Locale.US).orEmpty()

        /**
         * `true` bila URL stream sudah kedaluwarsa atau kedaluwarsa dalam
         * [STREAM_URL_EXPIRY_SAFETY_MS] (param `expire` epoch-detik);
         * tanpa param `expire` dianggap tidak kedaluwarsa.
         */
        fun isExpiredOrNearExpired(url: String?, nowMs: Long = System.currentTimeMillis()): Boolean {
            val expireSeconds = url?.let { urlQueryParam(it, "expire") }?.toLongOrNull() ?: return false
            return expireSeconds * 1000L <= nowMs + STREAM_URL_EXPIRY_SAFETY_MS
        }
    }
}
