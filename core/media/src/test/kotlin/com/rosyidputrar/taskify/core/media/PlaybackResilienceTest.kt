package com.rosyidputrar.taskify.core.media

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tes unit murni (tanpa Robolectric / ExoPlayer) untuk mesin ketahanan
 * Agen B: auto-resume dan auto-skip. Integrasi ke pemutar diuji di
 * `MusicPlayerTest` (dengan ExoPlayer Robolectric + resolver palsu).
 */
class PlaybackResilienceTest {

    // ---------------------------------------------------------- auto-resume

    @Test fun resumeDiperlukanBilaLaguTerputusSaatBerbunyi() {
        val tracker = PlaybackAutoResumeTracker()
        tracker.onPlaybackStarted("aaaaaaaaaaa")
        assertTrue(tracker.onInterrupted("aaaaaaaaaaa"), "gangguan saat berbunyi dicatat")
        assertTrue(tracker.shouldAutoResume("aaaaaaaaaaa"))
    }

    @Test fun resumeTidakUntukLaguLain() {
        val tracker = PlaybackAutoResumeTracker()
        tracker.onPlaybackStarted("aaaaaaaaaaa")
        assertFalse(tracker.onInterrupted("bbbbbbbbbbb"), "gangguan lagu lain bukan urusan lagu ini")
        assertFalse(tracker.shouldAutoResume("bbbbbbbbbbb"))
        assertFalse(tracker.shouldAutoResume("aaaaaaaaaaa"))
    }

    @Test fun jedaPenggunaMematikanResume() {
        val tracker = PlaybackAutoResumeTracker()
        tracker.onPlaybackStarted("aaaaaaaaaaa")
        tracker.onUserPaused()
        assertFalse(tracker.onInterrupted("aaaaaaaaaaa"), "sudah dijeda pengguna: tidak ada yang terputus")
        assertFalse(tracker.shouldAutoResume("aaaaaaaaaaa"))
    }

    @Test fun jedaDiTengahPemulihanMematikanResume() {
        val tracker = PlaybackAutoResumeTracker()
        tracker.onPlaybackStarted("aaaaaaaaaaa")
        assertTrue(tracker.onInterrupted("aaaaaaaaaaa"))
        tracker.onUserPaused()
        assertFalse(tracker.shouldAutoResume("aaaaaaaaaaa"), "pengguna menekan jeda selagi re-resolve jalan")
    }

    @Test fun resumeHanyaSekali() {
        val tracker = PlaybackAutoResumeTracker()
        tracker.onPlaybackStarted("aaaaaaaaaaa")
        tracker.onInterrupted("aaaaaaaaaaa")
        assertTrue(tracker.shouldAutoResume("aaaaaaaaaaa"))
        tracker.onResumed("aaaaaaaaaaa")
        assertFalse(tracker.shouldAutoResume("aaaaaaaaaaa"), "sudah dipakai sekali, jangan lanjut lagi")
    }

    @Test fun laguBaruMelupakanGangguanLama() {
        val tracker = PlaybackAutoResumeTracker()
        tracker.onPlaybackStarted("aaaaaaaaaaa")
        tracker.onInterrupted("aaaaaaaaaaa")
        tracker.onPlaybackStarted("bbbbbbbbbbb")
        assertFalse(tracker.shouldAutoResume("aaaaaaaaaaa"), "ganti lagu menghapus gangguan lama")
    }

    @Test fun resetMelupakanSemuanya() {
        val tracker = PlaybackAutoResumeTracker()
        tracker.onPlaybackStarted("aaaaaaaaaaa")
        tracker.onInterrupted("aaaaaaaaaaa")
        tracker.reset()
        assertFalse(tracker.shouldAutoResume("aaaaaaaaaaa"))
        assertFalse(tracker.onInterrupted("aaaaaaaaaaa"))
    }

    // ----------------------------------------------------------- auto-skip

    @Test fun empatGalatPertamaBolehSkipKelimaBerhenti() {
        val tracker = ConsecutivePlaybackErrorTracker()
        assertTrue(tracker.onTerminalError(), "galat 1 → skip")
        assertTrue(tracker.onTerminalError(), "galat 2 → skip")
        assertTrue(tracker.onTerminalError(), "galat 3 → skip")
        assertTrue(tracker.onTerminalError(), "galat 4 → skip")
        assertFalse(tracker.onTerminalError(), "galat 5 → tampilkan galat, berhenti")
        assertEquals(5, tracker.count)
        assertFalse(tracker.onTerminalError(), "galat 6 tetap berhenti")
    }

    @Test fun laguBerbunyiMemutusRantaiGalat() {
        val tracker = ConsecutivePlaybackErrorTracker()
        repeat(4) { tracker.onTerminalError() }
        assertEquals(4, tracker.count)
        tracker.onPlaybackStarted()
        assertEquals(0, tracker.count, "lagu berhasil berbunyi → rantai putus")
        assertTrue(tracker.onTerminalError(), "galat berikut dihitung dari 1 lagi")
        assertEquals(1, tracker.count)
    }

    @Test fun resetMemutusRantaiGalat() {
        val tracker = ConsecutivePlaybackErrorTracker()
        repeat(3) { tracker.onTerminalError() }
        tracker.reset()
        assertEquals(0, tracker.count)
        assertTrue(tracker.onTerminalError())
    }

    @Test fun batasMaksBisaDiubah() {
        val tracker = ConsecutivePlaybackErrorTracker(maxConsecutiveErrors = 2)
        assertTrue(tracker.onTerminalError(), "galat 1 → skip")
        assertFalse(tracker.onTerminalError(), "galat 2 (batas) → berhenti")
    }
}
