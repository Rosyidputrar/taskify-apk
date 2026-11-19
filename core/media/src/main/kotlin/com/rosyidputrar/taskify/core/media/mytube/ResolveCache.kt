/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Cache hasil resolve stream (porting ArchiveTune Tahap 6, C2-16/17):
 * padanan cache resolve 256 entri + `pinnedFormatId` ArchiveTune
 * (`YoutubeiStreamRepository` / `YTPlayerUtils`), disederhanakan untuk
 * arsitektur pemutar Taskify (resolve per lagu + prefetch, bukan timeline
 * penuh — lihat PLAN.md §E).
 *
 * - Kunci: videoId + kualitas + preferMp4a + fingerprint auth. Sesi login
 *   berubah → fingerprint berubah → entri lama tidak terpakai.
 * - TTL: `expiresInSeconds` URL dikurangi margin 5 menit; tanpa
 *   `expiresInSeconds` atau sisa <= margin → tidak di-cache.
 * - LRU 256 entri (access-order). `pinnedItag` per video dicatat terpisah
 *   dan bertahan melewati kedaluwarsa entri: resolve berikut memakai itag
 *   yang sama (format yang sama), bukan hasil ranking ulang.
 * - Dedup in-flight TIDAK ada di sini: orkestrasi pemutar sudah mencegah
 *   resolve ganda untuk lagu yang sama (guard `resolving` + prefetch,
 *   lihat `MusicPlayerTest`); cache ini hanya untuk pemakaian ulang.
 * - Sinkron (`synchronized`): hanya dipakai dari coroutine scope pemutar.
 */
package com.rosyidputrar.taskify.core.media.mytube

import com.rosyidputrar.taskify.core.domain.media.MusicQuality

data class ResolveCacheKey(
    val videoId: String,
    val quality: MusicQuality,
    val preferMp4a: Boolean,
    val authFingerprint: String,
    /** Jaringan metered saat resolve (Tahap 8, C2-25): `Auto` metered memilih tier lain dari `Auto` biasa. */
    val networkMetered: Boolean = false,
)

class ResolveCache(
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val maxEntries: Int = MAX_ENTRIES,
) {
    private data class Entry(val stream: DirectStream, val expiresAtMs: Long)

    private val cache = object : LinkedHashMap<ResolveCacheKey, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ResolveCacheKey, Entry>): Boolean =
            size > maxEntries
    }
    private val pinnedItags = mutableMapOf<String, Int>()

    /** Ambil stream yang masih segar, atau null (miss/kedaluwarsa). */
    @Synchronized
    fun get(key: ResolveCacheKey): DirectStream? {
        val entry = cache[key] ?: return null
        if (nowMs() >= entry.expiresAtMs) {
            cache.remove(key)
            return null
        }
        return entry.stream
    }

    /** Simpan hasil resolve yang sukses; sekaligus catat itag-nya sebagai pinned. */
    @Synchronized
    fun put(key: ResolveCacheKey, stream: DirectStream) {
        pinnedItags[key.videoId] = stream.itag
        val ttlMs = stream.expiresInSeconds
            ?.takeIf { it > EXPIRY_MARGIN_SECONDS }
            ?.let { (it - EXPIRY_MARGIN_SECONDS) * 1_000L }
            ?: return
        cache[key] = Entry(stream, nowMs() + ttlMs)
    }

    /** Buang entri kunci ini (dipakai saat URL-nya kena 403: jangan disajikan lagi). */
    @Synchronized
    fun remove(key: ResolveCacheKey) {
        cache.remove(key)
    }

    /** Itag terakhir yang dipakai video ini (bertahan melewati kedaluwarsa entri). */
    @Synchronized
    fun pinnedItag(videoId: String): Int? = pinnedItags[videoId]

    @Synchronized
    fun clear() {
        cache.clear()
        pinnedItags.clear()
    }

    @Synchronized
    fun size(): Int = cache.size

    companion object {
        const val MAX_ENTRIES = 256
        const val EXPIRY_MARGIN_SECONDS = 300
    }
}
