package com.rosyidputrar.taskify.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.rosyidputrar.taskify.core.designsystem.component.DockDestination
import com.rosyidputrar.taskify.core.designsystem.component.TaskifyDock
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.designsystem.theme.ThemeMode
import com.rosyidputrar.taskify.data.AppPreferences
import com.rosyidputrar.taskify.navigation.CalendarRoute
import com.rosyidputrar.taskify.navigation.HomeRoute
import com.rosyidputrar.taskify.navigation.ModuleRoute
import com.rosyidputrar.taskify.navigation.RoomRoute
import com.rosyidputrar.taskify.navigation.SettingsRoute
import com.rosyidputrar.taskify.navigation.route
import com.rosyidputrar.taskify.ui.capture.CaptureSheet
import com.rosyidputrar.taskify.ui.home.HomeScreen
import com.rosyidputrar.taskify.ui.placeholder.ModulePlaceholderScreen
import com.rosyidputrar.taskify.ui.room.RoomScreen
import com.rosyidputrar.taskify.ui.settings.SettingsScreen
import com.rosyidputrar.taskify.ui.spotlight.Spotlight

private const val KEY_HOME = "home"
private const val KEY_CALENDAR = "calendar"
private const val KEY_ROOM = "room"
private const val KEY_PIN = "pin"

/** Lebar minimum untuk tata letak rail + dua panel (tablet, Chromebook, lanskap lebar). */
private val ExpandedWidth = 840.dp

@Composable
fun TaskifyApp(
    preferences: AppPreferences.Snapshot,
    onThemeModeChange: (ThemeMode) -> Unit,
    onPinnedModuleChange: (TaskifyModule?) -> Unit,
    displayName: String? = null,
) {
    val navController = rememberNavController()
    var spotlightOpen by rememberSaveable { mutableStateOf(false) }
    var captureOpen by rememberSaveable { mutableStateOf(false) }
    var captureText by rememberSaveable { mutableStateOf("") }
    var capturePickType by rememberSaveable { mutableStateOf(false) }

    fun openCapture(text: String = "", pickType: Boolean = false) {
        captureText = text
        capturePickType = pickType
        spotlightOpen = false
        captureOpen = true
    }

    LaunchedEffect(Unit) {
        AppEvents.events.collect { event ->
            when (event) {
                AppEvent.OpenSpotlight -> spotlightOpen = true
                is AppEvent.OpenCapture -> openCapture(event.text)
                AppEvent.OpenCalendar -> navController.navigateTopLevel(CalendarRoute)
            }
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val pinned = preferences.pinnedModule
    val currentModule: TaskifyModule? = when {
        destination == null -> null
        destination.hasRoute<HomeRoute>() -> TaskifyModule.Home
        destination.hasRoute<CalendarRoute>() -> TaskifyModule.Calendar
        destination.hasRoute<SettingsRoute>() -> TaskifyModule.Settings
        destination.hasRoute<ModuleRoute>() -> backStackEntry?.toRoute<ModuleRoute>()?.module
        else -> null
    }
    val selectedKey = when (currentModule) {
        TaskifyModule.Home -> KEY_HOME
        TaskifyModule.Calendar -> KEY_CALENDAR
        null -> KEY_ROOM
        pinned -> KEY_PIN
        else -> KEY_ROOM
    }

    val openModule: (TaskifyModule) -> Unit = { module ->
        spotlightOpen = false
        when (module) {
            TaskifyModule.Home, TaskifyModule.Calendar -> navController.navigateTopLevel(module.route())
            else -> navController.navigate(module.route()) { launchSingleTop = true }
        }
    }

    // Surface root memberi warna konten default (onBackground) ke semua teks di bawahnya.
    Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= ExpandedWidth
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        // Ruang di bawah konten supaya item terakhir tidak tertutup dok mengambang.
        val contentPadding = PaddingValues(
            top = statusTop,
            bottom = navBottom + if (expanded) 24.dp else 112.dp,
        )

        val host: @Composable (Modifier) -> Unit = { modifier ->
            TaskifyNavHost(
                navController = navController,
                contentPadding = contentPadding,
                preferences = preferences,
                displayName = displayName,
                onOpenModule = openModule,
                onCapture = { openCapture() },
                onThemeModeChange = onThemeModeChange,
                onPinnedModuleChange = onPinnedModuleChange,
                modifier = modifier,
            )
        }

        if (expanded) {
            Row(Modifier.fillMaxSize()) {
                TaskifyRail(
                    selectedKey = selectedKey,
                    onCapture = { openCapture() },
                    onSearch = { spotlightOpen = true },
                    onHome = { navController.navigateTopLevel(HomeRoute) },
                    onCalendar = { navController.navigateTopLevel(CalendarRoute) },
                    onRoom = { navController.navigateTopLevel(RoomRoute) },
                )
                host(Modifier.weight(1f))
            }
        } else {
            host(Modifier.fillMaxSize())
            val modules = TaskifyTheme.modules
            val home = DockDestination(KEY_HOME, "Hari Ini", TaskifyModule.Home.icon, modules.accent(TaskifyModule.Home))
            val calendar = DockDestination(KEY_CALENDAR, "Kalender", TaskifyModule.Calendar.icon, modules.accent(TaskifyModule.Calendar))
            val room = DockDestination(KEY_ROOM, "Ruang", Icons.Outlined.SpaceDashboard, MaterialTheme.colorScheme.primary)
            val pin = pinned?.let { DockDestination(KEY_PIN, it.label, it.icon, modules.accent(it)) }
            TaskifyDock(
                leading = listOfNotNull(home, pin),
                trailing = listOf(calendar, room),
                selectedKey = selectedKey,
                onSelect = { item ->
                    when (item.key) {
                        KEY_HOME -> navController.navigateTopLevel(HomeRoute)
                        KEY_CALENDAR -> navController.navigateTopLevel(CalendarRoute)
                        KEY_ROOM -> navController.navigateTopLevel(RoomRoute)
                        KEY_PIN -> pinned?.let { navController.navigateTopLevel(it.route()) }
                    }
                },
                onCapture = { openCapture() },
                onCaptureLongPress = { openCapture(pickType = true) },
                onSwipeUp = { spotlightOpen = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            )
        }

        Spotlight(
            visible = spotlightOpen,
            onDismiss = { spotlightOpen = false },
            onOpenModule = openModule,
            onCapture = { text -> openCapture(text) },
            themeMode = preferences.themeMode,
            onThemeModeChange = onThemeModeChange,
        )
    }
    }

    if (captureOpen) {
        CaptureSheet(
            initialText = captureText,
            startWithTypePicker = capturePickType,
            onDismiss = { captureOpen = false },
        )
    }
}

@Composable
private fun TaskifyNavHost(
    navController: NavHostController,
    contentPadding: PaddingValues,
    preferences: AppPreferences.Snapshot,
    displayName: String?,
    onOpenModule: (TaskifyModule) -> Unit,
    onCapture: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onPinnedModuleChange: (TaskifyModule?) -> Unit,
    modifier: Modifier,
) {
    NavHost(navController = navController, startDestination = HomeRoute, modifier = modifier) {
        composable<HomeRoute> {
            HomeScreen(
                contentPadding = contentPadding,
                displayName = displayName,
                onOpenModule = onOpenModule,
                onCapture = onCapture,
            )
        }
        composable<CalendarRoute> {
            ModulePlaceholderScreen(TaskifyModule.Calendar, contentPadding, onBack = null)
        }
        composable<RoomRoute> {
            RoomScreen(
                contentPadding = contentPadding,
                pinnedModule = preferences.pinnedModule,
                onOpenModule = onOpenModule,
                onPinnedModuleChange = onPinnedModuleChange,
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                contentPadding = contentPadding,
                themeMode = preferences.themeMode,
                pinnedModule = preferences.pinnedModule,
                onThemeModeChange = onThemeModeChange,
                onPinnedModuleChange = onPinnedModuleChange,
                onBack = navController.backIfPossible(),
            )
        }
        composable<ModuleRoute> { entry ->
            val route = entry.toRoute<ModuleRoute>()
            ModulePlaceholderScreen(route.module, contentPadding, onBack = navController.backIfPossible())
        }
    }
}

/** Tombol kembali hanya bila layar dibuka dari layar lain (bukan tujuan dok). */
private fun NavHostController.backIfPossible(): (() -> Unit)? =
    if (previousBackStackEntry != null) ({ popBackStack() }) else null

private fun NavHostController.navigateTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun TaskifyRail(
    selectedKey: String,
    onCapture: () -> Unit,
    onSearch: () -> Unit,
    onHome: () -> Unit,
    onCalendar: () -> Unit,
    onRoom: () -> Unit,
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
        header = {
            FloatingActionButton(onClick = onCapture, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Rounded.Add, contentDescription = "Tangkap cepat")
            }
        },
    ) {
        RailItem("Hari Ini", TaskifyModule.Home.icon, selectedKey == KEY_HOME, onHome)
        RailItem("Kalender", TaskifyModule.Calendar.icon, selectedKey == KEY_CALENDAR, onCalendar)
        RailItem("Ruang", Icons.Outlined.SpaceDashboard, selectedKey == KEY_ROOM || selectedKey == KEY_PIN, onRoom)
        RailItem("Cari  ⌘K", Icons.Outlined.Search, false, onSearch)
    }
}

@Composable
private fun RailItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
    )
}
