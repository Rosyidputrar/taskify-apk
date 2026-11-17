package com.rosyidputrar.taskify.ui.shell

import androidx.compose.animation.core.animateDpAsState
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyMotion
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavDestination
import androidx.navigation.NavBackStackEntry
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.scaleOut
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.AnimatedContentTransitionScope
import com.rosyidputrar.taskify.feature.wellbeing.WellbeingScreen
import com.rosyidputrar.taskify.feature.notifications.NotificationDraftsBanner
import com.rosyidputrar.taskify.feature.notifications.NotificationsScreen
import com.rosyidputrar.taskify.navigation.WellbeingRoute
import com.rosyidputrar.taskify.navigation.NotificationsRoute
import com.rosyidputrar.taskify.ui.focus.FocusScreen
import com.rosyidputrar.taskify.navigation.FocusRoute
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import com.rosyidputrar.taskify.core.designsystem.component.rememberHaptics
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.rosyidputrar.taskify.core.designsystem.component.launchSafe
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
import com.rosyidputrar.taskify.core.designsystem.component.UiMessage
import com.rosyidputrar.taskify.feature.tasks.TasksScreen
import com.rosyidputrar.taskify.feature.calendar.CalendarScreen
import com.rosyidputrar.taskify.navigation.TasksRoute
import com.rosyidputrar.taskify.navigation.JournalEntryRoute
import com.rosyidputrar.taskify.navigation.JournalRoute
import com.rosyidputrar.taskify.navigation.NoteEditRoute
import com.rosyidputrar.taskify.navigation.NotesRoute
import com.rosyidputrar.taskify.navigation.FinanceRoute
import com.rosyidputrar.taskify.navigation.TransactionRoute
import com.rosyidputrar.taskify.navigation.VaultRoute
import com.rosyidputrar.taskify.navigation.SnippetRoute
import com.rosyidputrar.taskify.navigation.ProjectRoute
import com.rosyidputrar.taskify.navigation.ZipRoute
import com.rosyidputrar.taskify.navigation.ConfigFileRoute
import com.rosyidputrar.taskify.navigation.ConfigEditRoute
import com.rosyidputrar.taskify.navigation.SandiRoute
import com.rosyidputrar.taskify.core.domain.files.EntityType
import com.rosyidputrar.taskify.navigation.FilesRoute
import com.rosyidputrar.taskify.navigation.ReviewRoute
import com.rosyidputrar.taskify.navigation.TagsRoute
import com.rosyidputrar.taskify.navigation.TagRoute
import com.rosyidputrar.taskify.navigation.TrashRoute
import com.rosyidputrar.taskify.navigation.ShareRoute
import com.rosyidputrar.taskify.navigation.MusicRoute
import com.rosyidputrar.taskify.navigation.TikTokRoute
import com.rosyidputrar.taskify.feature.media.MiniPlayer
import com.rosyidputrar.taskify.feature.media.MusicScreen
import com.rosyidputrar.taskify.feature.media.TikTokScreen
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.widthIn
import kotlinx.coroutines.flow.map
import com.rosyidputrar.taskify.ui.share.ShareScreen
import com.rosyidputrar.taskify.feature.organize.TagScreen
import com.rosyidputrar.taskify.feature.organize.TagsScreen
import com.rosyidputrar.taskify.feature.organize.TrashScreen
import com.rosyidputrar.taskify.feature.review.ReviewNav
import com.rosyidputrar.taskify.feature.review.ReviewScreen
import com.rosyidputrar.taskify.core.domain.review.ReviewPeriod
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.feature.files.FilesScreen
import com.rosyidputrar.taskify.feature.sandi.SandiScreen
import com.rosyidputrar.taskify.feature.vault.config.ConfigEditorScreen
import com.rosyidputrar.taskify.feature.vault.config.ConfigFileScreen
import com.rosyidputrar.taskify.feature.vault.VaultScreen
import com.rosyidputrar.taskify.feature.vault.SnippetEditorScreen
import com.rosyidputrar.taskify.feature.vault.ProjectScreen
import com.rosyidputrar.taskify.feature.vault.ZipViewerScreen
import com.rosyidputrar.taskify.feature.finance.FinanceScreen
import com.rosyidputrar.taskify.feature.finance.TxEditorArgs
import com.rosyidputrar.taskify.feature.finance.TxEditorScreen
import com.rosyidputrar.taskify.core.domain.journal.ReflectionKind
import com.rosyidputrar.taskify.feature.journal.JournalEditorArgs
import com.rosyidputrar.taskify.feature.journal.JournalEditorScreen
import com.rosyidputrar.taskify.feature.journal.JournalScreen
import com.rosyidputrar.taskify.feature.notes.NoteEditorArgs
import com.rosyidputrar.taskify.feature.notes.NoteEditorScreen
import com.rosyidputrar.taskify.feature.notes.NotesScreen
import com.rosyidputrar.taskify.ui.placeholder.BackButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.PaddingValues as Padding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import com.rosyidputrar.taskify.ui.placeholder.ModulePlaceholderScreen
import com.rosyidputrar.taskify.ui.room.RoomScreen
import com.rosyidputrar.taskify.ui.settings.SettingsScreen
import com.rosyidputrar.taskify.ui.spotlight.Spotlight

/** Slot layar Beranda (bisa diganti di tes tangkapan layar yang tidak memakai database). */
typealias HomeSlot = @Composable (Padding, String?, (TaskifyModule) -> Unit, (String) -> Unit) -> Unit

private const val KEY_HOME = "home"
private const val KEY_CALENDAR = "calendar"
private const val KEY_ROOM = "room"
private const val KEY_PIN = "pin"

/** Lebar minimum untuk tata letak rail + dua panel (tablet, Chromebook, lanskap lebar). */
private val ExpandedWidth = 840.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskifyApp(
    preferences: AppPreferences.Snapshot,
    onThemeModeChange: (ThemeMode) -> Unit,
    onPinnedModuleChange: (TaskifyModule?) -> Unit,
    onHapticsChange: (Boolean) -> Unit = {},
    onThemeChange: (com.rosyidputrar.taskify.core.designsystem.theme.ThemeSpec) -> Unit = {},
    displayName: String? = null,
    messages: Flow<UiMessage> = emptyFlow(),
    home: HomeSlot = { padding, name, openModule, openTask -> HomeScreen(padding, name, openModule, openTask) },
) {
    val navController = rememberNavController()
    val spotlight = com.rosyidputrar.taskify.ui.spotlight.rememberSpotlightSheetState()
    val haptics = rememberHaptics()
    var captureOpen by rememberSaveable { mutableStateOf(false) }
    var captureText by rememberSaveable { mutableStateOf("") }
    var capturePickType by rememberSaveable { mutableStateOf(false) }

    fun openCapture(text: String = "", pickType: Boolean = false) {
        captureText = text
        capturePickType = pickType
        spotlight.close()
        captureOpen = true
    }

    fun openEntity(type: EntityType, id: String) {
        spotlight.close()
        when (type) {
            EntityType.Task -> navController.navigate(TasksRoute(id)) { launchSingleTop = true }
            EntityType.JournalEntry -> navController.navigate(JournalEntryRoute(id = id))
            EntityType.Note -> navController.navigate(NoteEditRoute(id = id))
            EntityType.Transaction -> navController.navigate(TransactionRoute(id = id))
            EntityType.Snippet -> navController.navigate(SnippetRoute(id))
            EntityType.CodeProject -> navController.navigate(ProjectRoute(id))
        }
    }

    // Dibagikan saat gerbang (login/PIN) masih tertutup: event-nya terlewat, isinya tetap di kotak masuk.
    LaunchedEffect(Unit) {
        val pending = runCatching { org.koin.core.context.GlobalContext.getOrNull()?.getOrNull<com.rosyidputrar.taskify.ui.share.ShareInbox>() }.getOrNull()
        if (pending?.content?.value != null) navController.navigate(ShareRoute) { launchSingleTop = true }
    }

    LaunchedEffect(Unit) {
        AppEvents.events.collect { event ->
            when (event) {
                is AppEvent.OpenEntity -> openEntity(event.type, event.id)
                is AppEvent.OpenModule -> { spotlight.close(); navController.navigate(event.module.route()) { launchSingleTop = true } }
                AppEvent.OpenShare -> { spotlight.close(); navController.navigate(ShareRoute) { launchSingleTop = true } }
                is AppEvent.OpenFocus -> { spotlight.close(); navController.navigate(FocusRoute(event.taskId)) { launchSingleTop = true } }
                AppEvent.OpenSpotlight -> spotlight.open()
                is AppEvent.OpenCapture -> openCapture(event.text)
                is AppEvent.OpenCalendar -> {
                    spotlight.close()
                    navController.navigateTopLevel(CalendarRoute(event.date), restore = event.date == null)
                }
                is AppEvent.OpenTask -> {
                    spotlight.close()
                    navController.navigate(TasksRoute(event.taskId)) { launchSingleTop = true }
                }
                is AppEvent.OpenJournal -> {
                    spotlight.close()
                    navController.navigate(JournalEntryRoute(event.id, event.date))
                }
                is AppEvent.OpenNote -> {
                    spotlight.close()
                    navController.navigate(NoteEditRoute(event.id, event.date))
                }
                is AppEvent.OpenTransaction -> {
                    spotlight.close()
                    navController.navigate(TransactionRoute(event.id, event.date))
                }
            }
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val pinned = preferences.pinnedModule
    val shortcutContext = androidx.compose.ui.platform.LocalContext.current
    val currentModule: TaskifyModule? = when {
        destination == null -> null
        destination.hasRoute<HomeRoute>() -> TaskifyModule.Home
        destination.hasRoute<CalendarRoute>() -> TaskifyModule.Calendar
        destination.hasRoute<SettingsRoute>() -> TaskifyModule.Settings
        destination.hasRoute<TasksRoute>() -> TaskifyModule.Tasks
        destination.hasRoute<JournalRoute>() || destination.hasRoute<JournalEntryRoute>() -> TaskifyModule.Journal
        destination.hasRoute<NotesRoute>() || destination.hasRoute<NoteEditRoute>() -> TaskifyModule.Notes
        destination.hasRoute<FinanceRoute>() || destination.hasRoute<TransactionRoute>() -> TaskifyModule.Finance
        destination.hasRoute<VaultRoute>() || destination.hasRoute<SnippetRoute>() || destination.hasRoute<ProjectRoute>() || destination.hasRoute<ZipRoute>() ||
            destination.hasRoute<ConfigFileRoute>() || destination.hasRoute<ConfigEditRoute>() -> TaskifyModule.Vault
        destination.hasRoute<SandiRoute>() -> TaskifyModule.Sandi
        destination.hasRoute<FilesRoute>() -> TaskifyModule.Files
        destination.hasRoute<ReviewRoute>() -> TaskifyModule.Review
        destination.hasRoute<NotificationsRoute>() -> TaskifyModule.Notifications
        destination.hasRoute<WellbeingRoute>() -> TaskifyModule.Wellbeing
        destination.hasRoute<TagsRoute>() || destination.hasRoute<TagRoute>() -> TaskifyModule.Tags
        destination.hasRoute<TrashRoute>() -> TaskifyModule.Trash
        destination.hasRoute<MusicRoute>() -> TaskifyModule.Music
        destination.hasRoute<TikTokRoute>() -> TaskifyModule.TikTok
        destination.hasRoute<ModuleRoute>() -> backStackEntry?.toRoute<ModuleRoute>()?.module
        else -> null
    }
    LaunchedEffect(currentModule) { currentModule?.let { com.rosyidputrar.taskify.widget.ModuleShortcuts.report(shortcutContext, it) } }
    // Editor layar penuh: dok disembunyikan agar bilah format di atas keyboard tidak tertutup.
    val immersive = destination?.isFullScreenEditor() == true
    val selectedKey = when (currentModule) {
        TaskifyModule.Home -> KEY_HOME
        TaskifyModule.Calendar -> KEY_CALENDAR
        null -> KEY_ROOM
        pinned -> KEY_PIN
        else -> KEY_ROOM
    }

    val openModule: (TaskifyModule) -> Unit = { module ->
        spotlight.close()
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
        // Pemutar musik aktif → bilah mini di atas dok (ruang konten ikut ditambah).
        val musicPlayer = remember { runCatching { org.koin.core.context.GlobalContext.getOrNull()?.getOrNull<com.rosyidputrar.taskify.core.media.MusicPlayer>() }.getOrNull() }
        val musicActive by remember(musicPlayer) {
            musicPlayer?.state?.map { it.status != com.rosyidputrar.taskify.core.domain.media.YtStatus.Idle } ?: kotlinx.coroutines.flow.flowOf(false)
        }.collectAsState(false)
        val onMusic = destination?.hasRoute<MusicRoute>() == true
        // Keyboard tampil → dok & pemutar mini disembunyikan, area konten berhenti di atas keyboard
        // (edge-to-edge membuat jendela tidak lagi mengecil sendiri).
        val imeVisible = WindowInsets.isImeVisible
        val showMini = musicActive && !immersive && !onMusic && !imeVisible
        // Ruang di bawah konten supaya item terakhir tidak tertutup dok mengambang.
        // Ruang bilah mini ikut beranimasi supaya isi layar tidak melompat 68dp saat musik mulai/berhenti.
        val miniSpace by animateDpAsState(if (showMini) 68.dp else 0.dp, TaskifyMotion.screenDpSpec, label = "mini-space")
        val contentPadding = PaddingValues(
            top = statusTop,
            bottom = if (imeVisible) 16.dp else navBottom + (if (expanded) 24.dp else 112.dp) + miniSpace,
        )

        val host: @Composable (Modifier) -> Unit = { modifier ->
            TaskifyNavHost(
                navController = navController,
                contentPadding = contentPadding,
                preferences = preferences,
                displayName = displayName,
                home = home,
                onOpenModule = openModule,
                onCapture = { openCapture() },
                onThemeModeChange = onThemeModeChange,
                onPinnedModuleChange = onPinnedModuleChange,
                onHapticsChange = onHapticsChange,
                onThemeChange = onThemeChange,
                modifier = modifier,
            )
        }

        if (expanded) {
            Row(Modifier.fillMaxSize()) {
                TaskifyRail(
                    selectedKey = selectedKey,
                    onCapture = { openCapture() },
                    onSearch = { spotlight.open() },
                    onHome = { navController.navigateTopLevel(HomeRoute) },
                    onCalendar = { navController.navigateTopLevel(CalendarRoute()) },
                    onRoom = { navController.navigateTopLevel(RoomRoute) },
                )
                host(Modifier.weight(1f).imePadding())
            }
        } else {
            host(Modifier.fillMaxSize().imePadding())
        }
        AnimatedVisibility(
            visible = !expanded && !immersive && !imeVisible,
            enter = TaskifyMotion.chromeEnter,
            exit = TaskifyMotion.chromeExit,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
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
                        KEY_CALENDAR -> navController.navigateTopLevel(CalendarRoute())
                        KEY_ROOM -> navController.navigateTopLevel(RoomRoute)
                        KEY_PIN -> pinned?.let { navController.navigateTopLevel(it.route()) }
                    }
                },
                onCapture = { openCapture() },
                onCaptureLongPress = { openCapture(pickType = true) },
                // Panel Spotlight ikut tertarik sejak jari mulai bergeser ke atas.
                onPull = { delta -> if (spotlight.dragBy(delta)) haptics.threshold() },
                onPullEnd = { velocity -> spotlight.release(velocity) },
                onOpenSearch = { spotlight.open() },
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            )
        }

        // Bilah mini naik/turun dengan gerak yang sama dengan dok (tidak muncul/hilang mendadak).
        AnimatedVisibility(
            visible = showMini,
            enter = TaskifyMotion.chromeEnter,
            exit = TaskifyMotion.chromeExit,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (expanded) 16.dp else 92.dp, start = 16.dp, end = 16.dp)
                .widthIn(max = 560.dp),
        ) {
            MiniPlayer(onOpen = { navController.navigate(MusicRoute()) { launchSingleTop = true } })
        }

        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        LaunchedEffect(messages) {
            messages.collect { message ->
                snackbar.currentSnackbarData?.dismiss()
                scope.launchSafe {
                    val result = snackbar.showSnackbar(message.text, actionLabel = message.actionLabel, withDismissAction = message.action == null, duration = SnackbarDuration.Short)
                    if (result == SnackbarResult.ActionPerformed) {
                        haptics.tap()
                        message.action?.invoke()
                    }
                }
            }
        }
        // Selalu di atas keyboard, dok, dan pemutar mini — "Urungkan" tidak pernah tertutup.
        SnackbarHost(
            snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(
                    bottom = when {
                        imeVisible -> 8.dp
                        expanded -> 16.dp + if (showMini) 68.dp else 0.dp
                        immersive -> 64.dp
                        else -> 96.dp + if (showMini) 68.dp else 0.dp
                    },
                    start = 16.dp,
                    end = 16.dp,
                ),
        )

        Spotlight(
            state = spotlight,
            onOpenModule = openModule,
            onCapture = { text -> openCapture(text) },
            themeMode = preferences.themeMode,
            onThemeModeChange = onThemeModeChange,
            onOpenEntity = ::openEntity,
            onOpenTag = { id -> spotlight.close(); navController.navigate(TagRoute(id)) },
            onOpenNotifications = { pkg -> spotlight.close(); navController.navigate(NotificationsRoute(app = pkg)) },
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
    home: HomeSlot,
    onOpenModule: (TaskifyModule) -> Unit,
    onCapture: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onPinnedModuleChange: (TaskifyModule?) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onThemeChange: (com.rosyidputrar.taskify.core.designsystem.theme.ThemeSpec) -> Unit,
    modifier: Modifier,
) {
    val pinned = preferences.pinnedModule
    // Pola Animeru: top-level ↔ top-level = crossfade + skala halus; push/pop = geser berpegas
    // (paralaks 1/3); editor layar penuh = pudar + skala tanpa geser. SharedTransitionLayout
    // memungkinkan elemen bersama (kartu tugas) "terbang" antar layar lewat Modifier.sharedBounds.
    SharedTransitionLayout(modifier) {
    CompositionLocalProvider(LocalSharedScope provides this) {
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = Modifier.fillMaxSize(),
        enterTransition = { enter(pinned) },
        exitTransition = { exit(pinned) },
        popEnterTransition = { popEnter(pinned) },
        popExitTransition = { popExit(pinned) },
        // Back yang ditahan (predictive back) memakai geser yang sama dengan back biasa, seperti
        // Animeru. Tanpa ini navigation-compose 2.9+ memakai bawaannya: layar mengecil ke 70%.
        predictivePopEnterTransition = { popEnter(pinned) },
        predictivePopExitTransition = { popExit(pinned) },
    ) {
        composable<HomeRoute> {
            Screen { home(contentPadding, displayName, onOpenModule) { id -> navController.navigate(TasksRoute(id)) { launchSingleTop = true } } }
        }
        composable<TasksRoute> { entry ->
            Screen {
                val route = entry.toRoute<TasksRoute>()
                TasksScreen(
                    contentPadding = contentPadding,
                    openTaskId = route.taskId,
                    onBack = navController.backIfPossible(),
                    backButton = { BackButton(it) },
                    // Kartu tugas di Beranda "terbang" ke barisnya di daftar (key sama: task-{id}).
                    taskModifier = { id -> Modifier.sharedBounds("task-$id") },
                )
            }
        }
        composable<CalendarRoute> { entry ->
            Screen {
                CalendarScreen(
                    contentPadding = contentPadding,
                    initialDate = entry.toRoute<CalendarRoute>().date,
                    onOpenModule = onOpenModule,
                    onOpenJournal = { id, date -> navController.navigate(JournalEntryRoute(id, date)) },
                    onOpenNote = { id, date -> navController.navigate(NoteEditRoute(id, date)) },
                    onOpenTransaction = { id, date -> navController.navigate(TransactionRoute(id, date)) },
                    onOpenVault = { kind, id -> navController.navigate(if (kind == "snippet") SnippetRoute(id) else ProjectRoute(id)) },
                )
            }
        }
        composable<VaultRoute> {
            Screen {
                VaultScreen(
                    contentPadding = contentPadding,
                    onOpenSnippet = { navController.navigate(SnippetRoute(it)) },
                    onOpenProject = { navController.navigate(ProjectRoute(it)) },
                    onOpenConfig = { id -> navController.navigate(if (id == null) ConfigEditRoute() else ConfigFileRoute(id)) },
                    onBack = navController.backIfPossible(),
                    backButton = { BackButton(it) },
                )
            }
        }
        composable<ConfigFileRoute> { entry ->
            Screen {
                ConfigFileScreen(
                    entry.toRoute<ConfigFileRoute>().id,
                    contentPadding = contentPadding,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(ConfigEditRoute(fileId = it)) },
                    onOpenSandi = { navController.navigate(SandiRoute) },
                    backButton = { BackButton(it) },
                )
            }
        }
        composable<ConfigEditRoute> { entry ->
            Screen {
                val route = entry.toRoute<ConfigEditRoute>()
                ConfigEditorScreen(
                    route.fileId, route.projectId,
                    contentPadding = contentPadding,
                    onBack = { navController.popBackStack() },
                    onSaved = { id ->
                        navController.popBackStack()
                        if (route.fileId == null) navController.navigate(ConfigFileRoute(id))
                    },
                    onOpenSandi = { navController.navigate(SandiRoute) },
                    backButton = { BackButton(it) },
                )
            }
        }
        composable<ShareRoute> {
            Screen {
                ShareScreen(
                    contentPadding, onCapture = { AppEvents.send(AppEvent.OpenCapture(it)) }, onDone = { navController.popBackStack() }, backButton = { BackButton(it) },
                    onTikTok = { link -> navController.navigate(TikTokRoute(link)) },
                    onMusic = { link -> navController.navigate(MusicRoute(link)) },
                )
            }
        }
        composable<NotificationsRoute> { entry ->
            Screen {
                val route = entry.toRoute<NotificationsRoute>()
                NotificationsScreen(
                    contentPadding = contentPadding,
                    app = route.app,
                    openDrafts = route.drafts,
                    onBack = navController.backIfPossible(),
                    backButton = { BackButton(it) },
                )
            }
        }
        composable<WellbeingRoute> {
            Screen {
                WellbeingScreen(
                    contentPadding = contentPadding,
                    onOpenNotifications = { pkg -> navController.navigate(NotificationsRoute(app = pkg)) },
                    onBack = navController.backIfPossible(),
                    backButton = { BackButton(it) },
                )
            }
        }
        composable<FocusRoute> { entry ->
            Screen {
                FocusScreen(
                    taskId = entry.toRoute<FocusRoute>().taskId,
                    contentPadding = contentPadding,
                    backButton = { BackButton(it) },
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable<MusicRoute> { entry ->
            Screen {
                MusicScreen(contentPadding, onBack = navController.backIfPossible(), backButton = { BackButton(it) }, link = entry.toRoute<MusicRoute>().link)
            }
        }
        composable<TikTokRoute> { entry ->
            Screen { TikTokScreen(contentPadding, initial = entry.toRoute<TikTokRoute>().input, onBack = navController.backIfPossible(), backButton = { BackButton(it) }) }
        }
        composable<TagsRoute> {
            Screen { TagsScreen(contentPadding, onOpenTag = { navController.navigate(TagRoute(it)) }, onBack = navController.backIfPossible(), backButton = { BackButton(it) }) }
        }
        composable<TagRoute> { entry ->
            Screen { TagScreen(entry.toRoute<TagRoute>().id, contentPadding, onBack = { navController.popBackStack() }, backButton = { BackButton(it) }) }
        }
        composable<TrashRoute> {
            Screen { TrashScreen(contentPadding, onBack = navController.backIfPossible(), backButton = { BackButton(it) }) }
        }
        composable<ReviewRoute> {
            Screen {
                ReviewScreen(
                    contentPadding = contentPadding,
                    nav = ReviewNav(
                        openDay = { navController.navigate(CalendarRoute(it)) },
                        openJournal = { navController.navigate(JournalEntryRoute(id = it)) },
                        openFinance = { navController.navigate(FinanceRoute) },
                        openVault = { navController.navigate(VaultRoute) },
                        reflect = { period, from, to ->
                            navController.navigate(JournalEntryRoute(date = Dates.today(), template = if (period == ReviewPeriod.Month) "month" else "week", from = from, to = to))
                        },
                    ),
                    onBack = navController.backIfPossible(),
                    backButton = { BackButton(it) },
                )
            }
        }
        composable<FilesRoute> {
            Screen { FilesScreen(contentPadding = contentPadding, onBack = navController.backIfPossible(), backButton = { BackButton(it) }) }
        }
        composable<SandiRoute> {
            Screen { SandiScreen(contentPadding = contentPadding, onBack = navController.backIfPossible(), backButton = { BackButton(it) }) }
        }
        composable<SnippetRoute> { entry ->
            Screen { SnippetEditorScreen(entry.toRoute<SnippetRoute>().id, onBack = { navController.popBackStack() }) }
        }
        composable<ProjectRoute> { entry ->
            Screen {
                ProjectScreen(
                    entry.toRoute<ProjectRoute>().id,
                    contentPadding = contentPadding,
                    onBack = { navController.popBackStack() },
                    onViewZip = { navController.navigate(ZipRoute(it)) },
                    backButton = { BackButton(it) },
                )
            }
        }
        composable<ZipRoute> { entry ->
            Screen { ZipViewerScreen(entry.toRoute<ZipRoute>().attachmentId, onBack = { navController.popBackStack() }) }
        }
        composable<FinanceRoute> {
            Screen {
                FinanceScreen(
                    contentPadding = contentPadding,
                    onOpenTx = { navController.navigate(TransactionRoute(id = it)) },
                    onNewTx = { walletId -> navController.navigate(TransactionRoute(walletId = walletId)) },
                    onBack = navController.backIfPossible(),
                    backButton = { BackButton(it) },
                    topSlot = { NotificationDraftsBanner(onOpen = { navController.navigate(NotificationsRoute(drafts = true)) }) },
                )
            }
        }
        composable<TransactionRoute> { entry ->
            Screen {
                val route = entry.toRoute<TransactionRoute>()
                TxEditorScreen(TxEditorArgs(route.id, route.date, route.walletId), onBack = { navController.popBackStack() })
            }
        }
        composable<JournalRoute> {
            Screen {
                JournalScreen(
                    contentPadding = contentPadding,
                    onOpenEntry = { navController.navigate(JournalEntryRoute(id = it)) },
                    onNewEntry = { navController.navigate(JournalEntryRoute(date = it)) },
                    onBack = navController.backIfPossible(),
                    backButton = { BackButton(it) },
                )
            }
        }
        composable<JournalEntryRoute> { entry ->
            Screen {
                val route = entry.toRoute<JournalEntryRoute>()
                val template = when (route.template) { "week" -> ReflectionKind.Week; "month" -> ReflectionKind.Month; else -> null }
                JournalEditorScreen(JournalEditorArgs(route.id, route.date, template, route.from, route.to), onBack = { navController.popBackStack() })
            }
        }
        composable<NotesRoute> {
            Screen {
                NotesScreen(
                    contentPadding = contentPadding,
                    onOpenNote = { navController.navigate(NoteEditRoute(id = it)) },
                    onNewNote = { navController.navigate(NoteEditRoute()) },
                    onBack = navController.backIfPossible(),
                    backButton = { BackButton(it) },
                )
            }
        }
        composable<NoteEditRoute> { entry ->
            Screen {
                val route = entry.toRoute<NoteEditRoute>()
                NoteEditorScreen(NoteEditorArgs(route.id, route.date), onBack = { navController.popBackStack() })
            }
        }
        composable<RoomRoute> {
            Screen {
                RoomScreen(
                    contentPadding = contentPadding,
                    pinnedModule = preferences.pinnedModule,
                    onOpenModule = onOpenModule,
                    onPinnedModuleChange = onPinnedModuleChange,
                )
            }
        }
        composable<SettingsRoute> {
            Screen {
                SettingsScreen(
                    contentPadding = contentPadding,
                    themeMode = preferences.themeMode,
                    pinnedModule = preferences.pinnedModule,
                    onThemeModeChange = onThemeModeChange,
                    onPinnedModuleChange = onPinnedModuleChange,
                    haptics = preferences.haptics,
                    onHapticsChange = onHapticsChange,
                    theme = preferences.theme,
                    onThemeChange = onThemeChange,
                    onBack = navController.backIfPossible(),
                    onOpenModule = { navController.navigate(it.route()) },
                )
            }
        }
        composable<ModuleRoute> { entry ->
            Screen {
                val route = entry.toRoute<ModuleRoute>()
                ModulePlaceholderScreen(route.module, contentPadding, onBack = navController.backIfPossible())
            }
        }
    }
    }
    }
}

/**
 * Editor layar penuh (tanpa dok): juga dianimasikan seperti PlayerRoute Animeru — pudar + skala,
 * tanpa geser. Satu sumber untuk flag `immersive` dan aturan transisi.
 */
internal fun NavDestination.isFullScreenEditor(): Boolean =
    hasRoute<JournalEntryRoute>() || hasRoute<NoteEditRoute>() || hasRoute<TransactionRoute>() ||
        hasRoute<SnippetRoute>() || hasRoute<ZipRoute>() || hasRoute<ConfigEditRoute>() || hasRoute<FocusRoute>()

/** Padanan `isTab()` Animeru: Hari Ini, Kalender, Ruang, dan modul yang disematkan di dok. */
internal fun NavBackStackEntry.isTopLevel(pinned: TaskifyModule?): Boolean {
    val d = destination
    if (d.hasRoute<HomeRoute>() || d.hasRoute<CalendarRoute>() || d.hasRoute<RoomRoute>()) return true
    val route = pinned?.route() ?: return false
    return d.hasRoute(route::class)
}

/**
 * Akar setiap layar di NavHost: latar tema yang BURAM + [LocalNavScope]. Tanpa latar, layar yang
 * sedang keluar/masuk tembus pandang selama transisi (ghosting).
 */
@Composable
internal fun AnimatedVisibilityScope.Screen(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNavScope provides this) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
    }
}

/**
 * Transisi layar: top-level ke top-level = crossfade + skala halus; push/pop = geser berpegas
 * (paralaks 1/3). Layar teratas selalu buram penuh; hanya layar di bawahnya yang meredup.
 */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.enter(pinned: TaskifyModule?): EnterTransition = when {
    // Editor & tab dok: "fade through" — layar lama hilang dulu, baru yang baru muncul (tanpa tumpuk).
    targetState.destination.isFullScreenEditor() -> TaskifyMotion.fadeThroughIn(0.96f)
    initialState.isTopLevel(pinned) && targetState.isTopLevel(pinned) -> TaskifyMotion.fadeThroughIn(0.985f)
    // Layar baru di ATAS & buram penuh (tanpa fade) supaya layar di bawahnya tidak tembus.
    else -> slideInHorizontally(Motion.screenOffset) { it }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.exit(pinned: TaskifyModule?): ExitTransition = when {
    targetState.destination.isFullScreenEditor() -> TaskifyMotion.fadeThroughOut()
    initialState.isTopLevel(pinned) && targetState.isTopLevel(pinned) -> TaskifyMotion.fadeThroughOut()
    else -> slideOutHorizontally(Motion.screenOffset) { -it / 3 } + fadeOut(Motion.screenFloat, 0.4f)
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(pinned: TaskifyModule?): EnterTransition = when {
    initialState.destination.isFullScreenEditor() -> TaskifyMotion.fadeThroughIn()
    initialState.isTopLevel(pinned) && targetState.isTopLevel(pinned) -> TaskifyMotion.fadeThroughIn(0.985f)
    else -> slideInHorizontally(Motion.screenOffset) { -it / 3 } + fadeIn(Motion.screenFloat, 0.4f)
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(pinned: TaskifyModule?): ExitTransition = when {
    initialState.destination.isFullScreenEditor() -> TaskifyMotion.fadeThroughOut(0.96f)
    initialState.isTopLevel(pinned) && targetState.isTopLevel(pinned) -> TaskifyMotion.fadeThroughOut()
    // Saat kembali, layar yang ditutup tetap di ATAS (zIndex NavHost) — geser keluar tanpa fade,
    // jadi tidak ada "hantu" Keuangan di balik Ruang.
    else -> slideOutHorizontally(Motion.screenOffset) { it }
}

/** Tombol kembali hanya bila layar dibuka dari layar lain (bukan tujuan dok). */
private fun NavHostController.backIfPossible(): (() -> Unit)? =
    if (previousBackStackEntry != null) ({ popBackStack() }) else null

/** [restore] = false untuk tujuan dengan argumen baru (mis. tanggal Kalender) agar tidak memulihkan argumen lama. */
private fun NavHostController.navigateTopLevel(route: Any, restore: Boolean = true) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = restore }
        launchSingleTop = true
        restoreState = restore
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
