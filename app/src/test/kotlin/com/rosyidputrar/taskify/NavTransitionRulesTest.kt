package com.rosyidputrar.taskify

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.domain.files.EntityType
import com.rosyidputrar.taskify.navigation.CalendarRoute
import com.rosyidputrar.taskify.navigation.FinanceRoute
import com.rosyidputrar.taskify.navigation.FocusRoute
import com.rosyidputrar.taskify.navigation.HomeRoute
import com.rosyidputrar.taskify.navigation.JournalEntryRoute
import com.rosyidputrar.taskify.navigation.ModuleRoute
import com.rosyidputrar.taskify.navigation.NoteEditRoute
import com.rosyidputrar.taskify.navigation.ProjectRoute
import com.rosyidputrar.taskify.navigation.RoomRoute
import com.rosyidputrar.taskify.navigation.SnippetRoute
import com.rosyidputrar.taskify.navigation.TasksRoute
import com.rosyidputrar.taskify.navigation.TransactionRoute
import com.rosyidputrar.taskify.ui.shell.isFullScreenEditor
import com.rosyidputrar.taskify.ui.shell.isTopLevel
import com.rosyidputrar.taskify.ui.shell.moduleForDestination
import com.rosyidputrar.taskify.ui.shell.openEntity
import com.rosyidputrar.taskify.ui.shell.selectedKeyFor
import com.rosyidputrar.taskify.ui.shell.sharedBounds
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Aturan transisi pola Animeru: mana yang top-level, mana editor layar penuh. */
@RunWith(AndroidJUnit4::class)
class NavTransitionRulesTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var nav: NavHostController

    private fun host() {
        compose.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = HomeRoute) {
                composable<HomeRoute> { Text("home") }
                composable<CalendarRoute> { Text("calendar") }
                composable<RoomRoute> { Text("room") }
                composable<TasksRoute> { Text("tasks") }
                composable<FinanceRoute> { Text("finance") }
                composable<JournalEntryRoute> { Text("journal") }
                composable<NoteEditRoute> { Text("note") }
                composable<TransactionRoute> { Text("transaction") }
                composable<SnippetRoute> { Text("snippet") }
                composable<ProjectRoute> { Text("project") }
                composable<ModuleRoute> { Text("module") }
                composable<FocusRoute> { Text("focus") }
            }
        }
    }

    private fun go(route: Any) = compose.runOnIdle { nav.navigate(route) }
    private val current get() = nav.currentBackStackEntry!!

    /** Kunci dok/rail (harus sama dengan KEY_* di TaskifyApp): null = tidak ada yang terpilih. */
    private fun selectedKey(pinned: TaskifyModule? = null): String? =
        selectedKeyFor(current.destination, moduleForDestination(current.destination, current), pinned)

    @Test fun topLevelIncludesPinnedModule() {
        host()
        compose.runOnIdle { assertTrue(current.isTopLevel(null)) } // Hari Ini
        go(CalendarRoute())
        compose.runOnIdle { assertTrue(current.isTopLevel(null)) }
        go(RoomRoute)
        compose.runOnIdle { assertTrue(current.isTopLevel(null)) }
        go(FinanceRoute)
        compose.runOnIdle {
            assertFalse(current.isTopLevel(null), "modul biasa = push")
            assertTrue(current.isTopLevel(TaskifyModule.Finance), "modul yang disematkan di dok = top-level")
            assertFalse(current.isTopLevel(TaskifyModule.Journal))
        }
        // A2: detail Tugas BUKAN top-level walau Tugas disematkan — dulu rute berargumen
        // tetap cocok hasRoute(TasksRoute::class) sehingga detail beranimasi seperti tab.
        go(TasksRoute("t1"))
        compose.runOnIdle { assertFalse(current.isTopLevel(TaskifyModule.Tasks), "detail tugas = push, bukan tab") }
        go(TasksRoute())
        compose.runOnIdle { assertTrue(current.isTopLevel(TaskifyModule.Tasks), "daftar tugas yang disematkan = top-level") }
    }

    /** A1: dok/rail tidak menyorot apa pun saat modul aktif bukan tujuan dok. */
    @Test fun dockSelectionIsNullOutsideDockDestinations() {
        host()
        compose.runOnIdle { assertEquals("home", selectedKey()) }
        go(CalendarRoute())
        compose.runOnIdle { assertEquals("calendar", selectedKey()) }
        go(RoomRoute)
        compose.runOnIdle { assertEquals("room", selectedKey()) }
        go(FinanceRoute)
        compose.runOnIdle { assertNull(selectedKey(), "Keuangan (bukan dok, tidak disematkan) = tidak ada yang terpilih") }
        go(JournalEntryRoute(id = "j"))
        compose.runOnIdle { assertNull(selectedKey(), "editor jurnal = tidak ada yang terpilih") }
        go(TasksRoute("t1"))
        compose.runOnIdle { assertNull(selectedKey(), "detail tugas = tidak ada yang terpilih") }
    }

    /** A1 + A3: modul sematan disorot sebagai "pin", dan Ruang tidak ikut tersorot. */
    @Test fun pinnedModuleSelectsPinKeyNotRoom() {
        host()
        go(FinanceRoute)
        compose.runOnIdle {
            assertEquals("pin", selectedKey(TaskifyModule.Finance), "Keuangan yang disematkan = pin")
            assertNull(selectedKey(TaskifyModule.Journal), "pin orang lain tidak berlaku")
        }
        go(RoomRoute)
        compose.runOnIdle { assertEquals("room", selectedKey(TaskifyModule.Finance), "di Ruang, yang tersorot tetap Ruang") }
        go(ModuleRoute("Finance"))
        compose.runOnIdle { assertEquals("pin", selectedKey(TaskifyModule.Finance), "rute generik modul sematan = pin") }
        // A8: rute generik modul yang sudah tidak ada = tidak ada yang terpilih (bukan crash).
        go(ModuleRoute("TidakAdaLagi"))
        compose.runOnIdle {
            assertNull(moduleForDestination(current.destination, current), "modul basi = null")
            assertNull(selectedKey(TaskifyModule.Finance))
        }
    }

    /**
     * A6: buka entitas yang sama dua kali = satu entri di tumpukan (bukan editor duplikat).
     * Catatan: `currentBackStack` menyertakan graf navigasi itu sendiri sebagai entri
     * pertama (route null), jadi tumpukan yang benar = graf + Beranda + 1 editor = 3.
     */
    @Test fun openEntityTwiceKeepsSingleEntry() {
        host()
        val cases = listOf(
            EntityType.Task to { nav.openEntity(EntityType.Task, "t1") },
            EntityType.JournalEntry to { nav.openEntity(EntityType.JournalEntry, "j1") },
            EntityType.Note to { nav.openEntity(EntityType.Note, "n1") },
            EntityType.Transaction to { nav.openEntity(EntityType.Transaction, "x1") },
            EntityType.Snippet to { nav.openEntity(EntityType.Snippet, "s1") },
            EntityType.CodeProject to { nav.openEntity(EntityType.CodeProject, "p1") },
        )
        for ((type, open) in cases) {
            compose.runOnIdle { open() }
            compose.runOnIdle {
                val entries = nav.currentBackStack.value
                assertEquals(3, entries.size, "$type dibuka sekali = graf + Beranda + 1 editor (ditemukan ${entries.size}: ${entries.map { it.destination.route }})")
            }
            compose.runOnIdle { open() } // ketuk ganda cepat
            compose.runOnIdle {
                val entries = nav.currentBackStack.value
                assertEquals(3, entries.size, "$type dibuka dua kali harus tetap 1 entri (ditemukan ${entries.size}: ${entries.map { it.destination.route }})")
            }
            // Satu back harus langsung keluar dari editor (tidak melewati duplikatnya).
            compose.runOnIdle { assertTrue(nav.popBackStack(), "back dari editor $type") }
            compose.runOnIdle { assertTrue(current.destination.hasRoute<HomeRoute>(), "satu back dari $type harus kembali ke Beranda") }
        }
    }

    @Test fun fullScreenEditorsMatchImmersiveFlag() {
        host()
        go(JournalEntryRoute(id = "j"))
        compose.runOnIdle { assertTrue(current.destination.isFullScreenEditor()) }
        go(NoteEditRoute())
        compose.runOnIdle { assertTrue(current.destination.isFullScreenEditor()) }
        go(FocusRoute())
        compose.runOnIdle { assertTrue(current.destination.isFullScreenEditor()) }
        go(TasksRoute())
        compose.runOnIdle {
            assertFalse(current.destination.isFullScreenEditor())
            assertFalse(current.isTopLevel(null))
        }
    }

    @Test fun sharedBoundsIsNoOpOutsideNavHost() {
        compose.setContent { Box(Modifier.size(40.dp).sharedBounds("task-1")) { Text("kartu") } }
        compose.onNodeWithText("kartu").assertExists()
    }
}
