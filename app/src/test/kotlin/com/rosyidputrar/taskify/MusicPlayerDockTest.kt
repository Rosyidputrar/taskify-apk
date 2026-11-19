package com.rosyidputrar.taskify

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.media.DiscoverySection
import com.rosyidputrar.taskify.core.domain.media.MoodCategory
import com.rosyidputrar.taskify.core.domain.media.MoodGroup
import com.rosyidputrar.taskify.core.media.LocalRadioStations
import com.rosyidputrar.taskify.feature.media.ExploreState
import com.rosyidputrar.taskify.feature.media.MusicContent
import com.rosyidputrar.taskify.feature.media.MusicTab
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/**
 * Regresi bug HP 17 Nov 2026 (Agen C): dok mengambang menutupi UI pemutar —
 * teks "Hentikan & kosongkan antrean" setengah tertutup dok, baris
 * Radio/Pewaktu tidur terpotong.
 *
 * Akar masalah: viewport `LazyColumn` pemutar memanjang sampai bawah layar,
 * ke bawah dok. Baris bawah kartu pemutar tergambar setengah di bawah dok
 * (dok mencegat sentuhan), dan `performScrollTo` tidak menggesernya karena
 * scroller menganggap baris itu sudah terlihat.
 *
 * Perbaikan ([MusicContent]): saat pemutar aktif, padding bawah konten
 * dipakai sebagai padding viewport (daftar berakhir di atas dok), bukan
 * sebagai padding gulir akhir daftar. Tes ini membuktikannya dengan batas
 * elemen, bukan dengan mata: setelah digulir ke tampilan, tiap tombol
 * pemutar harus sepenuhnya di atas zona dok (bawah = 112dp seperti yang
 * dicadangkan shell untuk dok), dan dalam keadaan diam tidak ada tombol
 * yang setengah tertutup dok.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class MusicPlayerDockTest {
    @get:Rule val compose = createComposeRule()

    /** Padding seperti aplikasi: bawah = navBottom + 112dp (TaskifyApp). */
    private val padding = PaddingValues(top = 24.dp, bottom = 112.dp)
    private val songs = SampleCross.songs

    private val explore = ExploreState(
        loaded = true,
        moods = listOf(MoodGroup("Mood & momen", listOf("Fokus", "Santai").map { MoodCategory(it, "p-$it") })),
        stations = LocalRadioStations.FALLBACK,
        home = listOf(DiscoverySection.Songs("Pilihan cepat", songs)),
        charts = emptyList(),
    )

    /** Tepi atas zona dok dalam px: 112dp dari bawah layar (ruang cadangan dok). */
    private fun dockTopPx(): Float {
        val rootH = compose.onRoot().fetchSemanticsNode().boundsInRoot.height
        return rootH - with(compose.density) { 112.dp.toPx() }
    }

    /** Batas bawah node dalam px. */
    private fun bottomPx(text: String): Float =
        compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.bottom

    private fun topPx(text: String): Float =
        compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

    private fun setContent(tab: MusicTab) {
        compose.setContent {
            TaskifyTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MusicContent(
                        SampleCross.music(tab).copy(explore = explore),
                        SampleCross.Actions, padding,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    /** Tombol tidak pernah setengah tertutup dok: seluruhnya di atasnya, atau seluruhnya di bawah viewport. */
    private fun assertNeverHalfCovered(text: String) {
        val dockTop = dockTopPx()
        val top = topPx(text)
        val bottom = bottomPx(text)
        assertTrue(
            bottom <= dockTop || top >= dockTop,
            "'$text' setengah tertutup dok: top=$top bottom=$bottom dockTop=$dockTop",
        )
    }

    /** Setelah digulir ke tampilan, tombol harus sepenuhnya di atas zona dok — dan bisa diklik. */
    private fun assertReachableAboveDock(text: String) {
        compose.onNodeWithText(text).performScrollTo()
        compose.waitForIdle()
        val dockTop = dockTopPx()
        val bottom = bottomPx(text)
        assertTrue(bottom <= dockTop, "'$text' masih di bawah dok setelah digulir: bottom=$bottom dockTop=$dockTop")
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    @Test fun stopButtonReachableAboveDock_explore() {
        setContent(MusicTab.Explore)
        assertNeverHalfCovered("Hentikan & kosongkan antrean")
        assertReachableAboveDock("Hentikan & kosongkan antrean")
        // Bisa diklik (tidak dicegat dok): aksinya no-op di tes.
        compose.onNodeWithText("Hentikan & kosongkan antrean").performClick()
    }

    @Test fun stopButtonReachableAboveDock_queueShort() {
        // Antrean pendek = konten di bawah kartu sedikit; dulu scroll maksimal
        // belum tentu cukup mengangkat tombol ke atas dok.
        setContent(MusicTab.Queue)
        assertNeverHalfCovered("Hentikan & kosongkan antrean")
        assertReachableAboveDock("Hentikan & kosongkan antrean")
    }

    @Test fun radioAndSleepRowReachableAboveDock() {
        setContent(MusicTab.Explore)
        assertNeverHalfCovered("Radio")
        assertNeverHalfCovered("Pewaktu tidur")
        assertReachableAboveDock("Radio")
        assertReachableAboveDock("Pewaktu tidur")
    }

    @Test fun playerAboveDockGolden() {
        setContent(MusicTab.Explore)
        compose.onNodeWithText("Hentikan & kosongkan antrean").performScrollTo()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/music_player_above_dock_light.png")
    }
}
