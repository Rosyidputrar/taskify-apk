package com.rosyidputrar.taskify

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.navigation.CalendarRoute
import com.rosyidputrar.taskify.navigation.FinanceRoute
import com.rosyidputrar.taskify.navigation.FocusRoute
import com.rosyidputrar.taskify.navigation.HomeRoute
import com.rosyidputrar.taskify.navigation.JournalEntryRoute
import com.rosyidputrar.taskify.navigation.NoteEditRoute
import com.rosyidputrar.taskify.navigation.RoomRoute
import com.rosyidputrar.taskify.navigation.TasksRoute
import com.rosyidputrar.taskify.ui.shell.isFullScreenEditor
import com.rosyidputrar.taskify.ui.shell.isTopLevel
import com.rosyidputrar.taskify.ui.shell.sharedBounds
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
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
                composable<FocusRoute> { Text("focus") }
            }
        }
    }

    private fun go(route: Any) = compose.runOnIdle { nav.navigate(route) }
    private val current get() = nav.currentBackStackEntry!!

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
        go(TasksRoute("t1"))
        compose.runOnIdle { assertTrue(current.isTopLevel(TaskifyModule.Tasks), "rute berargumen tetap cocok") }
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
