package com.rosyidputrar.taskify

import com.rosyidputrar.taskify.feature.media.isWebViewLoadableUrl
import com.rosyidputrar.taskify.feature.media.isYouTubeMusicStoreListingUrl
import com.rosyidputrar.taskify.feature.media.isYouTubeUrl
import com.rosyidputrar.taskify.feature.media.mergeMyTubeCookies
import com.rosyidputrar.taskify.feature.media.myTubeCookieOrigin
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Helper URL + cookie layar login (JVM-murni, tanpa WebView). */
class MusicLoginWebUtilsTest {
    @Test fun mergeDedupPertamaMenang() {
        val merged = mergeMyTubeCookies(
            mapOf(
                "https://music.youtube.com" to "LOGIN_INFO=abc; SAPISID=pertama; lain=1",
                "https://www.youtube.com" to "SAPISID=keduax; SID=zzz",
                "https://youtube.com" to "HSID=hh",
            ),
            currentUrl = "https://music.youtube.com/watch?v=abc",
        )
        assertEquals("LOGIN_INFO=abc; SAPISID=pertama; lain=1; SID=zzz; HSID=hh", merged)
    }

    @Test fun mergeOriginHalamanDidahulukan() {
        val merged = mergeMyTubeCookies(
            mapOf(
                "https://www.youtube.com" to "SAPISID=dari-www",
                "https://music.youtube.com" to "SAPISID=dari-music",
            ),
            currentUrl = "https://www.youtube.com/",
        )
        assertTrue(merged?.contains("SAPISID=dari-www") == true, "origin halaman menang atas daftar tetap")
    }

    @Test fun mergeKosongNullDanBagianTanpaSamaDenganDiabaikan() {
        assertNull(mergeMyTubeCookies(emptyMap(), "https://music.youtube.com"))
        assertEquals(
            "LOGIN_INFO=abc",
            mergeMyTubeCookies(mapOf("https://music.youtube.com" to "tanpa-sama-dengan; LOGIN_INFO=abc; =kosong")),
        )
    }

    @Test fun isYouTubeUrl() {
        assertTrue("https://music.youtube.com/watch".isYouTubeUrl())
        assertTrue("https://www.youtube.com/".isYouTubeUrl())
        assertTrue("https://youtube.com".isYouTubeUrl())
        assertFalse("https://accounts.google.com/ServiceLogin".isYouTubeUrl())
        assertFalse("https://notyoutube.com".isYouTubeUrl())
        assertFalse((null as String?).isYouTubeUrl())
    }

    @Test fun isWebViewLoadableUrl() {
        assertTrue("https://music.youtube.com".isWebViewLoadableUrl())
        assertTrue("http://contoh.id".isWebViewLoadableUrl())
        assertTrue("javascript:void(0)".isWebViewLoadableUrl())
        assertFalse("intent://x#Intent;scheme=https;end".isWebViewLoadableUrl())
        assertFalse("market://details?id=x".isWebViewLoadableUrl())
    }

    @Test fun isYouTubeMusicStoreListingUrl() {
        assertTrue("https://play.google.com/store/apps/details?id=com.google.android.apps.youtube.music".isYouTubeMusicStoreListingUrl())
        assertFalse("https://play.google.com/store/apps/details?id=com.google.android.youtube".isYouTubeMusicStoreListingUrl())
        assertFalse("https://play.google.com/store/apps?hl=id".isYouTubeMusicStoreListingUrl())
        assertFalse("https://music.youtube.com".isYouTubeMusicStoreListingUrl())
    }

    @Test fun myTubeCookieOrigin() {
        assertEquals("https://music.youtube.com", "https://music.youtube.com/watch?v=1".myTubeCookieOrigin())
        // Skema dipertahankan seperti ArchiveTune (http tetap http).
        assertEquals("http://www.youtube.com", "http://www.youtube.com/".myTubeCookieOrigin())
        assertNull("https://accounts.google.com/".myTubeCookieOrigin())
        assertNull((null as String?).myTubeCookieOrigin())
    }
}
