/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.rosyidputrar.taskify.core.media.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tahap 7 (C2-19): blacklist klien stream yang gagal — murni JVM.
 */
class FailedStreamClientsTest {

    @Test
    fun `tandai lalu terblokir untuk video dan sesi itu`() {
        val blacklist = FailedStreamClients()
        assertFalse(blacklist.isBlocked("VID12345678", "ANDROID@21.26.364", "fp1"))
        blacklist.markFailed("VID12345678", "ANDROID@21.26.364", 403, "fp1")
        assertTrue(blacklist.isBlocked("VID12345678", "ANDROID@21.26.364", "fp1"))
        // Video lain / sesi lain tidak ikut terblokir.
        assertFalse(blacklist.isBlocked("VID99999999", "ANDROID@21.26.364", "fp1"))
        assertFalse(blacklist.isBlocked("VID12345678", "ANDROID@21.26.364", "fp2"))
        // Klien lain tidak ikut terblokir.
        assertFalse(blacklist.isBlocked("VID12345678", "WEB_CREATOR@1.20260708.06.00", "fp1"))
    }

    @Test
    fun `status 404 410 416 ikut dicatat, 429 dan 500 tidak`() {
        val blacklist = FailedStreamClients()
        blacklist.markFailed("V1", "ANDROID@1", 404, "fp")
        blacklist.markFailed("V2", "ANDROID@1", 410, "fp")
        blacklist.markFailed("V3", "ANDROID@1", 416, "fp")
        blacklist.markFailed("V4", "ANDROID@1", 429, "fp")
        blacklist.markFailed("V5", "ANDROID@1", 500, "fp")
        assertTrue(blacklist.isBlocked("V1", "ANDROID@1", "fp"))
        assertTrue(blacklist.isBlocked("V2", "ANDROID@1", "fp"))
        assertTrue(blacklist.isBlocked("V3", "ANDROID@1", "fp"))
        assertFalse(blacklist.isBlocked("V4", "ANDROID@1", "fp"))
        assertFalse(blacklist.isBlocked("V5", "ANDROID@1", "fp"))
    }

    @Test
    fun `blacklist kedaluwarsa setelah 10 menit`() {
        var now = 1_000_000L
        val blacklist = FailedStreamClients(nowMs = { now })
        blacklist.markFailed("VID12345678", "ANDROID@21.26.364", 403, "fp1")
        now += 9 * 60 * 1000L
        assertTrue(blacklist.isBlocked("VID12345678", "ANDROID@21.26.364", "fp1"))
        now += 61_000L
        assertFalse(blacklist.isBlocked("VID12345678", "ANDROID@21.26.364", "fp1"))
    }

    @Test
    fun `kunci dinormalisasi huruf besar dan tanpa spasi`() {
        val blacklist = FailedStreamClients()
        blacklist.markFailed("VID12345678", "  android@21.26.364 ", 403, "fp1")
        assertTrue(blacklist.isBlocked("VID12345678", "ANDROID@21.26.364", "fp1"))
        assertTrue(blacklist.isBlocked("VID12345678", "android@21.26.364", "fp1"))
    }

    @Test
    fun `kunci kosong diabaikan, status null ikut dicatat seperti ArchiveTune`() {
        val blacklist = FailedStreamClients()
        blacklist.markFailed("VID12345678", "", 403, "fp1")
        blacklist.markFailed("VID12345678", null, 403, "fp1")
        assertTrue(blacklist.blockedClientKeys("VID12345678", "fp1").isEmpty())
        // ArchiveTune `markStreamClientFailed`: status null (tidak diketahui)
        // TIDAK menghentikan pencatatan — hanya status di luar
        // RETRYABLE_STREAM_RESPONSE_CODES yang ditolak.
        blacklist.markFailed("VID12345678", "ANDROID@1", null, "fp1")
        assertTrue(blacklist.isBlocked("VID12345678", "ANDROID@1", "fp1"))
    }

    @Test
    fun `blockedClientKeys hanya yang masih berlaku`() {
        var now = 1_000_000L
        val blacklist = FailedStreamClients(nowMs = { now })
        blacklist.markFailed("VID12345678", "ANDROID@21.26.364", 403, "fp1")
        blacklist.markFailed("VID12345678", "WEB_CREATOR@1.0", 404, "fp1")
        now += 11 * 60 * 1000L
        assertTrue(blacklist.blockedClientKeys("VID12345678", "fp1").isEmpty())
    }

    @Test
    fun `clear membuang semua blacklist`() {
        val blacklist = FailedStreamClients()
        blacklist.markFailed("VID12345678", "ANDROID@21.26.364", 403, "fp1")
        blacklist.clear()
        assertFalse(blacklist.isBlocked("VID12345678", "ANDROID@21.26.364", "fp1"))
    }

    @Test
    fun `isExpiredOrNearExpired membaca param expire`() {
        val now = 1_700_000_000_000L
        // expire 1 jam lagi: tidak kedaluwarsa.
        assertFalse(FailedStreamClients.isExpiredOrNearExpired("https://rr.test/x?expire=${now / 1000 + 3600}", now))
        // expire 30 detik lagi: hampir kedaluwarsa (ambang 60 detik).
        assertTrue(FailedStreamClients.isExpiredOrNearExpired("https://rr.test/x?expire=${now / 1000 + 30}", now))
        // expire sudah lewat: kedaluwarsa.
        assertTrue(FailedStreamClients.isExpiredOrNearExpired("https://rr.test/x?expire=${now / 1000 - 10}", now))
        // tanpa param expire: dianggap tidak kedaluwarsa.
        assertFalse(FailedStreamClients.isExpiredOrNearExpired("https://rr.test/x", now))
        assertFalse(FailedStreamClients.isExpiredOrNearExpired(null, now))
    }
}
