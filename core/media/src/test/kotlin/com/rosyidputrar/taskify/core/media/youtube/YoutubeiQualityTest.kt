package com.rosyidputrar.taskify.core.media.youtube

import com.rosyidputrar.taskify.core.domain.media.MusicQuality
import com.rosyidputrar.taskify.core.youtubei.YoutubeiAudioQuality
import org.junit.Test
import kotlin.test.assertEquals

/** Pemetaan kualitas pemutar → tier kualitas youtubei.js (Tahap 8, C2-23). */
class YoutubeiQualityTest {

    @Test fun `Auto High Low dipetakan ke tier youtubei yang sama`() {
        assertEquals(YoutubeiAudioQuality.AUTO, MusicQuality.Auto.toYoutubeiQuality())
        assertEquals(YoutubeiAudioQuality.HIGH, MusicQuality.High.toYoutubeiQuality())
        assertEquals(YoutubeiAudioQuality.LOW, MusicQuality.Low.toYoutubeiQuality())
    }

    @Test fun `Highest dipetakan ke HIGHEST`() {
        assertEquals(YoutubeiAudioQuality.HIGHEST, MusicQuality.Highest.toYoutubeiQuality())
    }
}
