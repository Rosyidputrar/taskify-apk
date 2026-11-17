package com.rosyidputrar.taskify

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.device.UsageSettings
import com.rosyidputrar.taskify.core.domain.device.DeviceDay
import com.rosyidputrar.taskify.core.domain.device.UsageMath
import com.rosyidputrar.taskify.core.domain.device.UsageSession
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.store.device.AppDayPoint
import com.rosyidputrar.taskify.core.store.device.AppUsage
import com.rosyidputrar.taskify.feature.wellbeing.AppDetail
import com.rosyidputrar.taskify.feature.wellbeing.WbRange
import com.rosyidputrar.taskify.feature.wellbeing.WellbeingActions
import com.rosyidputrar.taskify.feature.wellbeing.WellbeingContent
import com.rosyidputrar.taskify.feature.wellbeing.WellbeingUiState
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Modul Kesehatan Digital: waktu layar, buka kunci, pertama dipegang, sepanjang hari, minggu, detail aplikasi. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class WellbeingScreenTest {
    @get:Rule val compose = createComposeRule()
    private val padding = PaddingValues(top = 24.dp, bottom = 24.dp)
    private val today = "2026-11-16"
    private val m = 60_000L
    private fun at(day: String, h: Int, min: Int = 0) = UsageMath.window(day).first + h * UsageMath.HOUR + min * m

    private val apps = listOf("com.zhiliaoapp.musically" to "TikTok", "com.whatsapp" to "WhatsApp", "com.android.chrome" to "Chrome", "com.instagram.android" to "Instagram", "com.spotify.music" to "Spotify")

    /** 14 hari data; hari ini sampai 21.30. */
    private fun state(range: WbRange = WbRange.Day, excluded: Set<String> = emptySet()): WellbeingUiState {
        val days = (13 downTo 0).map { Dates.addDays(today, -it.toLong()) }
        val appDays = days.associateWith { d ->
            val k = Dates.diffDays(d, today).toInt() + 1 // 1..14
            apps.mapIndexed { i, (pkg, label) -> AppUsage(pkg, label, ((5 - i) * 18 + (k * 7 % 23)) * m, 4 + i * 3, 20 * m) }
        }
        val history = days.map { d ->
            val k = Dates.diffDays(d, today).toInt()
            DeviceDay(d, appDays[d]!!.sumOf { it.foregroundMs }, 70 + k * 2, at(d, 6, 20 + k % 4 * 7))
        }
        val sessions = listOf(
            UsageSession("com.whatsapp", at(today, 6, 45), at(today, 7, 10)),
            UsageSession("com.android.chrome", at(today, 9, 0), at(today, 9, 40)),
            UsageSession("com.zhiliaoapp.musically", at(today, 12, 10), at(today, 13, 5)),
            UsageSession("com.instagram.android", at(today, 17, 30), at(today, 18, 0)),
            UsageSession("com.zhiliaoapp.musically", at(today, 20, 30), at(today, 21, 30)),
            UsageSession("com.spotify.music", at(today, 21, 0), at(today, 21, 20)),
        )
        return WellbeingUiState(
            today = today,
            day = today,
            range = range,
            access = true,
            settings = UsageSettings(excluded = excluded),
            history = history,
            appDays = appDays,
            sessions = sessions,
            notifTimes = listOf(at(today, 7, 2), at(today, 9, 15), at(today, 12, 30), at(today, 12, 31), at(today, 20, 50)),
            notifTotals = days.associateWith { 120 + it.takeLast(1).toInt() },
            days = days.reversed(),
            loaded = true,
            now = at(today, 21, 30),
        )
    }

    private fun show(dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { TaskifyTheme(darkTheme = dark) { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
    }

    private val verticalList = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    @Test fun derivedNumbers() {
        val s = state()
        assertEquals(s.appsOn(today).sumOf { it.foregroundMs }, s.screenMs)
        assertEquals("com.zhiliaoapp.musically", s.apps.first().pkg)
        // Rata-rata 7 hari sebelumnya & "biasanya" (median jam pertama dipegang) tersedia.
        assertEquals((1..7).map { s.screenOn(Dates.addDays(today, -it.toLong())) }.sum() / 7, s.average7)
        assertEquals(6 * 60L + 34, s.usualPickupMinute)
        // Jeda buka kunci: dari pertama dipegang (06.20) sampai sekarang (21.30) dibagi 70×.
        assertEquals((at(today, 21, 30) - at(today, 6, 20)) / 70, s.unlockInterval)
        // Aplikasi yang dikecualikan tidak dihitung.
        val ex = state(excluded = setOf("com.zhiliaoapp.musically"))
        assertEquals(s.screenMs - s.apps.first().foregroundMs, ex.screenMs)
        assertEquals("com.whatsapp", ex.apps.first().pkg)
        // Minggu: 7 hari yang berakhir di hari terpilih.
        val w = state(range = WbRange.Week)
        assertEquals(7, w.weekDays.size)
        assertEquals((0..6).sumOf { w.screenOn(Dates.addDays(today, -it.toLong())) }, w.screenMs)
        assertNull(WellbeingUiState(today = today).average7)
    }

    @Test fun onboarding() {
        show { WellbeingContent(state().copy(access = false), WellbeingActions.None, padding) }
        compose.onNodeWithText("Buka Akses penggunaan").assertExists()
        compose.onRoot().captureRoboImage("screenshots/wellbeing_onboarding_light.png")
    }

    @Test fun dayScreenshot() {
        val opened = mutableListOf<String?>()
        show(dark = true) { WellbeingContent(state(), WellbeingActions.None, padding, onOpenNotifications = { opened += it }) }
        compose.onNodeWithText("WAKTU LAYAR HARI INI").assertExists()
        compose.onNodeWithText("70×").assertExists()
        compose.onNodeWithText("06.20").assertExists()
        compose.onRoot().captureRoboImage("screenshots/wellbeing_day_dark.png")
        compose.onNodeWithText("Notifikasi").performClick()
        assertEquals(listOf<String?>(null), opened)
        compose.onNode(verticalList).performScrollToNode(hasText("SEPANJANG HARI"))
        compose.onNodeWithContentDescription("Pemakaian sepanjang hari", substring = true).assertExists()
    }

    @Test fun weekScreenshotAndTapToDay() {
        var s by mutableStateOf(state(range = WbRange.Week))
        val actions = object : WellbeingActions {
            override fun setRange(range: WbRange) { s = s.copy(range = range) }
            override fun selectDay(day: String) { s = s.copy(day = day) }
        }
        show { WellbeingContent(s, actions, padding) }
        compose.onNodeWithText("rata-rata per hari", substring = true).assertExists()
        compose.onRoot().captureRoboImage("screenshots/wellbeing_week_light.png")
        compose.onNodeWithText("Hari").performClick()
        assertEquals(WbRange.Day, s.range)
    }

    @Test fun dayStartChangeAsksBeforeWipingHistory() {
        val applied = mutableListOf<Int>()
        val actions = object : WellbeingActions {
            override fun setDayStart(hour: Int) { applied += hour }
        }
        show { WellbeingContent(state(), actions, padding) }
        compose.onNodeWithContentDescription("Pengaturan Kesehatan Digital").performClick()
        // Ketuk jam lain (saat ini 00.00) → setDayStart TIDAK langsung dipanggil.
        compose.onNodeWithText("04.00").performClick()
        assertEquals(emptyList<Int>(), applied)
        // Dialog konfirmasi menjelaskan riwayat >7 hari akan hilang.
        compose.onNodeWithText("Ubah jam pergantian hari?").assertExists()
        compose.onNodeWithText("Riwayat lebih dari 7 hari terakhir akan hilang permanen", substring = true).assertExists()
        // Batal → tidak ada yang diterapkan.
        compose.onNodeWithText("Batal").performClick()
        assertEquals(emptyList<Int>(), applied)
        compose.onNodeWithText("Ubah jam pergantian hari?").assertDoesNotExist()
        // Ketuk lagi lalu konfirmasi → baru diterapkan.
        compose.onNodeWithText("04.00").performClick()
        compose.onNodeWithText("Ubah jam").performClick()
        assertEquals(listOf(4), applied)
    }

    @Test fun appDetailSheet() {
        val s = state()
        val detail = AppDetail(
            "com.zhiliaoapp.musically", "TikTok", today,
            history = (29 downTo 0).map { AppDayPoint(Dates.addDays(today, -it.toLong()), (60 + it * 3 % 40) * m, 8) },
            sessions = s.sessions.filter { it.pkg == "com.zhiliaoapp.musically" },
            notifications = 12,
        )
        show { WellbeingContent(s, WellbeingActions.None, padding, detail = { _, _, _ -> flowOf(detail) }) }
        val tikTokRow = SemanticsMatcher("baris TikTok") { n ->
            n.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.OnClick)?.label == "Detail TikTok"
        }
        compose.onNode(verticalList).performScrollToNode(tikTokRow)
        compose.onNode(tikTokRow).performClick()
        compose.onNodeWithText("Sesi terlama").assertExists()
        compose.onNodeWithText("12 notifikasi hari ini").assertExists()
        compose.onRoot().captureRoboImage("screenshots/wellbeing_app_detail_light.png")
    }
}
