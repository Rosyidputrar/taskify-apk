package com.rosyidputrar.taskify

import com.rosyidputrar.taskify.core.designsystem.component.LocalHapticsEnabled
import androidx.compose.runtime.CompositionLocalProvider
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.view.KeyEvent
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.rosyidputrar.taskify.core.auth.LockManager
import com.rosyidputrar.taskify.ui.gate.RecentsPrivacy
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.designsystem.theme.isDark
import com.rosyidputrar.taskify.core.designsystem.component.Messenger
import com.rosyidputrar.taskify.core.reminders.ReminderNotifier
import org.koin.android.ext.android.inject
import com.rosyidputrar.taskify.data.AppPreferences
import com.rosyidputrar.taskify.feature.auth.LockScreen
import com.rosyidputrar.taskify.feature.auth.LoginScreen
import com.rosyidputrar.taskify.feature.auth.MfaScreen
import com.rosyidputrar.taskify.ui.gate.ConfigMissingScreen
import com.rosyidputrar.taskify.ui.gate.GateHost
import com.rosyidputrar.taskify.ui.gate.GateUi
import com.rosyidputrar.taskify.ui.gate.GateViewModel
import com.rosyidputrar.taskify.ui.gate.PreparingScreen
import com.rosyidputrar.taskify.ui.shell.AppEvent
import com.rosyidputrar.taskify.ui.shell.AppEvents
import com.rosyidputrar.taskify.ui.shell.AppViewModel
import com.rosyidputrar.taskify.ui.shell.TaskifyApp
import org.koin.androidx.viewmodel.ext.android.viewModel

/** FragmentActivity (turunan ComponentActivity): syarat dialog BiometricPrompt untuk Sandi. */
class MainActivity : FragmentActivity() {

    private val appViewModel: AppViewModel by viewModel()
    private val gateViewModel: GateViewModel by viewModel()
    private val messenger: Messenger by inject()
    private val shareInbox: com.rosyidputrar.taskify.ui.share.ShareInbox by inject()
    private val lock: LockManager by inject()
    private val recents = RecentsPrivacy(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Splash tetap tampil sampai tema & status login terbaca → tidak ada kedipan layar masuk.
        splash.setKeepOnScreenCondition {
            appViewModel.state.value == null || gateViewModel.ui.value == GateUi.Loading
        }
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                lock.state.map { it.config != null }.distinctUntilChanged().collect { recents.setHidden(it) }
            }
        }

        setContent {
            val prefs by appViewModel.state.collectAsStateWithLifecycle()
            val gate by gateViewModel.ui.collectAsStateWithLifecycle()
            val snapshot = prefs ?: return@setContent
            CompositionLocalProvider(LocalHapticsEnabled provides snapshot.haptics) {
                TaskifyTheme(darkTheme = snapshot.themeMode.isDark(), spec = snapshot.theme) {
                    AppGate(gate, snapshot)
                }
            }
        }
    }

    @Composable
    private fun AppGate(gate: GateUi, snapshot: AppPreferences.Snapshot) {
        var preview by rememberSaveable { mutableStateOf(false) }
        val shell: @Composable (String?) -> Unit = { name ->
            TaskifyApp(
                preferences = snapshot,
                displayName = name,
                messages = messenger.messages,
                onThemeModeChange = appViewModel::setThemeMode,
                onPinnedModuleChange = appViewModel::setPinnedModule,
                onHapticsChange = appViewModel::setHaptics,
                onThemeChange = appViewModel::setTheme,
            )
        }
        GateHost(gate, shell = shell, gateScreen = { target ->
            when (target) {
                GateUi.Loading -> Unit
                GateUi.ConfigMissing -> if (preview) shell(null) else ConfigMissingScreen(onPreview = { preview = true })
                GateUi.SignedOut -> LoginScreen()
                is GateUi.Mfa -> MfaScreen(target.status)
                GateUi.Preparing -> PreparingScreen()
                GateUi.Locked -> LockScreen()
                is GateUi.Open -> Unit // ditangani GateHost
            }
        })
    }

    override fun onPause() {
        recents.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        recents.onResume()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_CAPTURE -> AppEvents.send(AppEvent.OpenCapture())
            ACTION_SEARCH -> AppEvents.send(AppEvent.OpenSpotlight)
            ACTION_CALENDAR -> AppEvents.send(AppEvent.OpenCalendar())
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                // Salin isi selagi izin baca dari aplikasi pengirim masih berlaku, lalu buka layar Bagikan.
                lifecycleScope.launch { if (shareInbox.receive(intent)) AppEvents.send(AppEvent.OpenShare) }
            }
            com.rosyidputrar.taskify.widget.ModuleShortcuts.ACTION_OPEN_MODULE ->
                com.rosyidputrar.taskify.widget.ModuleShortcuts.moduleOf(intent)?.let { AppEvents.send(AppEvent.OpenModule(it)) }
            ReminderNotifier.ACTION_OPEN_TASK -> intent.getStringExtra(ReminderNotifier.EXTRA_TASK_ID)?.let { AppEvents.send(AppEvent.OpenTask(it)) }
            com.rosyidputrar.taskify.widget.EntityShortcuts.ACTION_OPEN_ENTITY ->
                com.rosyidputrar.taskify.widget.EntityShortcuts.entityOf(intent)?.let { (type, id) -> AppEvents.send(AppEvent.OpenEntity(type, id)) }
        }
    }

    /** Ctrl/⌘ + K dari keyboard fisik (tablet, Chromebook, DeX) membuka Spotlight di layar mana pun. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN &&
            event.keyCode == KeyEvent.KEYCODE_K &&
            (event.isCtrlPressed || event.isMetaPressed)
        ) {
            AppEvents.send(AppEvent.OpenSpotlight)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        const val ACTION_CAPTURE = "com.rosyidputrar.taskify.action.CAPTURE"
        const val ACTION_SEARCH = "com.rosyidputrar.taskify.action.SEARCH"
        const val ACTION_CALENDAR = "com.rosyidputrar.taskify.action.CALENDAR"
    }
}
