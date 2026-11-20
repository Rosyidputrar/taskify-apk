package com.rosyidputrar.taskify

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.home.HomeLayout
import com.rosyidputrar.taskify.core.domain.home.HomeSection
import com.rosyidputrar.taskify.core.domain.home.HomeShortcut
import com.rosyidputrar.taskify.core.domain.home.HomeShortcuts
import com.rosyidputrar.taskify.core.domain.home.QuickView
import com.rosyidputrar.taskify.core.domain.home.ShortcutRef
import com.rosyidputrar.taskify.core.domain.home.ShortcutType
import com.rosyidputrar.taskify.navigation.FinanceRoute
import com.rosyidputrar.taskify.navigation.JournalEntryRoute
import com.rosyidputrar.taskify.navigation.SandiRoute
import com.rosyidputrar.taskify.navigation.TasksRoute
import com.rosyidputrar.taskify.navigation.quickViewRoute
import com.rosyidputrar.taskify.navigation.shortcutRoute
import com.rosyidputrar.taskify.ui.home.HomeContent
import com.rosyidputrar.taskify.ui.home.HomeLayoutEditor
import com.rosyidputrar.taskify.ui.home.HomeShortcutActions
import com.rosyidputrar.taskify.ui.home.ShortcutPicker
import com.rosyidputrar.taskify.ui.home.ShortcutPickerUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Pintasan Hari ini: baris kartu, tekan lama, lembar Tambah, Susun Beranda, ubin kontekstual, rute. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class HomeShortcutsTest {
    @get:Rule val compose = createComposeRule()
    private val padding = PaddingValues(top = 24.dp, bottom = 24.dp)
    private val now = HomeShortcuts.parseIsoMs("2026-10-07T12:00:00Z")!!

    private class Recorder : HomeShortcutActions {
        val calls = mutableListOf<String>()
        override fun open(ref: ShortcutRef) { calls += "open:${ref.key}" }
        override fun togglePin(shortcut: HomeShortcut) { calls += "pin:${shortcut.ref.key}" }
        override fun move(ref: ShortcutRef, by: Int) { calls += "move:${ref.key}:$by" }
        override fun removeUnavailable() { calls += "clean" }
        override fun setPickerQuery(query: String) { calls += "q:$query" }
    }

    private fun show(dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { TaskifyTheme(darkTheme = dark) { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
    }

    /** Pintasan di urutan atas supaya terlihat di tangkapan layar tanpa menggulir. */
    private val shortcutsFirst = HomeLayout.Default.moveSection(HomeSection.Shortcuts, -2)

    private fun recentWithTimes() = SampleData.homeRecent.mapIndexed { i, sc ->
        sc.copy(updatedAt = if (i == 0) "2026-10-07T10:00:00Z" else "2026-10-06T20:00:00Z")
    }

    @Test fun rowShowsPinnedThenRecentAndOpens() {
        val actions = Recorder()
        val state = SampleData.homeState().copy(layout = shortcutsFirst, recent = recentWithTimes())
        show { HomeContent(state, "Rosyid", padding, {}, {}, {}, {}, shortcutActions = actions, nowMs = now) }
        compose.onNodeWithText("Pintasan").assertExists()
        compose.onNodeWithContentDescription("Tugas terlambat. Lewat tenggat").performClick()
        compose.onNodeWithContentDescription("Ide fitur Taskify. Catatan · 5 Okt 2026").assertExists()
        assertEquals(listOf("open:view:TasksOverdue"), actions.calls)
        compose.onRoot().captureRoboImage("screenshots/home_shortcuts_light.png")
    }

    @Test fun recentCardsShowRelativeTimeAndCanBePinned() {
        val actions = Recorder()
        // Satu sematan saja → kartu "Lanjutkan" pertama terlihat tanpa menggeser baris.
        val state = SampleData.homeState().copy(layout = shortcutsFirst, pinned = SampleData.homePinned.take(1), recent = recentWithTimes())
        show { HomeContent(state, "Rosyid", padding, {}, {}, {}, {}, shortcutActions = actions, nowMs = now) }
        compose.onNodeWithContentDescription("Retry dengan backoff. Lanjutkan · 2 jam lalu").assertExists()
        // Kartu "Lanjutkan" bisa disematkan; tidak bisa digeser (urutannya otomatis).
        compose.onNodeWithContentDescription("Retry dengan backoff", substring = true).performTouchInput { longClick() }
        compose.onNodeWithText("Geser ke kiri").assertDoesNotExist()
        compose.onNodeWithText("Sematkan ke Hari ini").performClick()
        assertEquals(listOf("pin:snippet:s-retry"), actions.calls)
    }

    @Test fun shortcutsDark() {
        val state = SampleData.homeState().copy(layout = shortcutsFirst, recent = recentWithTimes())
        show(dark = true) { HomeContent(state, "Rosyid", padding, {}, {}, {}, {}, nowMs = now) }
        compose.onRoot().captureRoboImage("screenshots/home_shortcuts_dark.png")
    }

    @Test fun longPressMenuPinsUnpinsAndMoves() {
        val actions = Recorder()
        show { HomeContent(SampleData.homeState().copy(layout = shortcutsFirst), "Rosyid", padding, {}, {}, {}, {}, shortcutActions = actions, nowMs = now) }
        compose.onNodeWithContentDescription("Ide fitur Taskify", substring = true).performTouchInput { longClick() }
        compose.onNodeWithText("Geser ke kiri").assertExists()
        compose.onNodeWithText("Geser ke kanan").performClick()
        compose.onNodeWithContentDescription("Ide fitur Taskify", substring = true).performTouchInput { longClick() }
        compose.onNodeWithText("Lepas dari Hari ini").performClick()
        assertEquals(listOf("move:note:n-ide:1", "pin:note:n-ide"), actions.calls)
    }

    @Test fun emptyStateInvitesToAdd() {
        val state = SampleData.homeState().copy(layout = shortcutsFirst.toggleRecent(), pinned = emptyList())
        var adding = false
        show { HomeContent(state, "Rosyid", padding, {}, {}, {}, {}, nowMs = now) }
        compose.onNodeWithText("Sematkan catatan, snippet, dompet", substring = true).assertExists()
        compose.onNodeWithText("Tambah pintasan").performClick()
        // Lembar pemilih terbuka dengan tampilan cepat.
        compose.onNodeWithText("TAMPILAN CEPAT").assertExists()
        adding = true
        assert(adding)
    }

    @Test fun pickerListsQuickViewsAndTogglesPins() {
        val actions = Recorder()
        val state = SampleData.homeState().copy(
            picker = ShortcutPickerUi(collections = listOf(HomeShortcut(ShortcutRef(ShortcutType.TaskList, "l1"), "Kerja"))),
        )
        show(dark = true) { ShortcutPicker(state, actions, onDone = {}) }
        compose.onNodeWithText("3/${HomeShortcuts.MAX_PINNED}").assertExists()
        compose.onRoot().captureRoboImage("screenshots/home_shortcut_picker_dark.png")
        compose.onNodeWithText("Tugas terlambat").performClick() // sudah tersemat → lepas
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Kerja"))
        compose.onNodeWithText("Kerja").performClick()
        assertEquals(listOf("pin:view:TasksOverdue", "pin:task_list:l1"), actions.calls)
    }

    @Test fun pickerSearchShowsResultsAndFiltersViews() {
        val state = SampleData.homeState().copy(
            picker = ShortcutPickerUi(
                query = "sandi",
                results = listOf(HomeShortcut(ShortcutRef(ShortcutType.Note, "n9"), "Catatan sandi wifi kantor", "2026-09-01")),
            ),
        )
        show { ShortcutPicker(state, Recorder(), onDone = {}) }
        compose.onNodeWithText("Kode 2FA").assertDoesNotExist() // tidak cocok "sandi" di judul? → cek petunjuk
        compose.onNodeWithText("Buat kata sandi").assertExists()
        compose.onNodeWithText("HASIL PENCARIAN").assertExists()
        compose.onNodeWithText("Catatan sandi wifi kantor").assertExists()
    }

    @Test fun arrangeSheetManagesPins() {
        val actions = Recorder()
        val gone = HomeShortcut(ShortcutRef(ShortcutType.Snippet, "x"), "Snippet lama", pinned = true, available = false)
        var layout by mutableStateOf(HomeLayout.Default)
        show { HomeLayoutEditor(layout, { layout = it }, {}, pinned = SampleData.homePinned + gone, shortcutActions = actions, onAddShortcut = {}) }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Lanjutkan otomatis"))
        compose.onNodeWithText("Lanjutkan otomatis").performClick()
        assertFalse(layout.showRecent)
        compose.onNodeWithContentDescription("Naikkan Tugas terlambat").assertExists()
        compose.onNodeWithContentDescription("Turunkan Tugas terlambat").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Sudah dihapus atau di Sampah"))
        compose.onNodeWithContentDescription("Lepas BCA").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Bersihkan yang sudah dihapus"))
        compose.onNodeWithText("Bersihkan yang sudah dihapus").performClick()
        assertEquals(listOf("move:view:TasksOverdue:1", "pin:wallet:w-bca", "clean"), actions.calls)
    }

    @Test fun arrangeScreenshot() {
        show(dark = true) { HomeLayoutEditor(HomeLayout.Default, {}, {}, pinned = SampleData.homePinned, onAddShortcut = {}) }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("PINTASAN · 3/${HomeShortcuts.MAX_PINNED}"))
        compose.onRoot().captureRoboImage("screenshots/home_arrange_shortcuts_dark.png")
    }

    @Test fun summaryTilesOpenTheRelevantView() {
        val actions = Recorder()
        // Sample: 1 tugas terlambat, jurnal sudah ditulis hari ini.
        show { HomeContent(SampleData.homeState(), "Rosyid", padding, {}, {}, {}, {}, shortcutActions = actions, nowMs = now) }
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Tugas hari ini"))
        compose.onNodeWithText("Tugas hari ini").performClick()
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Keluar hari ini"))
        compose.onNodeWithText("Keluar hari ini").performClick()
        assertEquals(listOf("open:view:TasksOverdue", "open:view:FinanceTransactions"), actions.calls)
    }

    @Test fun everyShortcutHasARoute() {
        val today = "2026-10-07"
        QuickView.entries.forEach { assertNotNull(it.name, quickViewRoute(it, today)) }
        ShortcutType.entries.filter { it != ShortcutType.View }.forEach { assertNotNull(it.name, shortcutRoute(ShortcutRef(it, "id1"), today)) }
        assertEquals(TasksRoute(view = "Overdue"), shortcutRoute(ShortcutRef.view(QuickView.TasksOverdue), today))
        assertEquals(TasksRoute(view = "list:l1"), shortcutRoute(ShortcutRef(ShortcutType.TaskList, "l1"), today))
        assertEquals(FinanceRoute(tab = "Transactions", walletId = "w1"), shortcutRoute(ShortcutRef(ShortcutType.Wallet, "w1"), today))
        assertEquals(SandiRoute(tab = "Codes"), shortcutRoute(ShortcutRef.view(QuickView.SandiCodes), today))
        assertEquals(JournalEntryRoute(date = today), shortcutRoute(ShortcutRef.view(QuickView.JournalToday), today))
        // Nama tab di rute harus nama enum yang benar-benar ada di modulnya.
        val tabs = mapOf(
            "Transactions" to com.rosyidputrar.taskify.feature.finance.FinanceTab.entries.map { it.name },
            "Codes" to com.rosyidputrar.taskify.feature.sandi.SandiTab.entries.map { it.name },
        )
        tabs.forEach { (tab, all) -> assert(tab in all) { "$tab tidak ada di $all" } }
        listOf("Budget", "Debt").forEach { assert(it in com.rosyidputrar.taskify.feature.finance.FinanceTab.entries.map { t -> t.name }) }
        listOf("Generator").forEach { assert(it in com.rosyidputrar.taskify.feature.sandi.SandiTab.entries.map { t -> t.name }) }
        listOf("Config", "Projects").forEach { assert(it in com.rosyidputrar.taskify.feature.vault.VaultTab.entries.map { t -> t.name }) }
        assert("Insight" in com.rosyidputrar.taskify.feature.journal.JournalTab.entries.map { it.name })
        listOf("Today", "Overdue", "Upcoming").forEach { assert(it in com.rosyidputrar.taskify.core.store.tasks.SmartView.entries.map { v -> v.name }) }
    }
}
