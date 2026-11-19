/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.rosyidputrar.taskify.core.media.mytube

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tahap 7 (C2-19): tracker sekali-per-lagu — murni JVM. */
class PlaybackStreamRecoveryTrackerTest {

    @Test
    fun `percobaan pertama diizinkan, kedua ditolak sampai pulih`() {
        val tracker = PlaybackStreamRecoveryTracker()
        assertTrue(tracker.registerRetryAttempt("VID12345678"))
        assertFalse(tracker.registerRetryAttempt("VID12345678"))
        // Lagu lain tidak terpengaruh... (tracker hanya mengingat satu lagu:
        // lagu lain justru menggantikan yang diingat).
        assertTrue(tracker.registerRetryAttempt("VID99999999"))
        assertFalse(tracker.registerRetryAttempt("VID99999999"))
    }

    @Test
    fun `pulih membebaskan lagu yang sama`() {
        val tracker = PlaybackStreamRecoveryTracker()
        assertTrue(tracker.registerRetryAttempt("VID12345678"))
        tracker.onPlaybackRecovered("VID12345678")
        assertTrue(tracker.registerRetryAttempt("VID12345678"))
    }

    @Test
    fun `pulih lagu lain tidak membebaskan`() {
        val tracker = PlaybackStreamRecoveryTracker()
        assertTrue(tracker.registerRetryAttempt("VID12345678"))
        tracker.onPlaybackRecovered("VID99999999")
        assertFalse(tracker.registerRetryAttempt("VID12345678"))
    }

    @Test
    fun `ganti lagu membebaskan`() {
        val tracker = PlaybackStreamRecoveryTracker()
        assertTrue(tracker.registerRetryAttempt("VID12345678"))
        tracker.onMediaItemChanged("VID99999999")
        assertTrue(tracker.registerRetryAttempt("VID12345678"))
    }

    @Test
    fun `reset membebaskan apa pun lagunya`() {
        val tracker = PlaybackStreamRecoveryTracker()
        assertTrue(tracker.registerRetryAttempt("VID12345678"))
        tracker.reset()
        assertTrue(tracker.registerRetryAttempt("VID12345678"))
    }
}
