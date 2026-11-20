package com.rosyidputrar.taskify.core.designsystem.component

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LivingLogoFrameTest {
    private fun close(a: Float, b: Float, eps: Float = 1e-3f) = abs(a - b) < eps

    @Test fun loopsJoinSeamlessly() {
        // Akhir siklus = awal siklus berikutnya → tidak ada loncatan saat jam berulang.
        LogoMood.entries.forEach { mood ->
            val a = Frame.at(0f, mood)
            val b = Frame.at(1f, mood)
            assertTrue(close(a.breath, b.breath) && close(a.tabLift, b.tabLift) && close(a.tabTilt, b.tabTilt), "$mood: $a vs $b")
        }
        // Idle: centang utuh di awal & akhir; Thinking: mulai kosong, berakhir memudar habis (= kosong).
        assertEquals(1f to 1f, Frame.at(0f, LogoMood.Idle).let { it.check to it.checkAlpha })
        assertEquals(0f, Frame.at(0f, LogoMood.Thinking).check)
        assertTrue(close(Frame.at(1f, LogoMood.Thinking).checkAlpha, 0f))
    }

    @Test fun motionIsSmallAndContinuous() {
        LogoMood.entries.forEach { mood ->
            var prev = Frame.at(0f, mood)
            for (i in 1..2000) {
                val f = Frame.at(i / 2000f, mood)
                // Gerak halus: tidak ada lompatan besar antar langkah 0,05%.
                assertTrue(abs(f.breath - prev.breath) < 0.002f, "napas $mood @ $i")
                assertTrue(abs(f.tabTilt - prev.tabTilt) < 0.05f, "tab $mood @ $i")
                assertTrue(abs(f.check - prev.check) < 0.03f || f.checkAlpha == 1f && prev.check == 0f || prev.checkAlpha < 0.01f, "centang $mood @ $i")
                assertTrue(f.breath in 0.95f..1.05f && abs(f.tabTilt) <= 5f && f.tabLift <= 0f)
                prev = f
            }
        }
    }

    @Test fun easeIsMonotonicWithFlatEnds() {
        assertEquals(0f, Frame.ease(0f))
        assertEquals(1f, Frame.ease(1f))
        assertTrue(close(Frame.ease(0.5f), 0.5f))
        var last = -1f
        for (i in 0..100) Frame.ease(i / 100f).also { assertTrue(it >= last); last = it }
        assertTrue(Frame.ease(0.02f) < 0.001f && Frame.ease(0.98f) > 0.999f)
    }
}
