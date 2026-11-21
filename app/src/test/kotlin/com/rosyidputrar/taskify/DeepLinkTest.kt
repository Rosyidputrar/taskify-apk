package com.rosyidputrar.taskify

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.navigation.HomeRoute
import com.rosyidputrar.taskify.navigation.JournalEntryRoute
import com.rosyidputrar.taskify.navigation.NoteEditRoute
import com.rosyidputrar.taskify.navigation.TasksRoute
import com.rosyidputrar.taskify.navigation.parseTaskifyDeepLink
import com.rosyidputrar.taskify.ui.shell.AppEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A5 — deep link `taskify://…`: parser URI (dipakai MainActivity untuk ACTION_VIEW) dan
 * `navDeepLink` di NavHost (pola URI yang sama seperti intent internal taskify://task/…).
 */
@RunWith(AndroidJUnit4::class)
class DeepLinkTest {
    @get:Rule val compose = createComposeRule()
    private val navHolder = mutableStateOf<NavHostController?>(null)
    private val nav get() = navHolder.value!!

    @Test fun parseTaskOpensTask() {
        assertEquals(AppEvent.OpenTask("t1"), parseTaskifyDeepLink(Uri.parse("taskify://task/t1")))
    }

    @Test fun parseNoteOpensNote() {
        assertEquals(AppEvent.OpenNote(id = "n1"), parseTaskifyDeepLink(Uri.parse("taskify://note/n1")))
    }

    @Test fun parseNoteWithDateOpensNewNoteOnDate() {
        assertEquals(AppEvent.OpenNote(date = "2026-11-20"), parseTaskifyDeepLink(Uri.parse("taskify://note?date=2026-11-20")))
    }

    @Test fun parseJournalOpensJournal() {
        assertEquals(AppEvent.OpenJournal(id = "j1"), parseTaskifyDeepLink(Uri.parse("taskify://journal/j1")))
    }

    @Test fun parseJournalWithDateOpensNewEntryOnDate() {
        assertEquals(AppEvent.OpenJournal(date = "2026-11-20"), parseTaskifyDeepLink(Uri.parse("taskify://journal?date=2026-11-20")))
    }

    @Test fun parseUnknownIsIgnored() {
        assertNull(parseTaskifyDeepLink(Uri.parse("taskify://focus/PAUSE")))
        assertNull(parseTaskifyDeepLink(Uri.parse("taskify://bogus/x")))
        assertNull(parseTaskifyDeepLink(Uri.parse("taskify://task/")))
        assertNull(parseTaskifyDeepLink(Uri.parse("taskify://note")))
        assertNull(parseTaskifyDeepLink(Uri.parse("https://example.com/task/t1")))
        assertNull(parseTaskifyDeepLink(Uri.parse("taskify://task")))
        // Parameter tanggal rusak/kosong harus ditolak (mencegah DateTimeParseException).
        assertNull(parseTaskifyDeepLink(Uri.parse("taskify://journal?date=")))
        assertNull(parseTaskifyDeepLink(Uri.parse("taskify://journal?date=bukan-tanggal")))
        assertNull(parseTaskifyDeepLink(Uri.parse("taskify://note?date=2026-99-99")))
    }

    @Test fun eventQueueClearDrainsPendingEvents() {
        val q = com.rosyidputrar.taskify.ui.shell.EventQueue<AppEvent>()
        q.send(AppEvent.OpenTask("old-account-task"))
        q.clear()
        q.send(AppEvent.OpenTask("new-account-task"))
        val first = runBlocking { q.events.first() }
        assertEquals(AppEvent.OpenTask("new-account-task"), first)
    }

    /** NavHost kecil dengan deklarasi deep link yang sama seperti TaskifyApp. */
    private fun host() {
        compose.setContent {
            val controller = rememberNavController().also { navHolder.value = it }
            NavHost(controller, startDestination = HomeRoute, modifier = Modifier.fillMaxSize().testTag("host")) {
                composable<HomeRoute> { Text("home") }
                composable<TasksRoute>(
                    deepLinks = listOf(navDeepLink { uriPattern = "taskify://task/{taskId}" }),
                ) { Text("tasks") }
                composable<JournalEntryRoute>(
                    deepLinks = listOf(
                        navDeepLink { uriPattern = "taskify://journal/{id}" },
                        navDeepLink { uriPattern = "taskify://journal?date={date}" },
                    ),
                ) { Text("journal") }
                composable<NoteEditRoute>(
                    deepLinks = listOf(
                        navDeepLink { uriPattern = "taskify://note/{id}" },
                        navDeepLink { uriPattern = "taskify://note?date={date}" },
                    ),
                ) { Text("note") }
            }
        }
        compose.waitForIdle()
    }

    private fun deepLink(uri: String): Boolean {
        var handled = false
        compose.runOnIdle { handled = nav.handleDeepLink(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
        compose.waitForIdle()
        return handled
    }

    @Test fun navDeepLinkOpensTaskWithId() {
        host()
        assertTrue(deepLink("taskify://task/t1"))
        compose.runOnIdle { assertEquals("t1", nav.currentBackStackEntry!!.toRoute<TasksRoute>().taskId) }
    }

    @Test fun navDeepLinkOpensJournalWithIdAndDate() {
        host()
        assertTrue(deepLink("taskify://journal/j1"))
        compose.runOnIdle { assertEquals("j1", nav.currentBackStackEntry!!.toRoute<JournalEntryRoute>().id) }
        assertTrue(deepLink("taskify://journal?date=2026-11-20"))
        compose.runOnIdle { assertEquals("2026-11-20", nav.currentBackStackEntry!!.toRoute<JournalEntryRoute>().date) }
    }

    @Test fun navDeepLinkOpensNoteWithIdAndDate() {
        host()
        assertTrue(deepLink("taskify://note/n1"))
        compose.runOnIdle { assertEquals("n1", nav.currentBackStackEntry!!.toRoute<NoteEditRoute>().id) }
        assertTrue(deepLink("taskify://note?date=2026-11-20"))
        compose.runOnIdle { assertEquals("2026-11-20", nav.currentBackStackEntry!!.toRoute<NoteEditRoute>().date) }
    }

    @Test fun navDeepLinkIgnoresUnknownHost() {
        host()
        assertFalse(deepLink("taskify://bogus/x"))
        compose.runOnIdle { assertTrue(nav.currentBackStackEntry!!.destination.hasRoute<HomeRoute>()) }
    }
}
