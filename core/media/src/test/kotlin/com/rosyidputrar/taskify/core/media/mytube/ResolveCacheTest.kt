/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.rosyidputrar.taskify.core.media.mytube

import com.rosyidputrar.taskify.core.domain.media.MusicQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tahap 6 (C2-16/17): cache hasil resolve + pinned itag — murni JVM.
 */
class ResolveCacheTest {

    private fun stream(itag: Int = 251, expiresInSeconds: Int? = 3600) = DirectStream(
        url = "https://rr.test/x?itag=$itag",
        mimeType = "audio/webm; codecs=\"opus\"",
        itag = itag,
        bitrate = 160_000,
        expiresInSeconds = expiresInSeconds,
        loudnessDb = null,
        perceptualLoudnessDb = null,
    )

    private fun key(videoId: String = "VID12345678", fingerprint: String = "fp1") =
        ResolveCacheKey(videoId, MusicQuality.Auto, false, fingerprint)

    @Test
    fun `miss lalu hit sebelum kedaluwarsa`() {
        var now = 1_000_000L
        val cache = ResolveCache(nowMs = { now })
        assertNull(cache.get(key()))
        cache.put(key(), stream(expiresInSeconds = 3600))
        assertEquals("https://rr.test/x?itag=251", cache.get(key())?.url)
        // Masih segar 3000 detik kemudian (TTL 3300 detik = 3600 - margin 300).
        now += 3_000_000L
        assertEquals("https://rr.test/x?itag=251", cache.get(key())?.url)
    }

    @Test
    fun `kedaluwarsa setelah TTL habis`() {
        var now = 1_000_000L
        val cache = ResolveCache(nowMs = { now })
        cache.put(key(), stream(expiresInSeconds = 3600))
        now += 3_301_000L
        assertNull(cache.get(key()))
    }

    @Test
    fun `tanpa expiresInSeconds tidak di-cache`() {
        val cache = ResolveCache()
        cache.put(key(), stream(expiresInSeconds = null))
        assertNull(cache.get(key()))
        // Tapi itag-nya tetap tercatat sebagai pinned.
        assertEquals(251, cache.pinnedItag("VID12345678"))
    }

    @Test
    fun `sisa di bawah margin tidak di-cache`() {
        val cache = ResolveCache()
        cache.put(key(), stream(expiresInSeconds = 200))
        assertNull(cache.get(key()))
        assertEquals(251, cache.pinnedItag("VID12345678"))
    }

    @Test
    fun `kunci berbeda tidak saling mengganggu`() {
        val cache = ResolveCache()
        cache.put(key(), stream())
        assertNull(cache.get(key(fingerprint = "fp2")))
        assertNull(cache.get(key(videoId = "VID99999999")))
    }

    @Test
    fun `pinnedItag bertahan melewati kedaluwarsa entri`() {
        var now = 1_000_000L
        val cache = ResolveCache(nowMs = { now })
        cache.put(key(), stream(itag = 249, expiresInSeconds = 3600))
        now += 9_999_999L
        assertNull(cache.get(key()))
        assertEquals(249, cache.pinnedItag("VID12345678"))
    }

    @Test
    fun `remove membuang entri tapi bukan pinned`() {
        val cache = ResolveCache()
        cache.put(key(), stream())
        cache.remove(key())
        assertNull(cache.get(key()))
        assertEquals(251, cache.pinnedItag("VID12345678"))
    }

    @Test
    fun `LRU membuang entri terlama saat penuh`() {
        val cache = ResolveCache(maxEntries = 2)
        cache.put(key("A"), stream())
        cache.put(key("B"), stream())
        cache.put(key("C"), stream())
        assertEquals(2, cache.size())
        assertNull(cache.get(key("A")))
        assertEquals("https://rr.test/x?itag=251", cache.get(key("B"))?.url)
        assertEquals("https://rr.test/x?itag=251", cache.get(key("C"))?.url)
    }

    @Test
    fun `clear membuang semua termasuk pinned`() {
        val cache = ResolveCache()
        cache.put(key(), stream())
        cache.clear()
        assertNull(cache.get(key()))
        assertNull(cache.pinnedItag("VID12345678"))
        assertEquals(0, cache.size())
    }
}
