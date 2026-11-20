package com.rosyidputrar.taskify

import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.designsystem.component.DockDestination
import com.rosyidputrar.taskify.core.designsystem.component.TaskifyDock
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.designsystem.theme.ThemeMode
import com.rosyidputrar.taskify.data.AppPreferences
import com.rosyidputrar.taskify.ui.home.HomeContent
import com.rosyidputrar.taskify.ui.shell.TaskifyApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A1/A3/A12 — seleksi dok/rail dan ketuk ganda dok di shell asli.
 *
 * Aturan: dok/rail tidak menyorot apa pun saat modul aktif bukan tujuan dok (dulu semua modul
 * non-dok salah menyorot "Ruang"); ketuk ganda pada tab dok tidak boleh menumpuk tujuannya.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class ShellSelectionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun dock(selectedKey: String?) {
        compose.setContent {
            TaskifyTheme {
                TaskifyDock(
                    leading = listOf(DockDestination("home", "Hari Ini", TaskifyModule.Home.icon, MaterialTheme.colorScheme.primary)),
                    trailing = listOf(
                        DockDestination("calendar", "Kalender", TaskifyModule.Calendar.icon, MaterialTheme.colorScheme.primary),
                        DockDestination("room", "Ruang", Icons.Outlined.SpaceDashboard, MaterialTheme.colorScheme.primary),
                    ),
                    selectedKey = selectedKey,
                    onSelect = {},
                    onCapture = {},
                    onCaptureLongPress = {},
                    onPull = {},
                    onPullEnd = {},
                    onOpenSearch = {},
                )
            }
        }
        compose.waitForIdle()
    }

    /** A1: selectedKey null = tidak ada label tujuan yang mengembang. */
    @Test fun dockWithNoSelectionShowsNoLabel() {
        dock(null)
        compose.onNodeWithText("Hari Ini").assertDoesNotExist()
        compose.onNodeWithText("Kalender").assertDoesNotExist()
        compose.onNodeWithText("Ruang").assertDoesNotExist()
    }

    /** Kontrol: selectedKey "room" = hanya label Ruang yang tampil. */
    @Test fun dockWithRoomSelectedShowsOnlyRoomLabel() {
        dock("room")
        compose.onNodeWithText("Ruang").assertIsDisplayed()
        compose.onNodeWithText("Hari Ini").assertDoesNotExist()
        compose.onNodeWithText("Kalender").assertDoesNotExist()
    }

    private fun setApp() {
        compose.setContent {
            TaskifyTheme {
                TaskifyApp(
                    preferences = AppPreferences.Snapshot(ThemeMode.Light, null),
                    onThemeModeChange = {},
                    onPinnedModuleChange = {},
                    displayName = "Rosyid",
                    home = { padding, name, openModule, openTask ->
                        HomeContent(SampleData.homeState(), name, padding, openModule, openTask, {}, {})
                    },
                )
            }
        }
        compose.waitForIdle()
    }

    /**
     * A12 — ketuk ganda tab dok "Ruang": satu back harus langsung kembali ke Beranda
     * (bila Ruang tertumpuk dua kali, satu back masih menampilkan Ruang).
     */
    @Test fun doubleTapDockRoomThenOneBackReturnsHome() {
        setApp()
        // Ketukan pertama: ikon dok (belum terpilih → contentDescription = label).
        compose.onNodeWithContentDescription("Ruang").performClick()
        compose.waitForIdle()
        // "Ruang" ada dua: judul layar Ruang + label tab dok yang terpilih — sasar tab doknya.
        compose.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab).and(hasText("Ruang")),
        ).assertIsDisplayed()
        // Ketukan kedua: tab dok yang sudah terpilih (labelnya yang tampil, bukan ikon).
        compose.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab).and(hasText("Ruang")),
        ).performClick()
        compose.waitForIdle()
        // Satu back harus sampai Beranda, bukan "Ruang kedua".
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        // Label "Hari Ini" hanya dirender bila tab itu terpilih (A1), jadi ini
        // membuktikan kita kembali ke Beranda; label "Ruang" harus sudah hilang.
        // ("Aktivitas hari ini" tidak dipakai di sini: judul seksi itu ada di bawah
        // lipatan layar Beranda sehingga assertIsDisplayed gagal walau sudah di Beranda.)
        compose.onNodeWithText("Hari Ini").assertIsDisplayed()
        compose.onNodeWithText("Ruang").assertDoesNotExist()
    }
}
